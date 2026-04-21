package com.brainweb3.backend.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "identity_credential_history")
public class IdentityCredentialHistoryEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "subject_type", nullable = false, length = 40)
  private String subjectType;

  @Column(name = "subject_key", nullable = false, length = 160)
  private String subjectKey;

  @Column(name = "previous_status", length = 40)
  private String previousStatus;

  @Column(name = "next_status", nullable = false, length = 40)
  private String nextStatus;

  @Column(name = "source", nullable = false, length = 40)
  private String source;

  @Column(name = "reason", length = 255)
  private String reason;

  @Column(name = "updated_by", length = 80)
  private String updatedBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  public Long getId() {
    return id;
  }

  public String getSubjectType() {
    return subjectType;
  }

  public void setSubjectType(String subjectType) {
    this.subjectType = subjectType;
  }

  public String getSubjectKey() {
    return subjectKey;
  }

  public void setSubjectKey(String subjectKey) {
    this.subjectKey = subjectKey;
  }

  public String getPreviousStatus() {
    return previousStatus;
  }

  public void setPreviousStatus(String previousStatus) {
    this.previousStatus = previousStatus;
  }

  public String getNextStatus() {
    return nextStatus;
  }

  public void setNextStatus(String nextStatus) {
    this.nextStatus = nextStatus;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public String getUpdatedBy() {
    return updatedBy;
  }

  public void setUpdatedBy(String updatedBy) {
    this.updatedBy = updatedBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
