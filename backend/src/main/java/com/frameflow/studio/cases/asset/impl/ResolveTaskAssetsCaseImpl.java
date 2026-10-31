package com.frameflow.studio.cases.asset.impl;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import com.frameflow.studio.cases.asset.ICheckTaskAssetsCase;
import com.frameflow.studio.cases.asset.IResolveTaskAssetsCase;
import com.frameflow.studio.cases.asset.model.AssetDescriptor;
import com.frameflow.studio.cases.asset.model.AssetOperationException;
import com.frameflow.studio.domain.asset.adapter.port.IAssetStoragePort;
import com.frameflow.studio.domain.asset.adapter.repository.IUploadedAssetRepository;
import com.frameflow.studio.domain.asset.model.valobj.AssetException;

@Service
public class ResolveTaskAssetsCaseImpl implements IResolveTaskAssetsCase, ICheckTaskAssetsCase {
    private final IUploadedAssetRepository repository;
    private final IAssetStoragePort storage;

    public ResolveTaskAssetsCaseImpl(IUploadedAssetRepository repository, IAssetStoragePort storage) {
        this.repository = repository;
        this.storage = storage;
    }

    @Override
    public List<AssetDescriptor> resolve(UUID ownerId, List<UUID> assetIds) {
        try {
            return performResolve(ownerId, assetIds);
        } catch (AssetException failure) {
            throw AssetOperationException.from(failure);
        }
    }

    private List<AssetDescriptor> performResolve(UUID ownerId, List<UUID> assetIds) {
        return assetIds.stream().map(id -> {
            var asset = repository.findOwned(id, ownerId)
                    .filter(value -> value.belongsTo(ownerId))
                    .orElseThrow(() -> new AssetException(AssetException.Reason.NOT_FOUND));
            if (storage.read(asset.contentKey()).length != asset.sizeBytes()) {
                throw new AssetException(AssetException.Reason.UNAVAILABLE);
            }
            return AssetDescriptor.from(asset);
        }).toList();
    }

    @Override
    public void check(UUID ownerId, List<UUID> assetIds) {
        // Each execution checks real storage again; no readiness result is cached at acceptance.
        resolve(ownerId, assetIds);
    }
}
