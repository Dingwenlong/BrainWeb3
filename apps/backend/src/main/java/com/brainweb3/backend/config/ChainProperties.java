package com.brainweb3.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "brainweb3.chain")
public class ChainProperties {

  private String provider = "fisco-bcos-3";
  private boolean enabled = false;
  private String group = "group0";
  private String contractName = "DataNotary";
  private String contractAddress = "";
  private boolean autoDeploy = true;
  private String configPath = "classpath:fisco/config-example.toml";

  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getGroup() {
    return group;
  }

  public void setGroup(String group) {
    this.group = group;
  }

  public String getContractName() {
    return contractName;
  }

  public void setContractName(String contractName) {
    this.contractName = contractName;
  }

  public String getContractAddress() {
    return contractAddress;
  }

  public void setContractAddress(String contractAddress) {
    this.contractAddress = contractAddress;
  }

  public boolean isAutoDeploy() {
    return autoDeploy;
  }

  public void setAutoDeploy(boolean autoDeploy) {
    this.autoDeploy = autoDeploy;
  }

  public String getConfigPath() {
    return configPath;
  }

  public void setConfigPath(String configPath) {
    this.configPath = configPath;
  }
}
