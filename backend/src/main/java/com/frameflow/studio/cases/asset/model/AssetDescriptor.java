package com.frameflow.studio.cases.asset.model;

import java.time.Instant;
import java.util.UUID;
import com.frameflow.studio.domain.asset.model.entity.UploadedAsset;

public record AssetDescriptor(UUID assetId, UUID ownerId, String contentReference, String mediaType,
                              long sizeBytes, Instant createdAt) {
    public static AssetDescriptor from(UploadedAsset asset) {
        return new AssetDescriptor(asset.assetId(), asset.ownerId(), asset.contentKey(), asset.mediaType(),
                asset.sizeBytes(), asset.createdAt());
    }
}
