package com.frameflow.studio.trigger.job.task;

import java.util.UUID;
import org.springframework.stereotype.Component;
import com.frameflow.studio.cases.task.IExecuteProductionTaskCase;
import com.frameflow.studio.cases.task.model.ExecutionOptions;
import com.frameflow.studio.cases.task.port.ITaskDispatcher;

@Component
public class ProductionTaskWorker implements ITaskDispatcher.ExecutionCallback {
    private final IExecuteProductionTaskCase execution;

    public ProductionTaskWorker(IExecuteProductionTaskCase execution) {
        this.execution = execution;
    }

    @Override
    public void execute(UUID taskId, ExecutionOptions options) {
        execution.execute(taskId, options);
    }
}
