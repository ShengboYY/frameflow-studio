package com.frameflow.studio.infrastructure.dao.po.task;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductionTaskPO {
    private String taskId;
    private String ownerId;
    private String templateId;
    private String status;
    private String currentStage;
    private long revision;
    private String resultRef;
    private String failureKind;
    private String failureCode;
    private String failureMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
