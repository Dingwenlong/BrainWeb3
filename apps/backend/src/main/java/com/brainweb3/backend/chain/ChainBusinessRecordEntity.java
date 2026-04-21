package com.brainweb3.backend.chain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "chain_business_records")
public class ChainBusinessRecordEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "dataset_id", nullable = false, length = 40)
  private String datasetId;

  @Column(name = "event_type", nullable = false, length = 80)
  private String eventType;

  @Column(name = "reference_id", nullable = false, length = 80)
  private String referenceId;

  @Column(name = "business_status", nullable = false, length = 40)
  private String businessStatus;

  @Column(name = "anchor_status", nullable = false, length = 40)
  private String anchorStatus;

  @Column(name = "actor_id", nullable = false, length = 80)
  private String actorId;

  @Column(name = "actor_role", nullable = false, length = 40)
  private String actorRole;

  @Column(name = "actor_org", nullable = false, length = 120)
  private String actorOrg;

  @Column(name = "chain_provider", length = 40)
  private String chainProvider;

  @Column(name = "chain_group", length = 40)
  private String chainGroup;

  @Column(name = "contract_name", length = 120)
  private String contractName;

  @Column(name = "contract_address", length = 255)
  private String contractAddress;

  @Column(name = "event_hash", length = 255)
  private String eventHash;

  @Column(name = "chain_tx_hash", length = 255)
  private String chainTxHash;

  @Column(name = "detail", length = 1000)
  private String detail;

  @Column(name = "anchor_error", length = 1000)
  private String anchorError;

  @Column(name = "anchored_at", nullable = false)
  private Instant anchoredAt;

  public Long getId() {
    return id;
  }

  public String getDatasetId() {
    return datasetId;
  }

  public void setDatasetId(String datasetId) {
    this.datasetId = datasetId;
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public String getReferenceId() {
    return referenceId;
  }

  public void setReferenceId(String referenceId) {
    this.referenceId = referenceId;
  }

  public String getBusinessStatus() {
    return businessStatus;
  }

  public void setBusinessStatus(String businessStatus) {
    this.businessStatus = businessStatus;
  }

  public String getAnchorStatus() {
    return anchorStatus;
  }

  public void setAnchorStatus(String anchorStatus) {
    this.anchorStatus = anchorStatus;
  }

  public String getActorId() {
    return actorId;
  }

  public void setActorId(String actorId) {
    this.actorId = actorId;
  }

  public String getActorRole() {
    return actorRole;
  }

  public void setActorRole(String actorRole) {
    this.actorRole = actorRole;
  }

  public String getActorOrg() {
    return actorOrg;
  }

  public void setActorOrg(String actorOrg) {
    this.actorOrg = actorOrg;
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

  public String getEventHash() {
    return eventHash;
  }

  public void setEventHash(String eventHash) {
    this.eventHash = eventHash;
  }

  public String getChainTxHash() {
    return chainTxHash;
  }

  public void setChainTxHash(String chainTxHash) {
    this.chainTxHash = chainTxHash;
  }

  public String getDetail() {
    return detail;
  }

  public void setDetail(String detail) {
    this.detail = detail;
  }

  public String getAnchorError() {
    return anchorError;
  }

  public void setAnchorError(String anchorError) {
    this.anchorError = anchorError;
  }

  public Instant getAnchoredAt() {
    return anchoredAt;
  }

  public void setAnchoredAt(Instant anchoredAt) {
    this.anchoredAt = anchoredAt;
  }
}
