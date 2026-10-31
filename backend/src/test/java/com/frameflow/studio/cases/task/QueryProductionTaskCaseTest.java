package com.frameflow.studio.cases.task;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import com.frameflow.studio.cases.asset.IMediaResourceCase;
import com.frameflow.studio.cases.asset.model.AssetOperationException;
import com.frameflow.studio.cases.asset.model.MediaResource;
import com.frameflow.studio.cases.task.impl.QueryProductionTaskCaseImpl;
import com.frameflow.studio.cases.task.impl.ReadTaskResultCaseImpl;
import com.frameflow.studio.cases.task.model.TaskOperationException;
import com.frameflow.studio.domain.task.adapter.repository.IProductionTaskRepository;
import com.frameflow.studio.domain.task.adapter.repository.TaskRecord;
import com.frameflow.studio.domain.task.model.valobj.TaskResult;
import com.frameflow.studio.domain.task.model.valobj.TaskStage;
import com.frameflow.studio.domain.task.model.valobj.TaskStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QueryProductionTaskCaseTest {
    final IProductionTaskRepository repository = mock(IProductionTaskRepository.class);
    final IMediaResourceCase media = mock(IMediaResourceCase.class);
    final QueryProductionTaskCaseImpl query = new QueryProductionTaskCaseImpl(repository);
    final ReadTaskResultCaseImpl result = new ReadTaskResultCaseImpl(repository, media);
    final UUID owner = UUID.randomUUID();
    final UUID id = UUID.randomUUID();

    @Test
    void historyAndSingleReadMapSameRootFactsWithoutWritesAndHideInternalResultReference() {
        var record = record(TaskStatus.SUCCEEDED);
        when(repository.listOwned(owner)).thenReturn(List.of(record));
        when(repository.findOwned(id, owner)).thenReturn(Optional.of(record));
        var first = query.find(owner, id);
        assertThat(query.list(owner)).containsExactly(first);
        assertThat(query.find(owner, id)).isEqualTo(first);
        assertThat(first.result().mediaType()).isEqualTo("image/jpeg");
        assertThat(first.toString()).doesNotContain("demo:");
        verify(repository).listOwned(owner);
        verify(repository, times(2)).findOwned(id, owner);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(media);
    }

    @Test
    void missingAndOtherOwnersUseSameNotFoundAndNeverReadMedia() {
        when(repository.findOwned(any(), any())).thenReturn(Optional.empty());
        for (UUID target : List.of(owner, UUID.randomUUID())) {
            assertThatThrownBy(() -> query.find(target, id)).isInstanceOfSatisfying(TaskOperationException.class,
                    failure -> assertThat(failure.reason()).isEqualTo(TaskOperationException.Reason.NOT_FOUND));
            assertThatThrownBy(() -> result.read(target, id)).isInstanceOfSatisfying(TaskOperationException.class,
                    failure -> assertThat(failure.reason()).isEqualTo(TaskOperationException.Reason.NOT_FOUND));
        }
        verifyNoInteractions(media);
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"PENDING", "RUNNING", "FAILED"})
    void notSucceededNeverReadsResultResource(TaskStatus status) {
        when(repository.findOwned(id, owner)).thenReturn(Optional.of(record(status)));
        assertThatThrownBy(() -> result.read(owner, id)).isInstanceOfSatisfying(TaskOperationException.class,
                failure -> assertThat(failure.reason()).isEqualTo(TaskOperationException.Reason.RESULT_NOT_READY));
        verifyNoInteractions(media);
        verify(repository).findOwned(id, owner);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void successfulResultUsesAssetCapabilityAndResourceFailureNeverChangesTaskConclusion() {
        when(repository.findOwned(id, owner)).thenReturn(Optional.of(record(TaskStatus.SUCCEEDED)));
        var image = new MediaResource("demo:mock-result", "image/jpeg", new byte[]{1, 2});
        when(media.prepareResult("demo:mock-result")).thenReturn(image);
        assertThat(result.read(owner, id)).isEqualTo(image);
        when(media.prepareResult("demo:mock-result")).thenThrow(new AssetOperationException(AssetOperationException.Reason.UNAVAILABLE));
        assertThatThrownBy(() -> result.read(owner, id)).isInstanceOf(AssetOperationException.class);
        verify(repository, times(2)).findOwned(id, owner);
        verifyNoMoreInteractions(repository);
    }

    private TaskRecord record(TaskStatus status) {
        return new TaskRecord(id, owner, "portrait-card", status, status == TaskStatus.SUCCEEDED ? TaskStage.FINALIZE : null,
                4, Instant.parse("2026-10-31T01:02:03.123456Z"), Instant.parse("2026-10-31T01:02:04Z"),
                status == TaskStatus.SUCCEEDED ? new TaskResult("demo:mock-result") : null, null);
    }
}
