package com.frameflow.studio.trigger.http.template;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.frameflow.studio.api.template.dto.CategoryListResponseDTO;
import com.frameflow.studio.api.template.dto.CategoryListResponseDTO.CategoryDTO;
import com.frameflow.studio.api.template.dto.TemplateListResponseDTO;
import com.frameflow.studio.api.template.dto.TemplateListResponseDTO.TemplateSummaryDTO;
import com.frameflow.studio.api.template.dto.TemplateResponseDTO;
import com.frameflow.studio.api.template.dto.TemplateResponseDTO.RequirementsDTO;
import com.frameflow.studio.cases.template.ITemplateCatalogCase;
import com.frameflow.studio.trigger.http.error.JsonErrors;

@RestController
@RequestMapping("/api/v1/templates")
public class TemplateController {
    private final ITemplateCatalogCase catalog;
    private final JsonErrors errors;

    public TemplateController(ITemplateCatalogCase catalog, JsonErrors errors) {
        this.catalog = catalog;
        this.errors = errors;
    }

    @GetMapping("/categories")
    public ResponseEntity<?> categories(HttpServletRequest request) {
        if (!request.getParameterMap().isEmpty()) return invalid(request);
        return ResponseEntity.ok(new CategoryListResponseDTO(catalog.categories().stream()
                .map(category -> new CategoryDTO(category.categoryId(), category.name())).toList()));
    }

    @GetMapping
    public ResponseEntity<?> list(HttpServletRequest request) {
        if (request.getParameterMap().keySet().stream().anyMatch(key -> !key.equals("categoryId"))) {
            return invalid(request);
        }
        String[] values = request.getParameterValues("categoryId");
        if (values != null && (values.length != 1 || !validId(values[0]))) return invalid(request);
        return ResponseEntity.ok(new TemplateListResponseDTO(catalog.list(values == null ? null : values[0]).stream()
                .map(template -> new TemplateSummaryDTO(template.templateId(), template.title(), template.categoryId(),
                        "/api/v1/demo-media/" + template.coverKey())).toList()));
    }

    @GetMapping("/{templateId}")
    public ResponseEntity<?> detail(@PathVariable String templateId, HttpServletRequest request) {
        if (!validId(templateId) || !request.getParameterMap().isEmpty()) return invalid(request);
        var template = catalog.findById(templateId);
        if (template.isEmpty()) {
            return ResponseEntity.status(404).contentType(MediaType.APPLICATION_JSON)
                    .body(errors.body(request, "RESOURCE_NOT_FOUND", "资源不存在"));
        }
        var view = template.orElseThrow();
        return ResponseEntity.ok(new TemplateResponseDTO(view.templateId(), view.title(), view.categoryId(),
                "/api/v1/demo-media/" + view.coverKey(), view.description(),
                new RequirementsDTO(view.requiredImageCount(), view.acceptedMimeTypes(), view.maxFileSizeBytes())));
    }

    private boolean validId(String id) {
        return id.matches("[a-z0-9][a-z0-9-]{0,63}");
    }

    private ResponseEntity<?> invalid(HttpServletRequest request) {
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON)
                .body(errors.body(request, "INVALID_REQUEST", "请求参数不合法"));
    }
}
