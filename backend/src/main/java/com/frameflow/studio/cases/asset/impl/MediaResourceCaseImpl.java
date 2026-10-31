package com.frameflow.studio.cases.asset.impl;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import com.frameflow.studio.cases.asset.IMediaResourceCase;
import com.frameflow.studio.cases.asset.model.AssetOperationException;
import com.frameflow.studio.cases.asset.model.MediaResource;
import com.frameflow.studio.domain.asset.adapter.port.IAssetStoragePort;
import com.frameflow.studio.domain.asset.adapter.port.IDemoMediaPort;
import com.frameflow.studio.domain.asset.adapter.repository.IUploadedAssetRepository;
import com.frameflow.studio.domain.asset.model.valobj.AssetException;

@Service
public class MediaResourceCaseImpl implements IMediaResourceCase {
    private final IDemoMediaPort media;
    private final IUploadedAssetRepository repository;
    private final IAssetStoragePort storage;

    public MediaResourceCaseImpl(IDemoMediaPort media, IUploadedAssetRepository repository, IAssetStoragePort storage) {
        this.media = media;
        this.repository = repository;
        this.storage = storage;
    }

    @Override
    public MediaResource input(UUID ownerId, UUID assetId) {
        try {
            return readInput(ownerId, assetId);
        } catch (AssetException failure) {
            throw AssetOperationException.from(failure);
        }
    }

    private MediaResource readInput(UUID ownerId, UUID assetId) {
        var asset = repository.findOwned(assetId, ownerId).filter(value -> value.belongsTo(ownerId))
                .orElseThrow(() -> new AssetException(AssetException.Reason.NOT_FOUND));
        byte[] bytes = storage.read(asset.contentKey());
        if (bytes.length != asset.sizeBytes()) throw new AssetException(AssetException.Reason.UNAVAILABLE);
        return new MediaResource(asset.contentKey(), asset.mediaType(), bytes);
    }

    @Override
    public Optional<MediaResource> findCover(String mediaKey) {
        return media.findCover(mediaKey).map(MediaResource::from);
    }

    @Override
    public MediaResource prepareResult(String candidateReference) {
        // Resolve only registered fixtures, never a caller-supplied filesystem or classpath location.
        return media.findResult(candidateReference).map(MediaResource::from)
                .orElseThrow(() -> new AssetOperationException(AssetOperationException.Reason.UNAVAILABLE));
    }
}
