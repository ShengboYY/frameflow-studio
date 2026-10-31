package com.frameflow.studio.api.task.dto;

import java.time.Instant;
import java.util.UUID;

public record TaskAcceptedResponseDTO(UUID taskId, UUID ownerId, Instant createdAt) { }
