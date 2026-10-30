package com.frameflow.studio.cases.template.impl;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import com.frameflow.studio.cases.template.ITemplateCatalogCase;
import com.frameflow.studio.cases.template.model.CategoryView;
import com.frameflow.studio.cases.template.model.TemplateValidation;
import com.frameflow.studio.cases.template.model.TemplateValidation.ImageInput;
import com.frameflow.studio.cases.template.model.TemplateView;
import com.frameflow.studio.domain.template.adapter.repository.ITemplateCatalogRepository;
import com.frameflow.studio.domain.template.model.entity.Template;
import com.frameflow.studio.domain.template.model.valobj.AssetRequirements;
import com.frameflow.studio.domain.template.model.valobj.AssetRequirements.ImageDescriptor;

@Service
public class TemplateCatalogCaseImpl implements ITemplateCatalogCase {
    private final ITemplateCatalogRepository repository;

    public TemplateCatalogCaseImpl(ITemplateCatalogRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<CategoryView> categories() {
        return repository.findAll().stream().map(Template::category).distinct()
                .map(category -> new CategoryView(category.categoryId(), category.name())).toList();
    }

    @Override
    public List<TemplateView> list(String categoryId) {
        return repository.findAll().stream()
                .filter(template -> categoryId == null || template.category().categoryId().equals(categoryId))
                .map(TemplateView::from).toList();
    }

    @Override
    public Optional<TemplateView> findById(String templateId) {
        return find(templateId).map(TemplateView::from);
    }

    @Override
    public Optional<TemplateValidation> validateImage(String templateId, ImageInput image) {
        return find(templateId).map(template -> validation(template,
                template.requirements().validateImage(new ImageDescriptor(image.mediaType(), image.sizeBytes()))));
    }

    @Override
    public Optional<TemplateValidation> validateSubmission(String templateId, List<ImageInput> images) {
        return find(templateId).map(template -> validation(template, template.requirements().validateSubmission(
                images.stream().map(image -> new ImageDescriptor(image.mediaType(), image.sizeBytes())).toList())));
    }

    private Optional<Template> find(String templateId) {
        return repository.findAll().stream().filter(template -> template.templateId().equals(templateId)).findFirst();
    }

    private TemplateValidation validation(Template template, List<AssetRequirements.Violation> violations) {
        return new TemplateValidation(TemplateView.from(template), violations.stream()
                .map(violation -> TemplateValidation.Violation.valueOf(violation.name())).toList());
    }
}
