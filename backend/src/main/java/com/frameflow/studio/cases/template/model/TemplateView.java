package com.frameflow.studio.cases.template.model;

import java.util.List;
import com.frameflow.studio.domain.template.model.entity.Template;

public record TemplateView(String templateId, String title, String categoryId, String description,
                           String coverKey, int requiredImageCount, List<String> acceptedMimeTypes,
                           long maxFileSizeBytes) {
    public TemplateView {
        acceptedMimeTypes = List.copyOf(acceptedMimeTypes);
    }

    public static TemplateView from(Template template) {
        var rules = template.requirements();
        return new TemplateView(template.templateId(), template.title(), template.category().categoryId(),
                template.description(), template.coverKey(), rules.requiredImageCount(),
                rules.acceptedMimeTypes(), rules.maxFileSizeBytes());
    }
}
