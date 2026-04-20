package com.brainweb3.backend.dataset.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "data_asset_proofs")
public class DataAssetProofEntity {

  @Id
  @Column(name = "dataset_id", length = 40, nullable = false)
  private String datasetId;

  @MapsId
  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "dataset_id")
  private DatasetEntity dataset;

  @Column(name = "chain_provider", length = 40)
  private String chainProvider;

  @Column(name = "chain_group", length = 40)
  private String chainGroup;

  @Column(name = "contract_name", length = 120)
  private String contractName;

  @Column(name = "contract_address", length = 255)
  private String contractAddress;

  @Column(name = "sm3_hash", length = 255)
  private String sm3Hash;

  @Column(name = "ipfs_cid", length = 255)
  private String ipfsCid;

  @Column(name = "off_chain_reference", length = 1000)
  private String offChainReference;

  @Column(name = "chain_tx_hash", length = 255)
  private String chainTxHash;

  @Column(name = "did_holder", length = 255)
  private String didHolder;

  @Column(name = "access_policy", length = 255)
  private String accessPolicy;

  @Column(name = "audit_state", length = 80)
  private String auditState;

  public String getDatasetId() {
    return datasetId;
  }

  public void setDatasetId(String datasetId) {
    this.datasetId = datasetId;
  }

  public DatasetEntity getDataset() {
    return dataset;
  }

  public void setDataset(DatasetEntity dataset) {
    this.dataset = dataset;
    this.datasetId = dataset == null ? null : dataset.getId();
  }

  public String getChainProvider() {
    return chainProvider;
  }

  public void setChainProvider(String chainProvider) {
    this.chainProvider = chainProvider;
  }

  public String getChainGroup() {
    return chainGroup;
  }

  public void setChainGroup(String chainGroup) {
    this.chainGroup = chainGroup;
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

  public String getSm3Hash() {
    return sm3Hash;
  }

  public void setSm3Hash(String sm3Hash) {
    this.sm3Hash = sm3Hash;
  }

  public String getIpfsCid() {
    return ipfsCid;
  }

  public void setIpfsCid(String ipfsCid) {
    this.ipfsCid = ipfsCid;
  }

  public String getOffChainReference() {
    return offChainReference;
  }

  public void setOffChainReference(String offChainReference) {
    this.offChainReference = offChainReference;
  }

  public String getChainTxHash() {
    return chainTxHash;
  }

  public void setChainTxHash(String chainTxHash) {
    this.chainTxHash = chainTxHash;
  }

  public String getDidHolder() {
    return didHolder;
  }

  public void setDidHolder(String didHolder) {
    this.didHolder = didHolder;
  }

  public String getAccessPolicy() {
    return accessPolicy;
  }

  public void setAccessPolicy(String accessPolicy) {
    this.accessPolicy = accessPolicy;
  }

  public String getAuditState() {
    return auditState;
  }

  public void setAuditState(String auditState) {
    this.auditState = auditState;
  }
}
