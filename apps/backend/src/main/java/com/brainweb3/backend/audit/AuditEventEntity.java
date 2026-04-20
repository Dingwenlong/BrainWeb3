package com.brainweb3.backend.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "audit_events")
public class AuditEventEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "dataset_id", length = 40)
  private String datasetId;

  @Column(name = "actor_id", nullable = false, length = 80)
  private String actorId;

  @Column(name = "actor_role", nullable = false, length = 40)
  private String actorRole;

  @Column(name = "actor_org", nullable = false, length = 160)
  private String actorOrg;

  @Column(nullable = false, length = 80)
  private String action;

  @Column(nullable = false, length = 40)
  private String status;

  @Column(length = 1000)
  private String detail;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  public Long getId() {
    return id;
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

  public String getAction() {
    return action;
  }

  public void setAction(String action) {
    this.action = action;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getDetail() {
    return detail;
  }

  public void setDetail(String detail) {
    this.detail = detail;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
