package com.frameflow.studio.api.task.dto;

import java.time.Instant;
import java.util.UUID;

public record TaskResponseDTO(UUID taskId, UUID ownerId, String templateId, String status,
                              String currentStage, long revision, Instant createdAt, Instant updatedAt,
                              ResultDTO result, FailureDTO failure) {
    public record ResultDTO(String previewUrl, String mediaType) { }
    public record FailureDTO(String kind, String stage, String code, String message) { }
}
