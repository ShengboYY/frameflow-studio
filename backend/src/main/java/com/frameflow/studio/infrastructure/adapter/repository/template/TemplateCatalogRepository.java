package com.frameflow.studio.infrastructure.adapter.repository.template;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import org.springframework.core.io.Resource;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import com.frameflow.studio.domain.template.adapter.repository.ITemplateCatalogRepository;
import com.frameflow.studio.domain.template.model.entity.Template;

public class TemplateCatalogRepository implements ITemplateCatalogRepository {
    private final List<Template> templates;

    public TemplateCatalogRepository(ObjectMapper mapper, Resource source, Set<String> publicCoverKeys) {
        try (var input = source.getInputStream()) {
            var seedMapper = mapper.rebuild().disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                    .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                    .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,
                            DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
            templates = List.of(seedMapper.readValue(input, Template[].class));
            if (templates.isEmpty()) throw new IllegalArgumentException("Empty template catalog");
            var ids = new HashSet<String>();
            var categories = new LinkedHashMap<String, String>();
            for (var template : templates) {
                if (!ids.add(template.templateId())) throw new IllegalArgumentException("Duplicate template ID");
                if (!publicCoverKeys.contains(template.coverKey())) {
                    throw new IllegalArgumentException("Unregistered template cover");
                }
                var category = template.category();
                String existing = categories.putIfAbsent(category.categoryId(), category.name());
                if (existing != null && !existing.equals(category.name())) {
                    throw new IllegalArgumentException("Conflicting category names");
                }
            }
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Cannot load packaged template catalog", exception);
        }
    }

    @Override
    public List<Template> findAll() {
        return templates;
    }
}
