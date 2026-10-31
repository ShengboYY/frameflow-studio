package com.frameflow.studio.infrastructure.dao.task;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.frameflow.studio.infrastructure.dao.po.task.TaskInputAssetPO;

@Mapper
public interface ITaskInputAssetDao {
    int insert(TaskInputAssetPO input);
    List<TaskInputAssetPO> findByTaskId(@Param("taskId") String taskId);
}
