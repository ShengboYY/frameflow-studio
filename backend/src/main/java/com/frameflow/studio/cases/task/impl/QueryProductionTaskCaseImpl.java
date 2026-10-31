package com.frameflow.studio.cases.task.impl;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import com.frameflow.studio.cases.task.IQueryProductionTaskCase;
import com.frameflow.studio.cases.task.model.TaskOperationException;
import com.frameflow.studio.cases.task.model.TaskView;
import com.frameflow.studio.domain.task.adapter.repository.IProductionTaskRepository;

@Service
public class QueryProductionTaskCaseImpl implements IQueryProductionTaskCase {
    private final IProductionTaskRepository repository;

    public QueryProductionTaskCaseImpl(IProductionTaskRepository repository) { this.repository = repository; }

    @Override
    public List<TaskView> list(UUID ownerId) {
        return repository.listOwned(ownerId).stream().map(TaskView::from).toList();
    }

    @Override
    public TaskView find(UUID ownerId, UUID taskId) {
        return repository.findOwned(taskId, ownerId).map(TaskView::from)
                .orElseThrow(() -> new TaskOperationException(TaskOperationException.Reason.NOT_FOUND));
    }
}
