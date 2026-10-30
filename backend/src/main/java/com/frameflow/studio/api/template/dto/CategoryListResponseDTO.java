package com.frameflow.studio.api.template.dto;

import java.util.List;

public record CategoryListResponseDTO(List<CategoryDTO> items) {
    public record CategoryDTO(String categoryId, String name) {}
}
