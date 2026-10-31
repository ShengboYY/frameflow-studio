package com.frameflow.studio.cases.task.model;

import com.frameflow.studio.domain.task.model.valobj.TaskStage;

/** Frozen at acceptance, carried only in the local dispatch envelope, never global worker state. */
public record ExecutionOptions(TaskStage failAtStage, int stageDelayMs) {
    public ExecutionOptions {
        if (stageDelayMs < 0 || stageDelayMs > 30000) {
            throw new IllegalArgumentException("Stage delay must be between 0 and 30000 ms");
        }
    }
}
