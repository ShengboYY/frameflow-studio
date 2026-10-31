package com.frameflow.studio.api.asset.dto;

import java.time.Instant;
import java.util.UUID;

public record AssetResponseDTO(UUID assetId, UUID ownerId, String mediaType, long sizeBytes,
                               Instant createdAt, String previewUrl) {}
