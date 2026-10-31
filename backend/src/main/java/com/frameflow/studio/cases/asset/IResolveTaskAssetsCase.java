package com.frameflow.studio.cases.asset;

import java.util.List;
import java.util.UUID;
import com.frameflow.studio.cases.asset.model.AssetDescriptor;

public interface IResolveTaskAssetsCase {
    List<AssetDescriptor> resolve(UUID ownerId, List<UUID> assetIds);
}
