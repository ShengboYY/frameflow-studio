package com.frameflow.studio.api.task.dto;

import java.util.List;

public record TaskSubmissionRequestDTO(String templateId, List<String> assetIds, SimulationDTO simulation) {
    public record SimulationDTO(String failAtStage, Integer stageDelayMs) { }
}
