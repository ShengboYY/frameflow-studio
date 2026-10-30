package com.frameflow.studio.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;
import com.frameflow.studio.domain.template.adapter.repository.ITemplateCatalogRepository;
import com.frameflow.studio.infrastructure.adapter.port.asset.PackagedDemoMediaPort;
import com.frameflow.studio.infrastructure.adapter.repository.template.TemplateCatalogRepository;

@Configuration(proxyBeanMethods = false)
public class CatalogConfiguration {
    @Bean
    PackagedDemoMediaPort demoMediaPort() {
        return new PackagedDemoMediaPort();
    }

    @Bean
    ITemplateCatalogRepository templateCatalogRepository(ObjectMapper mapper, PackagedDemoMediaPort media) {
        // Validate cross-resource registration at composition time without coupling the two domains.
        return new TemplateCatalogRepository(mapper, new ClassPathResource("templates/catalog.json"),
                media.publicCoverKeys());
    }
}
