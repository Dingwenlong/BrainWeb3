package com.brainweb3.backend.training;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "model_records")
public class ModelRecordEntity {

  @Id
  @Column(length = 40, nullable = false)
  private String id;

  @Column(name = "training_job_id", length = 40, nullable = false, unique = true)
  private String trainingJobId;

  @Column(name = "dataset_id", length = 40, nullable = false)
  private String datasetId;

  @Column(name = "dataset_title", length = 200, nullable = false)
  private String datasetTitle;

  @Column(name = "actor_id", length = 80, nullable = false)
  private String actorId;

  @Column(name = "actor_role", length = 40, nullable = false)
  private String actorRole;

  @Column(name = "actor_org", length = 160, nullable = false)
  private String actorOrg;

  @Column(length = 40, nullable = false)
  private String orchestrator;

  @Column(length = 80, nullable = false)
  private String algorithm;

  @Column(name = "model_name", length = 120, nullable = false)
  private String modelName;

  @Column(length = 255, nullable = false)
  private String objective;

  @Column(name = "governance_status", length = 40, nullable = false)
  private String governanceStatus;

  @Column(name = "governance_note", length = 255)
  private String governanceNote;

  @Column(name = "artifact_ref", length = 120, nullable = false)
  private String artifactRef;

  @Column(name = "metric_summary", length = 255)
  private String metricSummary;

  @Column(name = "result_summary", length = 1000)
  private String resultSummary;

  @Column(name = "last_governed_by", length = 80)
  private String lastGovernedBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "governed_at")
  private Instant governedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getTrainingJobId() {
    return trainingJobId;
  }

  public void setTrainingJobId(String trainingJobId) {
    this.trainingJobId = trainingJobId;
  }

  public String getDatasetId() {
    return datasetId;
  }

  public void setDatasetId(String datasetId) {
    this.datasetId = datasetId;
  }

  public String getDatasetTitle() {
    return datasetTitle;
  }

  public void setDatasetTitle(String datasetTitle) {
    this.datasetTitle = datasetTitle;
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

  public String getOrchestrator() {
    return orchestrator;
  }

  public void setOrchestrator(String orchestrator) {
    this.orchestrator = orchestrator;
  }

  public String getAlgorithm() {
    return algorithm;
  }

  public void setAlgorithm(String algorithm) {
    this.algorithm = algorithm;
  }

  public String getModelName() {
    return modelName;
  }

  public void setModelName(String modelName) {
    this.modelName = modelName;
  }

  public String getObjective() {
    return objective;
  }

  public void setObjective(String objective) {
    this.objective = objective;
  }

  public String getGovernanceStatus() {
    return governanceStatus;
  }

  public void setGovernanceStatus(String governanceStatus) {
    this.governanceStatus = governanceStatus;
  }

  public String getGovernanceNote() {
    return governanceNote;
  }

  public void setGovernanceNote(String governanceNote) {
    this.governanceNote = governanceNote;
  }

  public String getArtifactRef() {
    return artifactRef;
  }

  public void setArtifactRef(String artifactRef) {
    this.artifactRef = artifactRef;
  }

  public String getMetricSummary() {
    return metricSummary;
  }

  public void setMetricSummary(String metricSummary) {
    this.metricSummary = metricSummary;
  }

  public String getResultSummary() {
    return resultSummary;
  }

  public void setResultSummary(String resultSummary) {
    this.resultSummary = resultSummary;
  }

  public String getLastGovernedBy() {
    return lastGovernedBy;
  }

  public void setLastGovernedBy(String lastGovernedBy) {
    this.lastGovernedBy = lastGovernedBy;
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

  public Instant getGovernedAt() {
    return governedAt;
  }

  public void setGovernedAt(Instant governedAt) {
    this.governedAt = governedAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(Instant completedAt) {
    this.completedAt = completedAt;
  }
}
