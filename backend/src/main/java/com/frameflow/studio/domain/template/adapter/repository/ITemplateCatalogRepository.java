package com.frameflow.studio.domain.template.adapter.repository;

import java.util.List;
import com.frameflow.studio.domain.template.model.entity.Template;

public interface ITemplateCatalogRepository {
    List<Template> findAll();
}
