package com.frameflow.studio.domain.template.model.valobj;

public record Category(String categoryId, String name) {
    public Category {
        if (categoryId == null || !categoryId.matches("[a-z0-9][a-z0-9-]{0,63}")
                || name == null || name.isBlank()) {
            throw new IllegalArgumentException("Invalid template category");
        }
    }
}
