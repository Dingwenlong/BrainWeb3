package com.brainweb3.backend.access;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "access_requests")
public class AccessRequestEntity {

  @Id
  @Column(length = 40, nullable = false)
  private String id;

  @Column(name = "dataset_id", nullable = false, length = 40)
  private String datasetId;

  @Column(name = "actor_id", nullable = false, length = 80)
  private String actorId;

  @Column(name = "actor_role", nullable = false, length = 40)
  private String actorRole;

  @Column(name = "actor_org", nullable = false, length = 160)
  private String actorOrg;

  @Column(nullable = false, length = 80)
  private String purpose;

  @Column(name = "requested_duration_hours", nullable = false)
  private int requestedDurationHours;

  @Column(nullable = false, length = 1000)
  private String reason;

  @Column(nullable = false, length = 40)
  private String status;

  @Column(name = "policy_note", length = 255)
  private String policyNote;

  @Column(name = "approved_duration_hours")
  private Integer approvedDurationHours;

  @Column(name = "approver_id", length = 80)
  private String approverId;

  @Column(name = "approver_role", length = 40)
  private String approverRole;

  @Column(name = "approver_org", length = 160)
  private String approverOrg;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "decided_at")
  private Instant decidedAt;

  @Column(name = "expires_at")
  private Instant expiresAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getDatasetId() {
    return datasetId;
  }

  public void setDatasetId(String datasetId) {
    this.datasetId = datasetId;
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

  public String getPurpose() {
    return purpose;
  }

  public void setPurpose(String purpose) {
    this.purpose = purpose;
  }

  public int getRequestedDurationHours() {
    return requestedDurationHours;
  }

  public void setRequestedDurationHours(int requestedDurationHours) {
    this.requestedDurationHours = requestedDurationHours;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getPolicyNote() {
    return policyNote;
  }

  public void setPolicyNote(String policyNote) {
    this.policyNote = policyNote;
  }

  public Integer getApprovedDurationHours() {
    return approvedDurationHours;
  }

  public void setApprovedDurationHours(Integer approvedDurationHours) {
    this.approvedDurationHours = approvedDurationHours;
  }

  public String getApproverId() {
    return approverId;
  }

  public void setApproverId(String approverId) {
    this.approverId = approverId;
  }

  public String getApproverRole() {
    return approverRole;
  }

  public void setApproverRole(String approverRole) {
    this.approverRole = approverRole;
  }

  public String getApproverOrg() {
    return approverOrg;
  }

  public void setApproverOrg(String approverOrg) {
    this.approverOrg = approverOrg;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public Instant getDecidedAt() {
    return decidedAt;
  }

  public void setDecidedAt(Instant decidedAt) {
    this.decidedAt = decidedAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }
}
