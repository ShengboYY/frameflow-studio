package com.frameflow.studio.infrastructure.adapter.repository.asset;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import com.frameflow.studio.domain.asset.adapter.repository.IUploadedAssetRepository;
import com.frameflow.studio.domain.asset.model.entity.UploadedAsset;
import com.frameflow.studio.infrastructure.dao.asset.IUploadedAssetDao;
import com.frameflow.studio.infrastructure.dao.po.asset.UploadedAssetPO;

@Repository
public class UploadedAssetRepository implements IUploadedAssetRepository {
    private final IUploadedAssetDao dao;

    public UploadedAssetRepository(IUploadedAssetDao dao) { this.dao = dao; }

    @Override
    public void insert(UploadedAsset asset) {
        var po = new UploadedAssetPO();
        po.setAssetId(asset.assetId().toString());
        po.setOwnerId(asset.ownerId().toString());
        po.setContentKey(asset.contentKey());
        po.setMediaType(asset.mediaType());
        po.setSizeBytes(asset.sizeBytes());
        po.setCreatedAt(LocalDateTime.ofInstant(asset.createdAt(), ZoneOffset.UTC));
        if (dao.insert(po) != 1) throw new IllegalStateException("Asset insert did not complete");
    }

    @Override
    public Optional<UploadedAsset> findOwned(UUID assetId, UUID ownerId) {
        return Optional.ofNullable(dao.findOwned(assetId.toString(), ownerId.toString())).map(po ->
                new UploadedAsset(UUID.fromString(po.getAssetId()), UUID.fromString(po.getOwnerId()),
                        po.getContentKey(), po.getMediaType(), po.getSizeBytes(), po.getCreatedAt().toInstant(ZoneOffset.UTC)));
    }
}
