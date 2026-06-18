package com.brainweb3.backend.training;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "evaluation_runs")
public class EvaluationRunEntity {

  @Id
  @Column(length = 40, nullable = false)
  private String id;

  @Column(name = "model_record_id", length = 40, nullable = false)
  private String modelRecordId;

  @Column(name = "dataset_id", length = 40, nullable = false)
  private String datasetId;

  @Column(name = "evaluator_actor_id", length = 80, nullable = false)
  private String evaluatorActorId;

  @Column(name = "evaluator_role", length = 40, nullable = false)
  private String evaluatorRole;

  @Column(name = "evaluator_org", length = 160, nullable = false)
  private String evaluatorOrg;

  @Column(name = "test_set_hash", length = 128, nullable = false)
  private String testSetHash;

  @Column(name = "eval_script_hash", length = 128, nullable = false)
  private String evalScriptHash;

  @Column(name = "metrics_json", length = 2000, nullable = false)
  private String metricsJson;

  @Column(name = "result_hash", length = 80, nullable = false)
  private String resultHash;

  @Column(name = "verification_status", length = 40, nullable = false)
  private String verificationStatus;

  @Column(length = 1000)
  private String notes;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getModelRecordId() {
    return modelRecordId;
  }

  public void setModelRecordId(String modelRecordId) {
    this.modelRecordId = modelRecordId;
  }

  public String getDatasetId() {
    return datasetId;
  }

  public void setDatasetId(String datasetId) {
    this.datasetId = datasetId;
  }

  public String getEvaluatorActorId() {
    return evaluatorActorId;
  }

  public void setEvaluatorActorId(String evaluatorActorId) {
    this.evaluatorActorId = evaluatorActorId;
  }

  public String getEvaluatorRole() {
    return evaluatorRole;
  }

  public void setEvaluatorRole(String evaluatorRole) {
    this.evaluatorRole = evaluatorRole;
  }

  public String getEvaluatorOrg() {
    return evaluatorOrg;
  }

  public void setEvaluatorOrg(String evaluatorOrg) {
    this.evaluatorOrg = evaluatorOrg;
  }

  public String getTestSetHash() {
    return testSetHash;
  }

  public void setTestSetHash(String testSetHash) {
    this.testSetHash = testSetHash;
  }

  public String getEvalScriptHash() {
    return evalScriptHash;
  }

  public void setEvalScriptHash(String evalScriptHash) {
    this.evalScriptHash = evalScriptHash;
  }

  public String getMetricsJson() {
    return metricsJson;
  }

  public void setMetricsJson(String metricsJson) {
    this.metricsJson = metricsJson;
  }

  public String getResultHash() {
    return resultHash;
  }

  public void setResultHash(String resultHash) {
    this.resultHash = resultHash;
  }

  public String getVerificationStatus() {
    return verificationStatus;
  }

  public void setVerificationStatus(String verificationStatus) {
    this.verificationStatus = verificationStatus;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
