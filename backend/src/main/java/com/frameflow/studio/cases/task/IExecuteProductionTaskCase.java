package com.frameflow.studio.cases.task;

import java.util.UUID;
import com.frameflow.studio.cases.task.model.ExecutionOptions;

/** Trusted background entry only; owner and inputs are always loaded from the persisted task. */
public interface IExecuteProductionTaskCase {
    void execute(UUID taskId, ExecutionOptions options);
}
