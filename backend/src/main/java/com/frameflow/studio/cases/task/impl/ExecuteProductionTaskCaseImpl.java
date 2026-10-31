package com.frameflow.studio.cases.task.impl;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.frameflow.studio.cases.asset.ICheckTaskAssetsCase;
import com.frameflow.studio.cases.asset.IMediaResourceCase;
import com.frameflow.studio.cases.asset.model.AssetOperationException;
import com.frameflow.studio.cases.task.IExecuteProductionTaskCase;
import com.frameflow.studio.cases.task.model.ExecutionOptions;
import com.frameflow.studio.cases.task.port.IStageDelay;
import com.frameflow.studio.domain.task.adapter.port.IMediaGeneratorPort;
import com.frameflow.studio.domain.task.adapter.repository.IProductionTaskRepository;
import com.frameflow.studio.domain.task.adapter.repository.TaskSnapshot;
import com.frameflow.studio.domain.task.model.aggregate.ProductionTask;
import com.frameflow.studio.domain.task.model.valobj.TaskResult;
import com.frameflow.studio.domain.task.model.valobj.TaskStage;
import com.frameflow.studio.domain.task.model.valobj.TaskStatus;

@Service
public class ExecuteProductionTaskCaseImpl implements IExecuteProductionTaskCase {
    private static final Logger log = LoggerFactory.getLogger(ExecuteProductionTaskCaseImpl.class);
    private final IProductionTaskRepository repository;
    private final ICheckTaskAssetsCase assets;
    private final IMediaGeneratorPort generator;
    private final IMediaResourceCase media;
    private final IStageDelay delay;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public ExecuteProductionTaskCaseImpl(IProductionTaskRepository repository, ICheckTaskAssetsCase assets,
                                         IMediaGeneratorPort generator, IMediaResourceCase media,
                                         IStageDelay delay, Clock clock, PlatformTransactionManager manager) {
        this.repository = repository;
        this.assets = assets;
        this.generator = generator;
        this.media = media;
        this.delay = delay;
        this.clock = clock;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(5);
    }

    @Override
    public void execute(UUID taskId, ExecutionOptions options) {
        Objects.requireNonNull(taskId);
        Objects.requireNonNull(options);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Task execution must start outside a transaction");
        }
        try {
            executeStages(taskId, options);
        } catch (RuntimeException failure) {
            // Includes read/write/commit failures: outcome may be unknown, so do not write a new failure or retry.
            log.warn("Task {} execution stopped: {}", taskId, failure.getClass().getSimpleName());
        }
    }

    private void executeStages(UUID id, ExecutionOptions options) {
        var checking = transition(id, 0, TaskStatus.PENDING, null, ProductionTask::startChecking);
        if (checking == null) return;
        var task = checking.getTask();
        if (stage(checking, options, () -> {
            assets.check(task.getOwnerId(), task.getInput().assetIds());
            return Boolean.TRUE;
        }) == null) return;

        var generating = advance(checking, ProductionTask::completeChecking);
        if (generating == null) return;
        var candidate = stage(generating, options, () -> Objects.requireNonNull(generator.generate(task.getInput())));
        if (candidate == null) return;

        var finalizing = advance(generating, ProductionTask::completeGeneration);
        if (finalizing == null) return;
        var ready = stage(finalizing, options, () -> new TaskResult(media.prepareResult(candidate.reference()).reference()));
        if (ready != null) advance(finalizing, value -> value.succeed(ready));
    }

    /** Only stage work is translated to a stage failure; persistence is deliberately outside this catch. */
    private <T> T stage(TaskSnapshot entered, ExecutionOptions options, Supplier<T> work) {
        String code;
        boolean interrupted = false;
        try {
            delay.pause(options.stageDelayMs());
            if (Thread.interrupted()) throw new InterruptedException();
            if (options.failAtStage() == entered.getExpectedStage()) throw new SimulatedStageFailure();
            return work.get();
        } catch (InterruptedException failure) {
            interrupted = true;
            code = "STAGE_EXECUTION_FAILED";
        } catch (SimulatedStageFailure failure) {
            code = "MOCK_STAGE_FAILURE";
        } catch (RuntimeException failure) {
            code = switch (entered.getExpectedStage()) {
                case CHECK_ASSETS -> failure instanceof AssetOperationException ? "ASSET_UNAVAILABLE" : "STAGE_EXECUTION_FAILED";
                case GENERATE -> "GENERATION_FAILED";
                case FINALIZE -> "RESULT_UNAVAILABLE";
            };
        }
        try {
            String reason = code;
            String message = switch (code) {
                case "MOCK_STAGE_FAILURE" -> "该阶段模拟失败";
                case "ASSET_UNAVAILABLE" -> "输入素材不可用";
                case "GENERATION_FAILED" -> "模拟生成失败";
                case "RESULT_UNAVAILABLE" -> "结果资源不可用";
                default -> "阶段执行中断或异常";
            };
            advance(entered, value -> value.failCurrentStage(entered.getExpectedStage(), reason, message));
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
        return null;
    }

    private TaskSnapshot advance(TaskSnapshot entered, Consumer<ProductionTask> behavior) {
        return transition(entered.getTask().getTaskId(), entered.getRevision(), TaskStatus.RUNNING,
                entered.getExpectedStage(), behavior);
    }

    private TaskSnapshot transition(UUID id, long revision, TaskStatus expectedStatus, TaskStage expectedStage,
                                     Consumer<ProductionTask> behavior) {
        return transaction.execute(status -> {
            var loaded = repository.findById(id).orElse(null);
            if (loaded == null || loaded.getRevision() != revision || loaded.getExpectedStatus() != expectedStatus
                    || loaded.getExpectedStage() != expectedStage) return null;
            behavior.accept(loaded.getTask());
            var updatedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
            if (!repository.save(loaded, updatedAt)) {
                log.info("Task {} lost execution eligibility at revision {}", id, revision);
                return null;
            }
            // TransactionTemplate must return successfully (including commit) before this permits any I/O.
            return new TaskSnapshot(loaded.getTask(), revision + 1, updatedAt);
        });
    }

    private static class SimulatedStageFailure extends RuntimeException { }
}
