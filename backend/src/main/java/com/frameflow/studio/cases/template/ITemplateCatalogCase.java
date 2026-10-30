package com.frameflow.studio.cases.template;

import java.util.List;
import java.util.Optional;
import com.frameflow.studio.cases.template.model.CategoryView;
import com.frameflow.studio.cases.template.model.TemplateValidation;
import com.frameflow.studio.cases.template.model.TemplateValidation.ImageInput;
import com.frameflow.studio.cases.template.model.TemplateView;

public interface ITemplateCatalogCase {
    List<CategoryView> categories();
    List<TemplateView> list(String categoryId);
    Optional<TemplateView> findById(String templateId);
    Optional<TemplateValidation> validateImage(String templateId, ImageInput image);
    Optional<TemplateValidation> validateSubmission(String templateId, List<ImageInput> images);
}
