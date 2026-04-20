package com.brainweb3.backend.dataset.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "datasets")
public class DatasetEntity {

  @Id
  @Column(length = 40, nullable = false)
  private String id;

  @Column(name = "subject_code", nullable = false, length = 80)
  private String subjectCode;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, length = 1000)
  private String description;

  @Column(name = "original_filename", nullable = false, length = 255)
  private String originalFilename;

  @Column(name = "file_size_bytes", nullable = false)
  private long fileSizeBytes;

  @Column(name = "owner_organization", nullable = false, length = 160)
  private String ownerOrganization;

  @Column(nullable = false, length = 16)
  private String format;

  @Column(name = "upload_status", nullable = false, length = 40)
  private String uploadStatus;

  @Column(name = "proof_status", nullable = false, length = 40)
  private String proofStatus;

  @Column(name = "training_readiness", nullable = false, length = 60)
  private String trainingReadiness;

  @Column(name = "channel_count", nullable = false)
  private int channelCount;

  @Column(name = "sample_count", nullable = false)
  private int sampleCount;

  @Column(name = "duration_seconds", nullable = false)
  private double durationSeconds;

  @Column(name = "sampling_rate", nullable = false)
  private int samplingRate;

  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(name = "dataset_tags", joinColumns = @JoinColumn(name = "dataset_id"))
  @OrderColumn(name = "tag_order")
  @Column(name = "tag_value", nullable = false, length = 80)
  private List<String> tags = new ArrayList<>();

  @Column(name = "storage_provider", length = 40)
  private String storageProvider;

  @Column(name = "storage_key", length = 255)
  private String storageKey;

  @Column(name = "storage_uri", length = 1000)
  private String storageUri;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @OneToOne(
      mappedBy = "dataset",
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true
  )
  private DataAssetProofEntity proof;

  @OneToMany(mappedBy = "dataset", fetch = FetchType.LAZY, orphanRemoval = true)
  private List<UploadAuditEntity> uploadAudits = new ArrayList<>();

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getSubjectCode() {
    return subjectCode;
  }

  public void setSubjectCode(String subjectCode) {
    this.subjectCode = subjectCode;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getOriginalFilename() {
    return originalFilename;
  }

  public void setOriginalFilename(String originalFilename) {
    this.originalFilename = originalFilename;
  }

  public long getFileSizeBytes() {
    return fileSizeBytes;
  }

  public void setFileSizeBytes(long fileSizeBytes) {
    this.fileSizeBytes = fileSizeBytes;
  }

  public String getOwnerOrganization() {
    return ownerOrganization;
  }

  public void setOwnerOrganization(String ownerOrganization) {
    this.ownerOrganization = ownerOrganization;
  }

  public String getFormat() {
    return format;
  }

  public void setFormat(String format) {
    this.format = format;
  }

  public String getUploadStatus() {
    return uploadStatus;
  }

  public void setUploadStatus(String uploadStatus) {
    this.uploadStatus = uploadStatus;
  }

  public String getProofStatus() {
    return proofStatus;
  }

  public void setProofStatus(String proofStatus) {
    this.proofStatus = proofStatus;
  }

  public String getTrainingReadiness() {
    return trainingReadiness;
  }

  public void setTrainingReadiness(String trainingReadiness) {
    this.trainingReadiness = trainingReadiness;
  }

  public int getChannelCount() {
    return channelCount;
  }

  public void setChannelCount(int channelCount) {
    this.channelCount = channelCount;
  }

  public int getSampleCount() {
    return sampleCount;
  }

  public void setSampleCount(int sampleCount) {
    this.sampleCount = sampleCount;
  }

  public double getDurationSeconds() {
    return durationSeconds;
  }

  public void setDurationSeconds(double durationSeconds) {
    this.durationSeconds = durationSeconds;
  }

  public int getSamplingRate() {
    return samplingRate;
  }

  public void setSamplingRate(int samplingRate) {
    this.samplingRate = samplingRate;
  }

  public List<String> getTags() {
    return tags;
  }

  public void setTags(List<String> tags) {
    this.tags = new ArrayList<>(tags);
  }

  public String getStorageProvider() {
    return storageProvider;
  }

  public void setStorageProvider(String storageProvider) {
    this.storageProvider = storageProvider;
  }

  public String getStorageKey() {
    return storageKey;
  }

  public void setStorageKey(String storageKey) {
    this.storageKey = storageKey;
  }

  public String getStorageUri() {
    return storageUri;
  }

  public void setStorageUri(String storageUri) {
    this.storageUri = storageUri;
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

  public DataAssetProofEntity getProof() {
    return proof;
  }

  public void setProof(DataAssetProofEntity proof) {
    this.proof = proof;
    if (proof != null) {
      proof.setDataset(this);
    }
  }

  public List<UploadAuditEntity> getUploadAudits() {
    return uploadAudits;
  }
}
