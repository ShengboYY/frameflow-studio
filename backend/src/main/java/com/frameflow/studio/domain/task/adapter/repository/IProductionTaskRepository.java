package com.frameflow.studio.domain.task.adapter.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.frameflow.studio.domain.task.model.aggregate.ProductionTask;

public interface IProductionTaskRepository {
    /** The calling Case owns the short transaction, including all input bindings. */
    void insert(ProductionTask task);

    /** Trusted worker lookup; user-facing reads must use the owner-scoped queries. */
    Optional<TaskSnapshot> findById(UUID taskId);

    /**
     * Apply one aggregate behavior to the loaded task before saving. False means
     * lost concurrency eligibility; even true requires a successful outer commit
     * before any external work. Reload in the next transaction, never reuse a snapshot.
     */
    boolean save(TaskSnapshot loaded, Instant updatedAt);

    Optional<TaskRecord> findOwned(UUID taskId, UUID ownerId);
    List<TaskRecord> listOwned(UUID ownerId);
}
