package com.frameflow.studio.infrastructure.dao.task;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.frameflow.studio.infrastructure.dao.po.task.ProductionTaskPO;

@Mapper
public interface IProductionTaskDao {
    int insert(ProductionTaskPO task);
    ProductionTaskPO findById(@Param("taskId") String taskId);
    ProductionTaskPO findOwned(@Param("taskId") String taskId, @Param("ownerId") String ownerId);
    List<ProductionTaskPO> listOwned(@Param("ownerId") String ownerId);
    int updateConclusion(@Param("task") ProductionTaskPO task, @Param("expectedRevision") long expectedRevision,
                         @Param("expectedStatus") String expectedStatus, @Param("expectedStage") String expectedStage);
}
