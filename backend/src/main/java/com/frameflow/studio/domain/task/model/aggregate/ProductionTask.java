package com.frameflow.studio.domain.task.model.aggregate;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.frameflow.studio.domain.task.model.valobj.FailureInfo;
import com.frameflow.studio.domain.task.model.valobj.FailureKind;
import com.frameflow.studio.domain.task.model.valobj.TaskInput;
import com.frameflow.studio.domain.task.model.valobj.TaskResult;
import com.frameflow.studio.domain.task.model.valobj.TaskStage;
import com.frameflow.studio.domain.task.model.valobj.TaskStatus;
import lombok.Getter;

/**
 * Owns one task's fixed input and lifecycle conclusion. Callers must persist an
 * authorized transition before performing the next stage's external work.
 * This in-memory guard does not replace repository concurrency checks.
 */
@Getter
public final class ProductionTask {

    private final UUID taskId;
    private final UUID ownerId;
    private final Instant createdAt;
    private final TaskInput input;
    private TaskStatus status;
    private TaskStage currentStage;
    private TaskResult result;
    private FailureInfo failure;

    private ProductionTask(UUID taskId, UUID ownerId, Instant createdAt, TaskInput input,
                           TaskStatus status, TaskStage currentStage, TaskResult result, FailureInfo failure) {
        this.taskId = Objects.requireNonNull(taskId, "taskId is required");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId is required");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
        this.input = Objects.requireNonNull(input, "input is required");
        this.status = Objects.requireNonNull(status, "status is required");
        validateConclusion(status, currentStage, result, failure);
        this.currentStage = currentStage;
        this.result = result;
        this.failure = failure;
    }

    /** External template, ownership and resource checks belong to the accepting Case. */
    public static ProductionTask accept(UUID taskId, UUID ownerId, Instant createdAt, TaskInput input) {
        return new ProductionTask(taskId, ownerId, createdAt, input, TaskStatus.PENDING, null, null, null);
    }

    /** Reconstitutes facts without replaying acceptance, execution, clocks or I/O. */
    public static ProductionTask restore(UUID taskId, UUID ownerId, Instant createdAt, TaskInput input,
                                         TaskStatus status, TaskStage currentStage,
                                         TaskResult result, FailureInfo failure) {
        return new ProductionTask(taskId, ownerId, createdAt, input, status, currentStage, result, failure);
    }

    public void startChecking() {
        requireState(TaskStatus.PENDING, null);
        status = TaskStatus.RUNNING;
        currentStage = TaskStage.CHECK_ASSETS;
    }

    public void completeChecking() {
        requireState(TaskStatus.RUNNING, TaskStage.CHECK_ASSETS);
        currentStage = TaskStage.GENERATE;
    }

    /** The execution Case retains and validates the generation candidate. */
    public void completeGeneration() {
        requireState(TaskStatus.RUNNING, TaskStage.GENERATE);
        currentStage = TaskStage.FINALIZE;
    }

    public void succeed(TaskResult readyResult) {
        requireState(TaskStatus.RUNNING, TaskStage.FINALIZE);
        result = Objects.requireNonNull(readyResult, "A ready result is required for success");
        status = TaskStatus.SUCCEEDED;
    }

    /** A late failure from a previous stage must not fail the current stage. */
    public void failCurrentStage(TaskStage failedStage, String code, String message) {
        Objects.requireNonNull(failedStage, "failedStage is required");
        requireState(TaskStatus.RUNNING, failedStage);
        FailureInfo stageFailure = new FailureInfo(FailureKind.STAGE, failedStage, code, message);
        failure = stageFailure;
        status = TaskStatus.FAILED;
    }

    public void failBeforeStart(String code, String message) {
        requireState(TaskStatus.PENDING, null);
        FailureInfo dispatchFailure = new FailureInfo(FailureKind.DISPATCH, null, code, message);
        failure = dispatchFailure;
        status = TaskStatus.FAILED;
    }

    private void requireState(TaskStatus expectedStatus, TaskStage expectedStage) {
        if (status != expectedStatus || currentStage != expectedStage) {
            throw new IllegalStateException("Task lifecycle does not allow this transition");
        }
    }

    private static void validateConclusion(TaskStatus status, TaskStage stage,
                                           TaskResult result, FailureInfo failure) {
        boolean valid = switch (status) {
            case PENDING -> stage == null && result == null && failure == null;
            case RUNNING -> stage != null && result == null && failure == null;
            case SUCCEEDED -> stage == TaskStage.FINALIZE && result != null && failure == null;
            case FAILED -> result == null && failure != null && failure.stage() == stage
                    && ((failure.kind() == FailureKind.DISPATCH && stage == null)
                    || (failure.kind() == FailureKind.STAGE && stage != null));
        };
        if (!valid) {
            throw new IllegalArgumentException("Inconsistent task status, stage, result or failure");
        }
    }
}
