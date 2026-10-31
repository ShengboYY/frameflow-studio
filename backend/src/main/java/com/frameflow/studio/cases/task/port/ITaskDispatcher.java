package com.frameflow.studio.cases.task.port;

import java.util.UUID;
import com.frameflow.studio.cases.task.model.ExecutionOptions;

public interface ITaskDispatcher {
    /** False guarantees this dispatch was not started or queued. No caller-thread execution. */
    boolean dispatch(UUID taskId, ExecutionOptions options);

    @FunctionalInterface
    interface ExecutionCallback {
        void execute(UUID taskId, ExecutionOptions options);
    }
}
