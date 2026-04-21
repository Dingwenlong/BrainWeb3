package com.brainweb3.backend.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "identity_credential_states")
public class IdentityCredentialStatusEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "subject_type", nullable = false, length = 40)
  private String subjectType;

  @Column(name = "subject_key", nullable = false, length = 160)
  private String subjectKey;

  @Column(name = "credential_status", nullable = false, length = 40)
  private String credentialStatus;

  @Column(name = "reason", length = 255)
  private String reason;

  @Column(name = "updated_by", nullable = false, length = 80)
  private String updatedBy;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
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

  public String getCredentialStatus() {
    return credentialStatus;
  }

  public void setCredentialStatus(String credentialStatus) {
    this.credentialStatus = credentialStatus;
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

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
