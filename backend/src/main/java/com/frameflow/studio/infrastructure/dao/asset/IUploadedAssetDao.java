package com.frameflow.studio.infrastructure.dao.asset;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.frameflow.studio.infrastructure.dao.po.asset.UploadedAssetPO;

@Mapper
public interface IUploadedAssetDao {
    int insert(UploadedAssetPO asset);
    UploadedAssetPO findOwned(@Param("assetId") String assetId, @Param("ownerId") String ownerId);
}
