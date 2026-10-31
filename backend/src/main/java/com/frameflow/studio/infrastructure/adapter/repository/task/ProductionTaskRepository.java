package com.frameflow.studio.infrastructure.adapter.repository.task;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.frameflow.studio.domain.task.adapter.repository.IProductionTaskRepository;
import com.frameflow.studio.domain.task.adapter.repository.TaskRecord;
import com.frameflow.studio.domain.task.adapter.repository.TaskSnapshot;
import com.frameflow.studio.domain.task.model.aggregate.ProductionTask;
import com.frameflow.studio.domain.task.model.valobj.FailureInfo;
import com.frameflow.studio.domain.task.model.valobj.FailureKind;
import com.frameflow.studio.domain.task.model.valobj.TaskInput;
import com.frameflow.studio.domain.task.model.valobj.TaskResult;
import com.frameflow.studio.domain.task.model.valobj.TaskStage;
import com.frameflow.studio.domain.task.model.valobj.TaskStatus;
import com.frameflow.studio.infrastructure.dao.po.task.ProductionTaskPO;
import com.frameflow.studio.infrastructure.dao.po.task.TaskInputAssetPO;
import com.frameflow.studio.infrastructure.dao.task.IProductionTaskDao;
import com.frameflow.studio.infrastructure.dao.task.ITaskInputAssetDao;

@Repository
public class ProductionTaskRepository implements IProductionTaskRepository {
    private final IProductionTaskDao tasks;
    private final ITaskInputAssetDao inputs;

    public ProductionTaskRepository(IProductionTaskDao tasks, ITaskInputAssetDao inputs) {
        this.tasks = tasks;
        this.inputs = inputs;
    }

    @Override
    public void insert(ProductionTask task) {
        requireWriteTransaction();
        if (task.getStatus() != TaskStatus.PENDING) {
            throw new IllegalArgumentException("Only a newly accepted task can be inserted");
        }
        if (tasks.insert(toPO(task, task.getCreatedAt())) != 1) {
            throw new IllegalStateException("Task insert did not complete");
        }
        for (int position = 0; position < task.getInput().assetIds().size(); position++) {
            var po = new TaskInputAssetPO();
            po.setTaskId(task.getTaskId().toString());
            po.setPosition(position);
            po.setAssetId(task.getInput().assetIds().get(position).toString());
            if (inputs.insert(po) != 1) throw new IllegalStateException("Task input insert did not complete");
        }
    }

    @Override
    public Optional<TaskSnapshot> findById(UUID taskId) {
        return Optional.ofNullable(tasks.findById(taskId.toString())).map(po -> {
            var bindings = inputs.findByTaskId(po.getTaskId());
            // Bindings never change after atomic creation. A root read therefore
            // remains compatible with the subsequent ordered binding read.
            for (int position = 0; position < bindings.size(); position++) {
                if (bindings.get(position).getPosition() != position) {
                    throw new IllegalStateException("Persisted task input positions are not contiguous");
                }
            }
            var input = new TaskInput(po.getTemplateId(), bindings.stream()
                    .map(binding -> UUID.fromString(binding.getAssetId())).toList());
            var task = ProductionTask.restore(UUID.fromString(po.getTaskId()), UUID.fromString(po.getOwnerId()),
                    po.getCreatedAt().toInstant(ZoneOffset.UTC), input, TaskStatus.valueOf(po.getStatus()),
                    stage(po), result(po), failure(po));
            return new TaskSnapshot(task, po.getRevision(), po.getUpdatedAt().toInstant(ZoneOffset.UTC));
        });
    }

    @Override
    public boolean save(TaskSnapshot loaded, Instant updatedAt) {
        requireWriteTransaction();
        int affected = tasks.updateConclusion(toPO(loaded.getTask(), updatedAt), loaded.getRevision(),
                loaded.getExpectedStatus().name(),
                loaded.getExpectedStage() == null ? null : loaded.getExpectedStage().name());
        if (affected < 0 || affected > 1) throw new IllegalStateException("Unexpected task update count");
        return affected == 1;
    }

    @Override
    public Optional<TaskRecord> findOwned(UUID taskId, UUID ownerId) {
        return Optional.ofNullable(tasks.findOwned(taskId.toString(), ownerId.toString())).map(this::toRecord);
    }

    @Override
    public List<TaskRecord> listOwned(UUID ownerId) {
        return tasks.listOwned(ownerId.toString()).stream().map(this::toRecord).toList();
    }

    private TaskRecord toRecord(ProductionTaskPO po) {
        return new TaskRecord(UUID.fromString(po.getTaskId()), UUID.fromString(po.getOwnerId()), po.getTemplateId(),
                TaskStatus.valueOf(po.getStatus()), stage(po), po.getRevision(),
                po.getCreatedAt().toInstant(ZoneOffset.UTC), po.getUpdatedAt().toInstant(ZoneOffset.UTC),
                result(po), failure(po));
    }

    private ProductionTaskPO toPO(ProductionTask task, Instant updatedAt) {
        var po = new ProductionTaskPO();
        po.setTaskId(task.getTaskId().toString());
        po.setOwnerId(task.getOwnerId().toString());
        po.setTemplateId(task.getInput().templateId());
        po.setStatus(task.getStatus().name());
        po.setCurrentStage(task.getCurrentStage() == null ? null : task.getCurrentStage().name());
        po.setResultRef(task.getResult() == null ? null : task.getResult().resultReference());
        if (task.getFailure() != null) {
            po.setFailureKind(task.getFailure().kind().name());
            po.setFailureCode(task.getFailure().code());
            po.setFailureMessage(task.getFailure().message());
        }
        po.setCreatedAt(LocalDateTime.ofInstant(task.getCreatedAt(), ZoneOffset.UTC));
        po.setUpdatedAt(LocalDateTime.ofInstant(updatedAt, ZoneOffset.UTC));
        return po;
    }

    private TaskStage stage(ProductionTaskPO po) {
        return po.getCurrentStage() == null ? null : TaskStage.valueOf(po.getCurrentStage());
    }

    private TaskResult result(ProductionTaskPO po) {
        return po.getResultRef() == null ? null : new TaskResult(po.getResultRef());
    }

    private FailureInfo failure(ProductionTaskPO po) {
        return po.getFailureKind() == null ? null : new FailureInfo(FailureKind.valueOf(po.getFailureKind()),
                stage(po), po.getFailureCode(), po.getFailureMessage());
    }

    private void requireWriteTransaction() {
        // The Case controls commit; autocommit here could leave a root without inputs.
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("Task writes require a writable Case transaction");
        }
    }
}
