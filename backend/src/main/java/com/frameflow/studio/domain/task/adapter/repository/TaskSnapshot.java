package com.frameflow.studio.domain.task.adapter.repository;

import java.time.Instant;
import java.util.Objects;
import com.frameflow.studio.domain.task.model.aggregate.ProductionTask;
import com.frameflow.studio.domain.task.model.valobj.TaskStage;
import com.frameflow.studio.domain.task.model.valobj.TaskStatus;
import lombok.Getter;

/** Captures the persisted predicate before the working aggregate is changed. */
@Getter
public final class TaskSnapshot {
    private final ProductionTask task;
    private final long revision;
    private final TaskStatus expectedStatus;
    private final TaskStage expectedStage;
    private final Instant updatedAt;

    public TaskSnapshot(ProductionTask task, long revision, Instant updatedAt) {
        this.task = Objects.requireNonNull(task, "task is required");
        if (revision < 0) throw new IllegalArgumentException("revision must not be negative");
        this.revision = revision;
        this.expectedStatus = task.getStatus();
        this.expectedStage = task.getCurrentStage();
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");
    }
}
