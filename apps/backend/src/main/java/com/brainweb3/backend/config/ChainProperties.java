package com.brainweb3.backend.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "brainweb3.chain")
public class ChainProperties {

  private String provider = "fisco-bcos-3";
  private boolean enabled = false;
  private String group = "group0";
  private String contractName = "DataNotary";
  private String contractAddress = "";
  private boolean autoDeploy = true;
  private String businessContractName = "BusinessEventAnchor";
  private String businessContractAddress = "";
  private boolean businessContractAutoDeploy = false;
  private String businessContractAbiPath = "classpath:fisco/contracts/DataNotary.abi";
  private String businessContractBinPath = "classpath:fisco/contracts/DataNotary.bin";
  private List<String> requiredEventTypes = List.of(
      "ACCESS_APPROVED",
      "ACCESS_REVOKED",
      "TRAINING_COMPLETED",
      "TRAINING_FAILED"
  );
  private List<String> optionalEventTypes = List.of(
      "DESTRUCTION_STORAGE_PURGED",
      "MODEL_REGISTERED",
      "MODEL_GOVERNED"
  );
  private List<String> disabledEventTypes = List.of();
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

  public String getBusinessContractName() {
    return businessContractName;
  }

  public void setBusinessContractName(String businessContractName) {
    this.businessContractName = businessContractName;
  }

  public String getBusinessContractAddress() {
    return businessContractAddress;
  }

  public void setBusinessContractAddress(String businessContractAddress) {
    this.businessContractAddress = businessContractAddress;
  }

  public boolean isBusinessContractAutoDeploy() {
    return businessContractAutoDeploy;
  }

  public void setBusinessContractAutoDeploy(boolean businessContractAutoDeploy) {
    this.businessContractAutoDeploy = businessContractAutoDeploy;
  }

  public String getBusinessContractAbiPath() {
    return businessContractAbiPath;
  }

  public void setBusinessContractAbiPath(String businessContractAbiPath) {
    this.businessContractAbiPath = businessContractAbiPath;
  }

  public String getBusinessContractBinPath() {
    return businessContractBinPath;
  }

  public void setBusinessContractBinPath(String businessContractBinPath) {
    this.businessContractBinPath = businessContractBinPath;
  }

  public String getConfigPath() {
    return configPath;
  }

  public void setConfigPath(String configPath) {
    this.configPath = configPath;
  }

  public List<String> getRequiredEventTypes() {
    return requiredEventTypes;
  }

  public void setRequiredEventTypes(List<String> requiredEventTypes) {
    this.requiredEventTypes = requiredEventTypes;
  }

  public List<String> getOptionalEventTypes() {
    return optionalEventTypes;
  }

  public void setOptionalEventTypes(List<String> optionalEventTypes) {
    this.optionalEventTypes = optionalEventTypes;
  }

  public List<String> getDisabledEventTypes() {
    return disabledEventTypes;
  }

  public void setDisabledEventTypes(List<String> disabledEventTypes) {
    this.disabledEventTypes = disabledEventTypes;
  }
}
