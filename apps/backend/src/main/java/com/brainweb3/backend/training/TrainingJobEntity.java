package com.brainweb3.backend.training;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "training_jobs")
public class TrainingJobEntity {

  @Id
  @Column(length = 40, nullable = false)
  private String id;

  @Column(name = "dataset_id", nullable = false, length = 40)
  private String datasetId;

  @Column(name = "dataset_title", nullable = false, length = 200)
  private String datasetTitle;

  @Column(name = "actor_id", nullable = false, length = 80)
  private String actorId;

  @Column(name = "actor_role", nullable = false, length = 40)
  private String actorRole;

  @Column(name = "actor_org", nullable = false, length = 160)
  private String actorOrg;

  @Column(nullable = false, length = 40)
  private String orchestrator;

  @Column(nullable = false, length = 80)
  private String algorithm;

  @Column(name = "model_name", nullable = false, length = 120)
  private String modelName;

  @Column(nullable = false, length = 255)
  private String objective;

  @Column(name = "requested_rounds", nullable = false)
  private int requestedRounds;

  @Column(name = "completed_rounds", nullable = false)
  private int completedRounds;

  @Column(nullable = false, length = 40)
  private String status;

  @Column(name = "external_job_ref", length = 80)
  private String externalJobRef;

  @Column(name = "latest_message", length = 255)
  private String latestMessage;

  @Column(name = "metric_summary", length = 255)
  private String metricSummary;

  @Column(name = "result_summary", length = 1000)
  private String resultSummary;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

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

  public int getRequestedRounds() {
    return requestedRounds;
  }

  public void setRequestedRounds(int requestedRounds) {
    this.requestedRounds = requestedRounds;
  }

  public int getCompletedRounds() {
    return completedRounds;
  }

  public void setCompletedRounds(int completedRounds) {
    this.completedRounds = completedRounds;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getExternalJobRef() {
    return externalJobRef;
  }

  public void setExternalJobRef(String externalJobRef) {
    this.externalJobRef = externalJobRef;
  }

  public String getLatestMessage() {
    return latestMessage;
  }

  public void setLatestMessage(String latestMessage) {
    this.latestMessage = latestMessage;
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

  public Instant getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(Instant startedAt) {
    this.startedAt = startedAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(Instant completedAt) {
    this.completedAt = completedAt;
  }
}
