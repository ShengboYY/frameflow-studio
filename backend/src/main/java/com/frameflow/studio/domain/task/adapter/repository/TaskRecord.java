package com.frameflow.studio.domain.task.adapter.repository;

import java.time.Instant;
import java.util.UUID;
import com.frameflow.studio.domain.task.model.valobj.FailureInfo;
import com.frameflow.studio.domain.task.model.valobj.TaskResult;
import com.frameflow.studio.domain.task.model.valobj.TaskStage;
import com.frameflow.studio.domain.task.model.valobj.TaskStatus;

/** Immutable root projection; internal result references are mapped by the Case/HTTP boundary. */
public record TaskRecord(UUID taskId, UUID ownerId, String templateId, TaskStatus status,
                         TaskStage currentStage, long revision, Instant createdAt, Instant updatedAt,
                         TaskResult result, FailureInfo failure) {
}
