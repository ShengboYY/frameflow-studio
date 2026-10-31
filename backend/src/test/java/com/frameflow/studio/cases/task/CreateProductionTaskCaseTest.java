package com.frameflow.studio.cases.task;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.frameflow.studio.cases.asset.IResolveTaskAssetsCase;
import com.frameflow.studio.cases.asset.model.AssetDescriptor;
import com.frameflow.studio.cases.asset.model.AssetOperationException;
import com.frameflow.studio.cases.task.impl.CreateProductionTaskCaseImpl;
import com.frameflow.studio.cases.task.model.ExecutionOptions;
import com.frameflow.studio.cases.task.model.TaskOperationException;
import com.frameflow.studio.cases.task.port.ITaskDispatcher;
import com.frameflow.studio.cases.template.ITemplateCatalogCase;
import com.frameflow.studio.cases.template.model.TemplateValidation;
import com.frameflow.studio.cases.template.model.TemplateView;
import com.frameflow.studio.domain.task.adapter.repository.IProductionTaskRepository;
import com.frameflow.studio.domain.task.adapter.repository.TaskSnapshot;
import com.frameflow.studio.domain.task.model.aggregate.ProductionTask;
import com.frameflow.studio.domain.task.model.valobj.TaskStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CreateProductionTaskCaseTest {
    final IProductionTaskRepository repository = mock(IProductionTaskRepository.class);
    final IResolveTaskAssetsCase assets = mock(IResolveTaskAssetsCase.class);
    final ITemplateCatalogCase templates = mock(ITemplateCatalogCase.class);
    final ITaskDispatcher dispatcher = mock(ITaskDispatcher.class);
    final LocalTransactions transactions = new LocalTransactions();
    final Instant now = Instant.parse("2026-10-31T01:02:03.123456789Z");
    final UUID owner = UUID.randomUUID();
    final UUID asset = UUID.randomUUID();
    final List<UUID> ids = List.of(asset, asset);
    final TemplateView template = new TemplateView("travel-postcard", "旅行", "travel", "介绍", "postcard-cover", 2,
            List.of("image/jpeg"), 8388608);
    CreateProductionTaskCaseImpl create;
    ProductionTask persisted;
    ProductionTask inserted;
    boolean rejectedSaved;

    @BeforeEach
    void prepare() {
        create = new CreateProductionTaskCaseImpl(repository, assets, templates, dispatcher,
                Clock.fixed(now, ZoneOffset.UTC), transactions);
        when(templates.findById("travel-postcard")).thenReturn(Optional.of(template));
        when(assets.resolve(owner, ids)).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return List.of(descriptor(), descriptor());
        });
        when(templates.validateSubmission(eq("travel-postcard"), anyList()))
                .thenReturn(Optional.of(new TemplateValidation(template, List.of())));
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(transactions.definition.getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            assertThat(transactions.definition.getIsolationLevel()).isEqualTo(TransactionDefinition.ISOLATION_READ_COMMITTED);
            assertThat(transactions.definition.getTimeout()).isEqualTo(5);
            inserted = call.getArgument(0);
            transactions.onCommit = () -> persisted = inserted;
            return null;
        }).when(repository).insert(any());
        when(dispatcher.dispatch(any(), any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(persisted.getTaskId()).isEqualTo(call.getArgument(0));
            return true;
        });
        when(repository.findById(any())).thenAnswer(call -> Optional.of(snapshot()));
        when(repository.save(any(), any())).thenAnswer(call -> {
            var loaded = call.getArgument(0, TaskSnapshot.class);
            assertThat(loaded.getRevision()).isZero();
            assertThat(loaded.getExpectedStatus()).isEqualTo(TaskStatus.PENDING);
            assertThat(loaded.getTask().getFailure().code()).isEqualTo("DISPATCH_REJECTED");
            assertThat(loaded.getTask().getCurrentStage()).isNull();
            transactions.onCommit = () -> { persisted = loaded.getTask(); rejectedSaved = true; };
            return true;
        });
    }

    @Test
    void freezesOrderedRepeatedInputsAndEachTasksOptionsAndDispatchesOnlyAfterCommit() {
        var mutable = new ArrayList<>(ids);
        var firstOptions = new ExecutionOptions(null, 71);
        var first = create.create(owner, "travel-postcard", mutable, firstOptions);
        mutable.clear();
        assertThat(persisted.getInput().assetIds()).containsExactly(asset, asset);
        assertThat(first.ownerId()).isEqualTo(owner);
        assertThat(first.createdAt()).isEqualTo(Instant.parse("2026-10-31T01:02:03.123456Z"));
        var secondOptions = ExecutionOptions.from("GENERATE", 19);
        var second = create.create(owner, "travel-postcard", ids, secondOptions);
        assertThat(second.taskId()).isNotEqualTo(first.taskId());
        verify(dispatcher).dispatch(first.taskId(), firstOptions);
        verify(dispatcher).dispatch(second.taskId(), secondOptions);
        verify(templates, times(2)).validateSubmission("travel-postcard", List.of(
                new TemplateValidation.ImageInput("image/jpeg", 123), new TemplateValidation.ImageInput("image/jpeg", 123)));
    }

    @Test
    void invalidTemplateAssetsAndRequirementsNeverCreateOrDispatch() {
        assertThatThrownBy(() -> create.create(owner, "missing", ids, new ExecutionOptions(null, 0)))
                .isInstanceOf(TaskOperationException.class);
        verifyNoInteractions(assets, repository, dispatcher);
        doThrow(new AssetOperationException(AssetOperationException.Reason.NOT_FOUND)).when(assets).resolve(owner, ids);
        assertThatThrownBy(() -> create.create(owner, "travel-postcard", ids, new ExecutionOptions(null, 0)))
                .isInstanceOf(AssetOperationException.class);
        reset(assets);
        when(assets.resolve(owner, ids)).thenReturn(List.of(descriptor(), descriptor()));
        doReturn(Optional.of(new TemplateValidation(template, List.of(TemplateValidation.Violation.IMAGE_SIZE))))
                .when(templates).validateSubmission(eq("travel-postcard"), anyList());
        assertThatThrownBy(() -> create.create(owner, "travel-postcard", ids, new ExecutionOptions(null, 0)))
                .isInstanceOfSatisfying(TaskOperationException.class,
                        failure -> {
                            assertThat(failure.reason()).isEqualTo(TaskOperationException.Reason.REQUIREMENTS_MISMATCH);
                            assertThat(failure.violations()).containsExactly(TemplateValidation.Violation.IMAGE_SIZE);
                        });
        verifyNoInteractions(repository, dispatcher);
    }

    @Test
    void ambientTransactionIsRejectedBeforeAnyValidationIoOrWrites() {
        new TransactionTemplate(transactions).executeWithoutResult(status ->
                assertThatThrownBy(() -> create.create(owner, "travel-postcard", ids, new ExecutionOptions(null, 0)))
                        .isInstanceOf(IllegalStateException.class));
        verifyNoInteractions(repository, assets, templates, dispatcher);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 3, 1600})
    void wrongImageCountIsRejectedBeforeResolvingOrReadingAnyAssets(int count) {
        var invalidIds = java.util.Collections.nCopies(count, asset);
        doReturn(invalidIds.stream().map(id -> descriptor()).toList()).when(assets).resolve(owner, invalidIds);
        doReturn(Optional.of(new TemplateValidation(template, List.of(TemplateValidation.Violation.IMAGE_COUNT))))
                .when(templates).validateSubmission(eq("travel-postcard"), anyList());
        assertThatThrownBy(() -> create.create(owner, "travel-postcard", invalidIds, new ExecutionOptions(null, 0)))
                .isInstanceOfSatisfying(TaskOperationException.class, failure -> {
                    assertThat(failure.reason()).isEqualTo(TaskOperationException.Reason.REQUIREMENTS_MISMATCH);
                    assertThat(failure.violations()).containsExactly(TemplateValidation.Violation.IMAGE_COUNT);
                });
        verifyNoInteractions(assets, repository, dispatcher);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void lostCommitAcknowledgementNeverRetriesOrDispatchesAndReportsTraceableId(boolean commitApplied) {
        transactions.failCommit = 1;
        transactions.applyBeforeFailure = commitApplied;
        assertThatThrownBy(() -> create.create(owner, "travel-postcard", ids, new ExecutionOptions(null, 0)))
                .isInstanceOfSatisfying(TaskOperationException.class, failure -> {
                    assertThat(failure.reason()).isEqualTo(TaskOperationException.Reason.SUBMISSION_OUTCOME_UNKNOWN);
                    assertThat(failure.taskId()).isEqualTo(inserted.getTaskId());
                });
        assertThat(persisted != null).isEqualTo(commitApplied);
        verify(repository).insert(any());
        verifyNoInteractions(dispatcher);
    }

    @Test
    void explicitWriteFailureRollsBackAndNeverDispatches() {
        doThrow(new IllegalStateException("write failed")).when(repository).insert(any());
        assertThatThrownBy(() -> create.create(owner, "travel-postcard", ids, new ExecutionOptions(null, 0)))
                .isInstanceOf(TaskOperationException.class);
        assertThat(transactions.rollbacks).isEqualTo(1);
        assertThat(persisted).isNull();
        verifyNoInteractions(dispatcher);
    }

    @Test
    void explicitRejectionCommitsDispatchFailureButStillReturnsAcceptedIdentity() {
        doReturn(false).when(dispatcher).dispatch(any(), any());
        var accepted = create.create(owner, "travel-postcard", ids, new ExecutionOptions(null, 0));
        assertThat(accepted.taskId()).isEqualTo(persisted.getTaskId());
        assertThat(rejectedSaved).isTrue();
        assertThat(persisted.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(transactions.commits).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void rejectedDispatchDoesNotOverwriteWinnerOrRepeatOnWriteOrCommitFailure(int failure) {
        doReturn(false).when(dispatcher).dispatch(any(), any());
        if (failure == 0) {
            doReturn(false).when(repository).save(any(), any());
        } else if (failure == 1) {
            doThrow(new IllegalStateException("write failed")).when(repository).save(any(), any());
        } else transactions.failCommit = 2;
        var accepted = create.create(owner, "travel-postcard", ids, new ExecutionOptions(null, 0));
        assertThat(accepted.taskId()).isEqualTo(persisted.getTaskId());
        assertThat(persisted.getStatus()).isEqualTo(TaskStatus.PENDING);
        verify(dispatcher).dispatch(any(), any());
        verify(repository).insert(any());
        verify(repository).save(any(), any());
        if (failure == 0) verify(repository, times(2)).findById(any());
    }

    @Test
    void rejectionCannotFailAlreadyClaimedTaskAndUnknownDispatchOutcomeDoesNotInventFailure() {
        doAnswer(call -> { persisted.startChecking(); return false; }).when(dispatcher).dispatch(any(), any());
        create.create(owner, "travel-postcard", ids, new ExecutionOptions(null, 0));
        assertThat(persisted.getStatus()).isEqualTo(TaskStatus.RUNNING);
        verify(repository, never()).save(any(), any());
        doThrow(new IllegalStateException("acknowledgement unknown")).when(dispatcher).dispatch(any(), any());
        var accepted = create.create(owner, "travel-postcard", ids, new ExecutionOptions(null, 0));
        assertThat(accepted.taskId()).isEqualTo(persisted.getTaskId());
        assertThat(persisted.getStatus()).isEqualTo(TaskStatus.PENDING);
        verify(repository, never()).save(any(), any());
    }

    private AssetDescriptor descriptor() { return new AssetDescriptor(asset, owner, "upload:hidden", "image/jpeg", 123, now); }
    private TaskSnapshot snapshot() {
        return new TaskSnapshot(ProductionTask.restore(persisted.getTaskId(), owner, persisted.getCreatedAt(), persisted.getInput(),
                persisted.getStatus(), persisted.getCurrentStage(), persisted.getResult(), persisted.getFailure()), 0, persisted.getCreatedAt());
    }

    class LocalTransactions extends AbstractPlatformTransactionManager {
        TransactionDefinition definition;
        Runnable onCommit;
        int commits;
        int rollbacks;
        int failCommit;
        boolean applyBeforeFailure;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) {
            this.definition = definition;
            onCommit = null;
        }
        @Override protected void doCommit(DefaultTransactionStatus status) {
            commits++;
            if (commits == failCommit && !applyBeforeFailure) throw new TransactionSystemException("commit unknown");
            if (onCommit != null) onCommit.run();
            if (commits == failCommit) throw new TransactionSystemException("commit unknown");
        }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; onCommit = null; }
    }
}
