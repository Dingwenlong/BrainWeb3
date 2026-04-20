package com.brainweb3.backend.config;

import com.brainweb3.backend.chain.ChainGateway;
import com.brainweb3.backend.chain.FiscoBcosChainGateway;
import com.brainweb3.backend.chain.MockChainGateway;
import com.brainweb3.backend.storage.LocalStorageGateway;
import com.brainweb3.backend.storage.MinioStorageGateway;
import com.brainweb3.backend.storage.StorageGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

@Configuration
public class GatewayConfiguration {

  @Bean
  ChainGateway chainGateway(ChainProperties chainProperties, ResourceLoader resourceLoader) {
    if (chainProperties.isEnabled()
        && "fisco-bcos-3".equalsIgnoreCase(chainProperties.getProvider())) {
      return new FiscoBcosChainGateway(chainProperties, resourceLoader);
    }
    return new MockChainGateway();
  }

  @Bean
  StorageGateway storageGateway(StorageProperties storageProperties) {
    if ("local".equalsIgnoreCase(storageProperties.getProvider())) {
      return new LocalStorageGateway(storageProperties);
    }
    if ("minio".equalsIgnoreCase(storageProperties.getProvider())) {
      return new MinioStorageGateway(storageProperties);
    }
    throw new IllegalStateException(
        "Unsupported storage provider: %s".formatted(storageProperties.getProvider())
    );
  }
}
