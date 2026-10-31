package com.frameflow.studio.cases.task;

import java.util.List;
import java.util.UUID;
import com.frameflow.studio.cases.task.model.TaskView;

public interface IQueryProductionTaskCase {
    List<TaskView> list(UUID ownerId);
    TaskView find(UUID ownerId, UUID taskId);
}
