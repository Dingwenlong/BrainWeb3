package com.brainweb3.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties({
    EegServiceProperties.class,
    ChainProperties.class,
    StorageProperties.class
})
public class HttpClientConfiguration {

  @Bean
  RestTemplate eegRestTemplate(RestTemplateBuilder builder, EegServiceProperties properties) {
    return builder.rootUri(properties.getBaseUrl()).build();
  }
}
