package com.frameflow.studio.api.template.dto;

import java.util.List;

public record TemplateListResponseDTO(List<TemplateSummaryDTO> items) {
    public record TemplateSummaryDTO(String templateId, String title, String categoryId, String coverUrl) {}
}
