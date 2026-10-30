package com.frameflow.studio.api.template.dto;

import java.util.List;

public record TemplateResponseDTO(String templateId, String title, String categoryId, String coverUrl,
                                   String description, RequirementsDTO requirements) {
    public record RequirementsDTO(int requiredImageCount, List<String> acceptedMimeTypes, long maxFileSizeBytes) {}
}
