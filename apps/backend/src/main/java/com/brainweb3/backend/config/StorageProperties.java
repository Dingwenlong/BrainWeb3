package com.brainweb3.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "brainweb3.storage")
public class StorageProperties {

  private String provider = "local";
  private String localRoot = System.getProperty("java.io.tmpdir") + "/brainweb3-storage";
  private String minioEndpoint = "http://localhost:9000";
  private String minioAccessKey = "brainweb3";
  private String minioSecretKey = "brainweb3secret";
  private String minioBucket = "brainweb3-datasets";
  private String minioRegion = "";
  private boolean minioAutoCreateBucket = true;

  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  public String getLocalRoot() {
    return localRoot;
  }

  public void setLocalRoot(String localRoot) {
    this.localRoot = localRoot;
  }

  public String getMinioEndpoint() {
    return minioEndpoint;
  }

  public void setMinioEndpoint(String minioEndpoint) {
    this.minioEndpoint = minioEndpoint;
  }

  public String getMinioAccessKey() {
    return minioAccessKey;
  }

  public void setMinioAccessKey(String minioAccessKey) {
    this.minioAccessKey = minioAccessKey;
  }

  public String getMinioSecretKey() {
    return minioSecretKey;
  }

  public void setMinioSecretKey(String minioSecretKey) {
    this.minioSecretKey = minioSecretKey;
  }

  public String getMinioBucket() {
    return minioBucket;
  }

  public void setMinioBucket(String minioBucket) {
    this.minioBucket = minioBucket;
  }

  public String getMinioRegion() {
    return minioRegion;
  }

  public void setMinioRegion(String minioRegion) {
    this.minioRegion = minioRegion;
  }

  public boolean isMinioAutoCreateBucket() {
    return minioAutoCreateBucket;
  }

  public void setMinioAutoCreateBucket(boolean minioAutoCreateBucket) {
    this.minioAutoCreateBucket = minioAutoCreateBucket;
  }
}
