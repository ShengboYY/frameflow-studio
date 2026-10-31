package com.frameflow.studio.domain.asset.model.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record UploadedAsset(UUID assetId, UUID ownerId, String contentKey, String mediaType,
                            long sizeBytes, Instant createdAt) {
    public UploadedAsset {
        Objects.requireNonNull(assetId);
        Objects.requireNonNull(ownerId);
        Objects.requireNonNull(createdAt);
        if (contentKey == null || !contentKey.equals("upload:" + assetId)
                || !Set.of("image/jpeg", "image/png").contains(mediaType)
                || sizeBytes < 1 || sizeBytes > 8388608) {
            throw new IllegalArgumentException("Invalid uploaded asset");
        }
    }

    public boolean belongsTo(UUID owner) {
        return ownerId.equals(owner);
    }
}
