package com.frameflow.studio.infrastructure.dao.po.asset;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UploadedAssetPO {
    private String assetId;
    private String ownerId;
    private String contentKey;
    private String mediaType;
    private long sizeBytes;
    private LocalDateTime createdAt;
}
