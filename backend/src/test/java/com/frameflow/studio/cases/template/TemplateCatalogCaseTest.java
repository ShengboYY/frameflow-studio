package com.frameflow.studio.cases.template;

import java.util.List;
import org.junit.jupiter.api.Test;
import com.frameflow.studio.cases.template.impl.TemplateCatalogCaseImpl;
import com.frameflow.studio.cases.template.model.TemplateValidation.ImageInput;
import com.frameflow.studio.domain.template.model.entity.Template;
import com.frameflow.studio.domain.template.model.valobj.AssetRequirements;
import com.frameflow.studio.domain.template.model.valobj.Category;

import static com.frameflow.studio.cases.template.model.TemplateValidation.Violation.*;
import static org.assertj.core.api.Assertions.*;

class TemplateCatalogCaseTest {
    private final Template first = new Template("first", "First", new Category("creative", "Creative"),
            "Description", "cover", new AssetRequirements(1, List.of("image/png"), 5));
    private final Template second = new Template("second", "Second", new Category("travel", "Travel"),
            "Description", "cover", new AssetRequirements(2, List.of("image/png", "image/jpeg"), 8));
    private final ITemplateCatalogCase catalog = new TemplateCatalogCaseImpl(() -> List.of(second, first));

    @Test
    void queriesPreserveSeedOrderFilterAndReturnMissingWithoutFallback() {
        assertThat(catalog.categories()).extracting("categoryId").containsExactly("travel", "creative");
        assertThat(catalog.list(null)).extracting("templateId").containsExactly("second", "first");
        assertThat(catalog.list("creative")).extracting("templateId").containsExactly("first");
        assertThat(catalog.list("absent")).isEmpty();
        assertThat(catalog.findById("absent")).isEmpty();
        assertThat(catalog.findById("first").orElseThrow().maxFileSizeBytes()).isEqualTo(5);
        var shared = new TemplateCatalogCaseImpl(() -> List.of(first, first));
        assertThat(shared.categories()).hasSize(1);
    }

    @Test
    void callersReceiveTemplateRulesAndDomainViolationsFromTheSameTemplate() {
        var png = new ImageInput("image/png", 8);
        var single = catalog.validateImage("second", png).orElseThrow();
        assertThat(single.violations()).isEmpty();
        assertThat(single.template().requiredImageCount()).isEqualTo(2);
        assertThat(catalog.validateSubmission("second", List.of(png)).orElseThrow().violations())
                .containsExactly(IMAGE_COUNT);
        assertThat(catalog.validateSubmission("second", List.of(png, png)).orElseThrow().violations()).isEmpty();
        assertThat(catalog.validateImage("first", new ImageInput("image/jpeg", 6)).orElseThrow().violations())
                .containsExactly(IMAGE_TYPE, IMAGE_SIZE);
        assertThat(catalog.validateImage("absent", png)).isEmpty();
        assertThat(catalog.validateSubmission("absent", List.of(png))).isEmpty();
    }
}
