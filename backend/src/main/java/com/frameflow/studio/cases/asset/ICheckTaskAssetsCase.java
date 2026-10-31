package com.frameflow.studio.cases.asset;

import java.util.List;
import java.util.UUID;

public interface ICheckTaskAssetsCase {
    void check(UUID ownerId, List<UUID> assetIds);
}
