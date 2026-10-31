package com.frameflow.studio.domain.asset.adapter.port;

import java.io.InputStream;
import java.util.UUID;
import com.frameflow.studio.domain.asset.model.valobj.StoredImage;

public interface IAssetStoragePort {
    StoredImage prepare(UUID assetId, InputStream input);
    void publish(String contentKey);
    byte[] read(String contentKey);
    // Failure compensation only; there is no user-facing delete capability.
    void discard(String contentKey);
}
