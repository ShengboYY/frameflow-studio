package com.frameflow.studio.cases.task.impl;

import java.util.UUID;
import org.springframework.stereotype.Service;
import com.frameflow.studio.cases.asset.IMediaResourceCase;
import com.frameflow.studio.cases.asset.model.MediaResource;
import com.frameflow.studio.cases.task.IReadTaskResultCase;
import com.frameflow.studio.cases.task.model.TaskOperationException;
import com.frameflow.studio.domain.task.adapter.repository.IProductionTaskRepository;
import com.frameflow.studio.domain.task.model.valobj.TaskStatus;

@Service
public class ReadTaskResultCaseImpl implements IReadTaskResultCase {
    private final IProductionTaskRepository repository;
    private final IMediaResourceCase media;

    public ReadTaskResultCaseImpl(IProductionTaskRepository repository, IMediaResourceCase media) {
        this.repository = repository;
        this.media = media;
    }

    @Override
    public MediaResource read(UUID ownerId, UUID taskId) {
        var task = repository.findOwned(taskId, ownerId)
                .orElseThrow(() -> new TaskOperationException(TaskOperationException.Reason.NOT_FOUND));
        if (task.status() != TaskStatus.SUCCEEDED) {
            throw new TaskOperationException(TaskOperationException.Reason.RESULT_NOT_READY);
        }
        return media.prepareResult(task.result().resultReference());
    }
}
