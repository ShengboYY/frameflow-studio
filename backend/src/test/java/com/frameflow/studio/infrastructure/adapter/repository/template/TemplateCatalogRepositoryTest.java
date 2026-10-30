package com.frameflow.studio.infrastructure.adapter.repository.template;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.frameflow.studio.domain.template.adapter.repository.ITemplateCatalogRepository;
import com.frameflow.studio.infrastructure.config.CatalogConfiguration;

import static org.assertj.core.api.Assertions.*;

class TemplateCatalogRepositoryTest {
    private final ObjectMapper mapper = JsonMapper.builder().build();
    private final Set<String> covers = Set.of("portrait-cover", "postcard-cover");

    @Test
    void actualBundledCatalogLoadsWithoutDatabaseAndCannotBeModified() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ObjectMapper.class, () -> mapper);
            context.register(CatalogConfiguration.class);
            context.refresh();
            var repository = context.getBean(ITemplateCatalogRepository.class);
            assertThat(repository.findAll()).extracting("templateId").containsExactly("portrait-card", "travel-postcard");
            assertThatThrownBy(() -> repository.findAll().clear()).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Test
    void duplicateIdsInvalidRulesAndUnknownCoversFailBeanCreation() throws Exception {
        String original = new ClassPathResource("templates/catalog.json").getContentAsString(StandardCharsets.UTF_8);
        for (String invalid : new String[]{"[]", "[{}]", "[", original.replace("travel-postcard", "portrait-card"),
                original.replace("\"requiredImageCount\": 1", "\"requiredImageCount\": 0"),
                original.replace("\"requiredImageCount\": 1", "\"requiredImageCount\": 1.5"),
                original.replace("\"requiredImageCount\": 1", "\"requiredImageCount\": \"1\""),
                original.replace("\"requiredImageCount\": 1", "\"requiredImageCount\": null"),
                original.replace("5242880", "8388609"), original.replace("image/jpeg", "image/gif"),
                original.replace("portrait-cover", "private-file"),
                original.replace("\"categoryId\": \"travel\"", "\"categoryId\": \"creative\"")}) {
            try (var context = new AnnotationConfigApplicationContext()) {
                context.registerBean(ITemplateCatalogRepository.class, () -> load(invalid));
                assertThatThrownBy(context::refresh).isInstanceOf(org.springframework.beans.factory.BeanCreationException.class)
                        .hasStackTraceContaining("Cannot load packaged template catalog");
                assertThat(context.isActive()).isFalse();
            }
        }
    }

    @Test
    void unreadableSeedFileFailsClearly() {
        assertThatThrownBy(() -> new TemplateCatalogRepository(mapper, new ClassPathResource("missing-catalog.json"), covers))
                .isInstanceOf(IllegalStateException.class).hasMessage("Cannot load packaged template catalog");
    }

    private TemplateCatalogRepository load(String json) {
        return new TemplateCatalogRepository(mapper, new ByteArrayResource(json.getBytes(StandardCharsets.UTF_8)), covers);
    }
}
