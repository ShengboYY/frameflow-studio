package com.frameflow.studio.infrastructure.config;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.frameflow.studio.domain.asset.adapter.port.IAssetStoragePort;
import com.frameflow.studio.infrastructure.adapter.port.asset.LocalAssetStoragePort;

@Configuration(proxyBeanMethods = false)
public class AssetConfiguration {
    @Bean
    IAssetStoragePort assetStorage(@Value("${app.assets.storage-root}") String root) {
        return new LocalAssetStoragePort(Path.of(root));
    }
}
