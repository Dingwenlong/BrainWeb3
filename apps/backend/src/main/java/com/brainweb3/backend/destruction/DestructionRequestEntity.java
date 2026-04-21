package com.brainweb3.backend.destruction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "destruction_requests")
public class DestructionRequestEntity {

  @Id
  @Column(length = 40, nullable = false)
  private String id;

  @Column(name = "dataset_id", length = 40, nullable = false)
  private String datasetId;

  @Column(name = "requester_id", length = 80, nullable = false)
  private String requesterId;

  @Column(name = "requester_role", length = 40, nullable = false)
  private String requesterRole;

  @Column(name = "requester_org", length = 120, nullable = false)
  private String requesterOrg;

  @Column(length = 1000, nullable = false)
  private String reason;

  @Column(length = 40, nullable = false)
  private String status;

  @Column(name = "policy_note", length = 255)
  private String policyNote;

  @Column(name = "approver_id", length = 80)
  private String approverId;

  @Column(name = "approver_role", length = 40)
  private String approverRole;

  @Column(name = "approver_org", length = 120)
  private String approverOrg;

  @Column(name = "executed_by", length = 80)
  private String executedBy;

  @Column(name = "cleanup_status", length = 40, nullable = false)
  private String cleanupStatus;

  @Column(name = "cleanup_error", length = 1000)
  private String cleanupError;

  @Column(name = "cleanup_evidence_ref", length = 255)
  private String cleanupEvidenceRef;

  @Column(name = "cleanup_evidence_hash", length = 128)
  private String cleanupEvidenceHash;

  @Column(name = "cleanup_verified_by", length = 80)
  private String cleanupVerifiedBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "decided_at")
  private Instant decidedAt;

  @Column(name = "executed_at")
  private Instant executedAt;

  @Column(name = "cleanup_completed_at")
  private Instant cleanupCompletedAt;

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

  public String getRequesterId() {
    return requesterId;
  }

  public void setRequesterId(String requesterId) {
    this.requesterId = requesterId;
  }

  public String getRequesterRole() {
    return requesterRole;
  }

  public void setRequesterRole(String requesterRole) {
    this.requesterRole = requesterRole;
  }

  public String getRequesterOrg() {
    return requesterOrg;
  }

  public void setRequesterOrg(String requesterOrg) {
    this.requesterOrg = requesterOrg;
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

  public String getExecutedBy() {
    return executedBy;
  }

  public void setExecutedBy(String executedBy) {
    this.executedBy = executedBy;
  }

  public String getCleanupStatus() {
    return cleanupStatus;
  }

  public void setCleanupStatus(String cleanupStatus) {
    this.cleanupStatus = cleanupStatus;
  }

  public String getCleanupError() {
    return cleanupError;
  }

  public void setCleanupError(String cleanupError) {
    this.cleanupError = cleanupError;
  }

  public String getCleanupEvidenceRef() {
    return cleanupEvidenceRef;
  }

  public void setCleanupEvidenceRef(String cleanupEvidenceRef) {
    this.cleanupEvidenceRef = cleanupEvidenceRef;
  }

  public String getCleanupEvidenceHash() {
    return cleanupEvidenceHash;
  }

  public void setCleanupEvidenceHash(String cleanupEvidenceHash) {
    this.cleanupEvidenceHash = cleanupEvidenceHash;
  }

  public String getCleanupVerifiedBy() {
    return cleanupVerifiedBy;
  }

  public void setCleanupVerifiedBy(String cleanupVerifiedBy) {
    this.cleanupVerifiedBy = cleanupVerifiedBy;
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

  public Instant getExecutedAt() {
    return executedAt;
  }

  public void setExecutedAt(Instant executedAt) {
    this.executedAt = executedAt;
  }

  public Instant getCleanupCompletedAt() {
    return cleanupCompletedAt;
  }

  public void setCleanupCompletedAt(Instant cleanupCompletedAt) {
    this.cleanupCompletedAt = cleanupCompletedAt;
  }
}
