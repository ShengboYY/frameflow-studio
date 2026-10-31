package com.frameflow.studio.cases.asset;

import java.util.Optional;
import java.util.UUID;
import com.frameflow.studio.cases.asset.model.MediaResource;

public interface IMediaResourceCase {
    Optional<MediaResource> findCover(String mediaKey);
    MediaResource resultFixture();
    MediaResource prepareResult(String candidateReference);
    MediaResource input(UUID ownerId, UUID assetId);
}
