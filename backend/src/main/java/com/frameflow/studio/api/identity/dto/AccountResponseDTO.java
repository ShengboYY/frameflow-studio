package com.frameflow.studio.api.identity.dto;

import java.util.UUID;

public record AccountResponseDTO(UUID ownerId, String username, String displayName) {}
