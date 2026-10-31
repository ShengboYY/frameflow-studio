package com.frameflow.studio.cases.task;

import java.util.List;
import java.util.UUID;
import com.frameflow.studio.cases.task.model.ExecutionOptions;
import com.frameflow.studio.cases.task.model.TaskAccepted;

public interface ICreateProductionTaskCase {
    TaskAccepted create(UUID ownerId, String templateId, List<UUID> assetIds, ExecutionOptions options);
}
