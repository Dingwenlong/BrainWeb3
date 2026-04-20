package com.brainweb3.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "brainweb3.services.eeg")
public class EegServiceProperties {

  private String baseUrl = "http://localhost:8101";

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }
}
