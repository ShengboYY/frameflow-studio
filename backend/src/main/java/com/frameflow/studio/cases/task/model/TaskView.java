package com.frameflow.studio.cases.task.model;

import java.time.Instant;
import java.util.UUID;
import com.frameflow.studio.domain.task.adapter.repository.TaskRecord;

/** Public facts omit internal storage references; the HTTP boundary derives resource URLs. */
public record TaskView(UUID taskId, UUID ownerId, String templateId, String status,
                       String currentStage, long revision, Instant createdAt, Instant updatedAt,
                       Result result, Failure failure) {
    public record Result(String mediaType) { }
    public record Failure(String kind, String stage, String code, String message) { }

    public static TaskView from(TaskRecord record) {
        var failure = record.failure();
        return new TaskView(record.taskId(), record.ownerId(), record.templateId(), record.status().name(),
                record.currentStage() == null ? null : record.currentStage().name(), record.revision(), record.createdAt(), record.updatedAt(),
                record.result() == null ? null : new Result("image/jpeg"), failure == null ? null : new Failure(
                        failure.kind().name(), failure.stage() == null ? null : failure.stage().name(), failure.code(), failure.message()));
    }
}
