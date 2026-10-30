package com.frameflow.studio.domain.task.model.valobj;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record TaskInput(String templateId, List<UUID> assetIds) {

    public TaskInput {
        Objects.requireNonNull(templateId, "templateId is required");
        if (templateId.isBlank()) {
            throw new IllegalArgumentException("templateId must not be blank");
        }
        // Positions are significant, including repeated use of the same asset.
        assetIds = List.copyOf(assetIds);
        if (assetIds.isEmpty()) {
            throw new IllegalArgumentException("At least one input asset is required");
        }
    }
}
