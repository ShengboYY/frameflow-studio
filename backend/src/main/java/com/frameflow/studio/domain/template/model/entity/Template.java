package com.frameflow.studio.domain.template.model.entity;

import java.util.Objects;
import com.frameflow.studio.domain.template.model.valobj.AssetRequirements;
import com.frameflow.studio.domain.template.model.valobj.Category;

public record Template(String templateId, String title, Category category, String description,
                       String coverKey, AssetRequirements requirements) {
    public Template {
        if (templateId == null || !templateId.matches("[a-z0-9][a-z0-9-]{0,63}")
                || coverKey == null || !coverKey.matches("[a-z0-9][a-z0-9-]{0,63}")
                || title == null || title.isBlank() || description == null || description.isBlank()) {
            throw new IllegalArgumentException("Invalid template");
        }
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(requirements, "requirements");
    }
}
