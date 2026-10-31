package com.frameflow.studio.cases.task;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.frameflow.studio.cases.asset.ICheckTaskAssetsCase;
import com.frameflow.studio.cases.asset.IMediaResourceCase;
import com.frameflow.studio.cases.asset.model.AssetOperationException;
import com.frameflow.studio.cases.asset.model.MediaResource;
import com.frameflow.studio.cases.task.impl.ExecuteProductionTaskCaseImpl;
import com.frameflow.studio.cases.task.model.ExecutionOptions;
import com.frameflow.studio.cases.task.port.IStageDelay;
import com.frameflow.studio.domain.task.adapter.port.IMediaGeneratorPort;
import com.frameflow.studio.domain.task.adapter.repository.IProductionTaskRepository;
import com.frameflow.studio.domain.task.adapter.repository.TaskSnapshot;
import com.frameflow.studio.domain.task.model.aggregate.ProductionTask;
import com.frameflow.studio.domain.task.model.valobj.TaskInput;
import com.frameflow.studio.domain.task.model.valobj.TaskStage;
import com.frameflow.studio.domain.task.model.valobj.TaskStatus;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExecuteProductionTaskCaseTest {
    final Instant now = Instant.parse("2026-10-31T01:02:03.123456789Z");
    final IProductionTaskRepository repository = mock(IProductionTaskRepository.class);
    final ICheckTaskAssetsCase assets = mock(ICheckTaskAssetsCase.class);
    final IMediaGeneratorPort generator = mock(IMediaGeneratorPort.class);
    final IMediaResourceCase media = mock(IMediaResourceCase.class);
    final IStageDelay delay = mock(IStageDelay.class);
    final Map<UUID, TaskSnapshot> persisted = new HashMap<>();
    final List<String> events = new ArrayList<>();
    final AtomicInteger saveCount = new AtomicInteger();
    final LocalTransactions transactions = new LocalTransactions();
    final UUID id = UUID.randomUUID();
    final UUID owner = UUID.randomUUID();
    final List<UUID> inputs = List.of(UUID.randomUUID(), UUID.randomUUID());
    ExecuteProductionTaskCaseImpl execution;
    int loseSave;

    @BeforeEach
    void prepare() throws Exception {
        persisted.put(id, new TaskSnapshot(ProductionTask.accept(id, owner, now,
                new TaskInput("travel-postcard", List.of(inputs.get(1), inputs.get(0), inputs.get(1)))), 0, now));
        when(repository.findById(any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return Optional.ofNullable(persisted.get(call.getArgument(0))).map(this::copy);
        });
        when(repository.save(any(), any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            var loaded = call.getArgument(0, TaskSnapshot.class);
            events.add("save:" + loaded.getTask().getStatus() + ":" + loaded.getTask().getCurrentStage());
            if (saveCount.incrementAndGet() == loseSave) return false;
            assertThat(call.getArgument(1, Instant.class).getNano()).isEqualTo(123456000);
            var saved = new TaskSnapshot(loaded.getTask(), loaded.getRevision() + 1, call.getArgument(1));
            transactions.onCommit = () -> persisted.put(loaded.getTask().getTaskId(), copy(saved));
            return true;
        });
        doAnswer(call -> { observe(TaskStage.CHECK_ASSETS); return null; }).when(assets).check(any(), any());
        when(generator.generate(any())).thenAnswer(call -> {
            observe(TaskStage.GENERATE);
            return new IMediaGeneratorPort.Candidate("demo:mock-result");
        });
        when(media.prepareResult(any())).thenAnswer(call -> {
            observe(TaskStage.FINALIZE);
            return new MediaResource("demo:mock-result", "image/jpeg", new byte[]{1});
        });
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            events.add("delay:" + call.getArgument(0));
            return null;
        }).when(delay).pause(anyInt());
        execution = new ExecuteProductionTaskCaseImpl(repository, assets, generator, media, delay,
                Clock.fixed(now, ZoneOffset.UTC), transactions);
    }

    @AfterEach
    void cleanThread() {
        Thread.interrupted();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    @Test
    void commitsEachStageBeforeIoAndFinalizesCandidateBeforeSuccessWithoutAnyQuery() throws Exception {
        execution.execute(id, new ExecutionOptions(null, 123));
        assertThat(events).containsExactly(
                "save:RUNNING:CHECK_ASSETS", "commit:1", "delay:123", "work:CHECK_ASSETS",
                "save:RUNNING:GENERATE", "commit:2", "delay:123", "work:GENERATE",
                "save:RUNNING:FINALIZE", "commit:3", "delay:123", "work:FINALIZE",
                "save:SUCCEEDED:FINALIZE", "commit:4");
        verify(assets).check(owner, List.of(inputs.get(1), inputs.get(0), inputs.get(1)));
        verify(generator).generate(new TaskInput("travel-postcard", List.of(inputs.get(1), inputs.get(0), inputs.get(1))));
        verify(media).prepareResult("demo:mock-result");
        var result = persisted.get(id);
        assertThat(result.getTask().getStatus()).isEqualTo(TaskStatus.SUCCEEDED);
        assertThat(result.getTask().getResult().resultReference()).isEqualTo("demo:mock-result");
        assertThat(result.getRevision()).isEqualTo(4);
        verify(repository, never()).findOwned(any(), any());
        verify(repository, never()).listOwned(any());
    }

    @ParameterizedTest
    @EnumSource(TaskStage.class)
    void delayThenInjectedFailurePersistsActualStageAndNeverCallsThisOrLaterWork(TaskStage failed) throws Exception {
        execution.execute(id, new ExecutionOptions(failed, 17));
        var task = persisted.get(id).getTask();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(task.getCurrentStage()).isEqualTo(failed);
        assertThat(task.getFailure().code()).isEqualTo("MOCK_STAGE_FAILURE");
        assertThat(task.getFailure().stage()).isEqualTo(failed);
        assertThat(task.getResult()).isNull();
        verify(delay, times(failed.ordinal() + 1)).pause(17);
        assertThat(events).doesNotContain("work:" + failed);
        if (failed.ordinal() < 1) verifyNoInteractions(generator);
        verifyNoInteractions(media);
        assertThat(events.get(events.size() - 2)).isEqualTo("save:FAILED:" + failed);
    }

    @ParameterizedTest
    @EnumSource(TaskStage.class)
    void realCollaboratorFailuresAreSanitizedAndStopDownstream(TaskStage failed) {
        if (failed == TaskStage.CHECK_ASSETS) doThrow(new AssetOperationException(AssetOperationException.Reason.UNAVAILABLE))
                .when(assets).check(any(), any());
        if (failed == TaskStage.GENERATE) doThrow(new IllegalStateException("secret-path")).when(generator).generate(any());
        if (failed == TaskStage.FINALIZE) doThrow(new IllegalStateException("secret-path")).when(media).prepareResult(any());
        execution.execute(id, new ExecutionOptions(null, 0));
        var task = persisted.get(id).getTask();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(task.getCurrentStage()).isEqualTo(failed);
        assertThat(task.getFailure().code()).isEqualTo(switch (failed) {
            case CHECK_ASSETS -> "ASSET_UNAVAILABLE";
            case GENERATE -> "GENERATION_FAILED";
            case FINALIZE -> "RESULT_UNAVAILABLE";
        });
        assertThat(task.getFailure().message()).doesNotContain("secret-path");
        if (failed == TaskStage.CHECK_ASSETS) verifyNoInteractions(generator, media);
        if (failed == TaskStage.GENERATE) verifyNoInteractions(media);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void losingAnyConditionalSaveStopsWithoutAnotherWriteOrExternalStage(int save) {
        loseSave = save;
        execution.execute(id, new ExecutionOptions(null, 0));
        assertThat(saveCount.get()).isEqualTo(save);
        assertThat(events.stream().filter(value -> value.startsWith("work:"))).hasSize(save - 1);
        assertThat(persisted.get(id).getRevision()).isEqualTo(save - 1);
        assertThat(persisted.get(id).getTask().getFailure()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void unknownCommitOutcomeStopsWithoutWritingFailureOrRepeatingGeneration(int commit) {
        transactions.failCommit = commit;
        // Simulate "commit succeeded but acknowledgement was lost".
        transactions.applyBeforeFailure = true;
        execution.execute(id, new ExecutionOptions(null, 0));
        assertThat(saveCount.get()).isEqualTo(commit);
        assertThat(events.stream().filter(value -> value.startsWith("work:"))).hasSize(commit - 1);
        assertThat(persisted.get(id).getRevision()).isEqualTo(commit);
        assertThat(persisted.get(id).getTask().getFailure()).isNull();
    }

    @Test
    void readAndSaveExceptionsStopWithoutInventingStageFailure() {
        doThrow(new IllegalStateException("DB read")).when(repository).findById(id);
        execution.execute(id, new ExecutionOptions(null, 0));
        verifyNoInteractions(assets, generator, media, delay);
        verify(repository, never()).save(any(), any());
        reset(repository);
        when(repository.findById(id)).thenReturn(Optional.of(copy(persisted.get(id))));
        when(repository.save(any(), any())).thenThrow(new IllegalStateException("DB write"));
        execution.execute(id, new ExecutionOptions(null, 0));
        verifyNoInteractions(assets, generator, media, delay);
        verify(repository).save(any(), any());
    }

    @Test
    void failedFailureCommitStopsWithoutRetry() {
        transactions.failCommit = 2;
        execution.execute(id, new ExecutionOptions(TaskStage.CHECK_ASSETS, 0));
        assertThat(persisted.get(id).getTask().getStatus()).isEqualTo(TaskStatus.RUNNING);
        assertThat(saveCount.get()).isEqualTo(2);
        verifyNoInteractions(assets, generator, media);
    }

    @Test
    void duplicateRunningTerminalAndMissingEntriesPerformNoExternalWork() {
        execution.execute(id, new ExecutionOptions(null, 0));
        clearInvocations(assets, generator, media, delay);
        execution.execute(id, new ExecutionOptions(TaskStage.CHECK_ASSETS, 0));
        execution.execute(UUID.randomUUID(), new ExecutionOptions(null, 0));
        var running = ProductionTask.accept(UUID.randomUUID(), owner, now, new TaskInput("portrait-card", inputs));
        running.startChecking();
        persisted.put(running.getTaskId(), new TaskSnapshot(running, 1, now));
        execution.execute(running.getTaskId(), new ExecutionOptions(null, 0));
        verifyNoInteractions(assets, generator, media, delay);
        assertThat(saveCount.get()).isEqualTo(4);
    }

    @Test
    void ambientTransactionIsRejectedBeforeAnyRepositoryOrIo() {
        new TransactionTemplate(transactions).executeWithoutResult(status ->
                assertThatThrownBy(() -> execution.execute(id, new ExecutionOptions(null, 0)))
                        .isInstanceOf(IllegalStateException.class));
        verifyNoInteractions(repository, assets, generator, media, delay);
    }

    @Test
    void interruptionPersistsBestEffortFailureThenRestoresThreadInterrupt() throws Exception {
        doThrow(new InterruptedException()).when(delay).pause(anyInt());
        execution.execute(id, new ExecutionOptions(null, 0));
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        assertThat(persisted.get(id).getTask().getFailure().code()).isEqualTo("STAGE_EXECUTION_FAILED");
        verifyNoInteractions(assets, generator, media);
    }

    @Test
    void eachTaskRetainsItsOwnOptionsAndPersistedOwner() {
        UUID second = UUID.randomUUID();
        var other = ProductionTask.accept(second, UUID.randomUUID(), now, new TaskInput("portrait-card", List.of(inputs.get(0))));
        persisted.put(second, new TaskSnapshot(other, 0, now));
        execution.execute(id, new ExecutionOptions(TaskStage.GENERATE, 21));
        execution.execute(second, new ExecutionOptions(null, 9));
        assertThat(persisted.get(id).getTask().getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(persisted.get(second).getTask().getStatus()).isEqualTo(TaskStatus.SUCCEEDED);
        verify(assets).check(other.getOwnerId(), other.getInput().assetIds());
        assertThat(events.stream().filter(value -> value.startsWith("delay:")))
                .containsExactly("delay:21", "delay:21", "delay:9", "delay:9", "delay:9");
    }

    private void observe(TaskStage stage) {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(persisted.values()).anySatisfy(value -> {
            assertThat(value.getTask().getStatus()).isEqualTo(TaskStatus.RUNNING);
            assertThat(value.getExpectedStage()).isEqualTo(stage);
        });
        events.add("work:" + stage);
    }

    private TaskSnapshot copy(TaskSnapshot value) {
        var task = value.getTask();
        return new TaskSnapshot(ProductionTask.restore(task.getTaskId(), task.getOwnerId(), task.getCreatedAt(),
                task.getInput(), task.getStatus(), task.getCurrentStage(), task.getResult(), task.getFailure()),
                value.getRevision(), value.getUpdatedAt());
    }

    class LocalTransactions extends AbstractPlatformTransactionManager {
        Runnable onCommit;
        int commits;
        int failCommit;
        boolean applyBeforeFailure;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) {
            if (definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW) {
                assertThat(definition.getIsolationLevel()).isEqualTo(TransactionDefinition.ISOLATION_READ_COMMITTED);
                assertThat(definition.getTimeout()).isEqualTo(5);
            }
            onCommit = null;
        }
        @Override protected void doCommit(DefaultTransactionStatus status) {
            events.add("commit:" + ++commits);
            if (commits == failCommit && !applyBeforeFailure) throw new TransactionSystemException("commit unknown");
            if (onCommit != null) onCommit.run();
            if (commits == failCommit) throw new TransactionSystemException("commit unknown");
        }
        @Override protected void doRollback(DefaultTransactionStatus status) { onCommit = null; }
    }
}
