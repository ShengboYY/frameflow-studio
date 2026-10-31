package com.frameflow.studio.cases.task.impl;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.frameflow.studio.cases.asset.IResolveTaskAssetsCase;
import com.frameflow.studio.cases.task.ICreateProductionTaskCase;
import com.frameflow.studio.cases.task.model.ExecutionOptions;
import com.frameflow.studio.cases.task.model.TaskAccepted;
import com.frameflow.studio.cases.task.model.TaskOperationException;
import com.frameflow.studio.cases.task.port.ITaskDispatcher;
import com.frameflow.studio.cases.template.ITemplateCatalogCase;
import com.frameflow.studio.cases.template.model.TemplateValidation.ImageInput;
import com.frameflow.studio.cases.template.model.TemplateValidation.Violation;
import com.frameflow.studio.domain.task.adapter.repository.IProductionTaskRepository;
import com.frameflow.studio.domain.task.model.aggregate.ProductionTask;
import com.frameflow.studio.domain.task.model.valobj.TaskInput;
import com.frameflow.studio.domain.task.model.valobj.TaskStatus;
import static com.frameflow.studio.cases.task.model.TaskOperationException.Reason.*;

@Service
public class CreateProductionTaskCaseImpl implements ICreateProductionTaskCase {
    private static final Logger log = LoggerFactory.getLogger(CreateProductionTaskCaseImpl.class);
    private final IProductionTaskRepository repository;
    private final IResolveTaskAssetsCase assets;
    private final ITemplateCatalogCase templates;
    private final ITaskDispatcher dispatcher;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public CreateProductionTaskCaseImpl(IProductionTaskRepository repository, IResolveTaskAssetsCase assets,
                                        ITemplateCatalogCase templates, ITaskDispatcher dispatcher,
                                        Clock clock, PlatformTransactionManager manager) {
        this.repository = repository;
        this.assets = assets;
        this.templates = templates;
        this.dispatcher = dispatcher;
        this.clock = clock;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(5);
    }

    @Override
    public TaskAccepted create(UUID ownerId, String templateId, List<UUID> assetIds, ExecutionOptions options) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Task acceptance must start outside a transaction");
        }
        Objects.requireNonNull(options);
        var orderedIds = List.copyOf(assetIds);
        var template = templates.findById(templateId).orElseThrow(() -> new TaskOperationException(NOT_FOUND));
        // The catalog is immutable. Reject impossible counts before per-asset database and file reads.
        if (orderedIds.size() != template.requiredImageCount()) {
            throw new TaskOperationException(REQUIREMENTS_MISMATCH, null, List.of(Violation.IMAGE_COUNT));
        }
        var resolved = assets.resolve(ownerId, orderedIds);
        var validation = templates.validateSubmission(templateId, resolved.stream()
                .map(asset -> new ImageInput(asset.mediaType(), asset.sizeBytes())).toList())
                .orElseThrow(() -> new TaskOperationException(NOT_FOUND));
        if (!validation.violations().isEmpty()) {
            throw new TaskOperationException(REQUIREMENTS_MISMATCH, null, validation.violations());
        }
        var task = ProductionTask.accept(UUID.randomUUID(), ownerId, clock.instant().truncatedTo(ChronoUnit.MICROS),
                new TaskInput(templateId, orderedIds));
        try {
            transaction.executeWithoutResult(status -> repository.insert(task));
        } catch (RuntimeException failure) {
            // A lost commit acknowledgement is indistinguishable from rollback here. Never dispatch or retry.
            log.warn("Task {} submission outcome unknown: {}", task.getTaskId(), failure.getClass().getSimpleName());
            throw new TaskOperationException(SUBMISSION_OUTCOME_UNKNOWN, task.getTaskId(), List.of());
        }
        boolean rejected = false;
        try {
            rejected = !dispatcher.dispatch(task.getTaskId(), options);
        } catch (RuntimeException failure) {
            // Without an explicit rejection guarantee, it may already be queued. Preserve the accepted identity.
            log.warn("Task {} dispatch outcome unknown: {}", task.getTaskId(), failure.getClass().getSimpleName());
        }
        if (rejected) {
            try {
                markRejected(task.getTaskId());
            } catch (RuntimeException failure) {
                log.warn("Task {} dispatch rejection conclusion not confirmed: {}", task.getTaskId(), failure.getClass().getSimpleName());
            }
        }
        return new TaskAccepted(task.getTaskId(), ownerId, task.getCreatedAt());
    }

    private void markRejected(UUID taskId) {
        transaction.executeWithoutResult(status -> {
            var loaded = repository.findById(taskId).orElse(null);
            if (loaded == null || loaded.getRevision() != 0 || loaded.getExpectedStatus() != TaskStatus.PENDING) return;
            loaded.getTask().failBeforeStart("DISPATCH_REJECTED", "任务队列暂时无法接收制作任务");
            if (!repository.save(loaded, clock.instant().truncatedTo(ChronoUnit.MICROS))) {
                repository.findById(taskId); // Observe the winner, never overwrite it or dispatch again.
            }
        });
    }
}
