package com.frameflow.studio.cases.template.model;

import java.util.List;

public record TemplateValidation(TemplateView template, List<Violation> violations) {
    public TemplateValidation {
        violations = List.copyOf(violations);
    }

    public record ImageInput(String mediaType, long sizeBytes) {}
    public enum Violation { IMAGE_COUNT, IMAGE_TYPE, IMAGE_SIZE }
}
