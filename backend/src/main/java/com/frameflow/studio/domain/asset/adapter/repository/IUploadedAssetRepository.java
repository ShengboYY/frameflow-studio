package com.frameflow.studio.domain.asset.adapter.repository;

import java.util.Optional;
import java.util.UUID;
import com.frameflow.studio.domain.asset.model.entity.UploadedAsset;

public interface IUploadedAssetRepository {
    void insert(UploadedAsset asset);
    Optional<UploadedAsset> findOwned(UUID assetId, UUID ownerId);
}
