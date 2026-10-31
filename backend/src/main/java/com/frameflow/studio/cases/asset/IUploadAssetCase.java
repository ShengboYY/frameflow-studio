package com.frameflow.studio.cases.asset;

import java.io.InputStream;
import java.util.UUID;
import com.frameflow.studio.cases.asset.model.AssetDescriptor;

public interface IUploadAssetCase {
    AssetDescriptor upload(UUID ownerId, String templateId, InputStream input);
}
