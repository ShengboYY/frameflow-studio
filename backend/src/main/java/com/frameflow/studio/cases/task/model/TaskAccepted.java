package com.frameflow.studio.cases.task.model;

import java.time.Instant;
import java.util.UUID;

public record TaskAccepted(UUID taskId, UUID ownerId, Instant createdAt) { }
