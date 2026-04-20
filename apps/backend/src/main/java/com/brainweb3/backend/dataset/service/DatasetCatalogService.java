package com.brainweb3.backend.dataset.service;

import com.brainweb3.backend.access.AccessRequestRepository;
import com.brainweb3.backend.audit.AuditEventRepository;
import com.brainweb3.backend.chain.ChainGateway;
import com.brainweb3.backend.chain.ChainRegistrationCommand;
import com.brainweb3.backend.chain.ChainRegistrationReceipt;
import com.brainweb3.backend.dataset.api.DataAssetProofResponse;
import com.brainweb3.backend.dataset.api.DatasetDetailResponse;
import com.brainweb3.backend.dataset.api.DatasetSummaryResponse;
import com.brainweb3.backend.dataset.persistence.DataAssetProofEntity;
import com.brainweb3.backend.dataset.persistence.DatasetEntity;
import com.brainweb3.backend.dataset.persistence.DatasetRepository;
import com.brainweb3.backend.dataset.persistence.UploadAuditEntity;
import com.brainweb3.backend.dataset.persistence.UploadAuditRepository;
import com.brainweb3.backend.storage.StorageGateway;
import com.brainweb3.backend.storage.StoragePersistCommand;
import com.brainweb3.backend.storage.StoragePersistReceipt;
import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

@Service
public class DatasetCatalogService {

  private static final Pattern DATASET_ID_PATTERN = Pattern.compile("^ds-(\\d+)$");
  private static final String DEFAULT_SEED_SAMPLE_PATH = "physionet/S001/S001R04.edf";

  private final ChainGateway chainGateway;
  private final StorageGateway storageGateway;
  private final EegMetadataGateway eegMetadataGateway;
  private final DatasetRepository datasetRepository;
  private final UploadAuditRepository uploadAuditRepository;
  private final AccessRequestRepository accessRequestRepository;
  private final AuditEventRepository auditEventRepository;
  private final Path demoSampleRoot;

  public DatasetCatalogService(
      ChainGateway chainGateway,
      StorageGateway storageGateway,
      EegMetadataGateway eegMetadataGateway,
      DatasetRepository datasetRepository,
      UploadAuditRepository uploadAuditRepository,
      AccessRequestRepository accessRequestRepository,
      AuditEventRepository auditEventRepository,
      @Value("${brainweb3.demo.sample-root:.brainweb3-samples}") String demoSampleRoot
  ) {
    this.chainGateway = chainGateway;
    this.storageGateway = storageGateway;
    this.eegMetadataGateway = eegMetadataGateway;
    this.datasetRepository = datasetRepository;
    this.uploadAuditRepository = uploadAuditRepository;
    this.accessRequestRepository = accessRequestRepository;
    this.auditEventRepository = auditEventRepository;
    this.demoSampleRoot = Path.of(demoSampleRoot);
  }

  @PostConstruct
  void initializeCatalog() {
    if (datasetRepository.count() == 0) {
      seedCatalog();
    }
  }

  @Transactional
  public void resetCatalog() {
    auditEventRepository.deleteAll();
    accessRequestRepository.deleteAll();
    uploadAuditRepository.deleteAll();
    datasetRepository.deleteAll();
    seedCatalog();
  }

  @Transactional(readOnly = true)
  public List<DatasetSummaryResponse> listDatasets() {
    return datasetRepository.findAllByOrderByUpdatedAtDesc().stream()
        .map(this::toSummary)
        .toList();
  }

  @Transactional(readOnly = true)
  public Optional<DatasetDetailResponse> getDataset(String datasetId) {
    return datasetRepository.findById(datasetId)
        .map(this::toDetail);
  }

  @Transactional(readOnly = true)
  public Optional<String> getDatasetActivitySourceUri(String datasetId) {
    return datasetRepository.findById(datasetId)
        .map(dataset -> {
          if (dataset.getStorageUri() != null && !dataset.getStorageUri().isBlank()) {
            return dataset.getStorageUri();
          }

          DataAssetProofEntity proof = dataset.getProof();
          if (proof != null && proof.getOffChainReference() != null && !proof.getOffChainReference().isBlank()) {
            return proof.getOffChainReference();
          }
          return "";
        });
  }

  @Transactional
  public DatasetUploadResult uploadDataset(
      MultipartFile file,
      String subjectCode,
      String title,
      String description,
      String ownerOrganization,
      String tagsCsv
  ) {
    validateUpload(file, subjectCode, title, ownerOrganization);

    String normalizedFormat = extractFormat(file.getOriginalFilename());
    byte[] fileBytes = readFileBytes(file);
    int samplingRate = deriveSamplingRate(normalizedFormat);
    int channelCount = deriveChannelCount(normalizedFormat);
    int sampleCount = deriveSampleCount(fileBytes.length, channelCount);
    double durationSeconds = sampleCount / (double) samplingRate;
    String proofFingerprint = createFingerprint(fileBytes, subjectCode, title);
    String datasetId = nextDatasetId();
    Instant uploadedAt = Instant.now();
    List<String> normalizedTags = parseTags(tagsCsv, normalizedFormat);
    String traceId = "upload-%s".formatted(proofFingerprint.substring(0, 12));

    DatasetEntity uploadedDataset = createPendingDataset(
        datasetId,
        subjectCode,
        title,
        description,
        ownerOrganization,
        file,
        normalizedFormat,
        channelCount,
        sampleCount,
        durationSeconds,
        samplingRate,
        normalizedTags,
        uploadedAt
    );
    uploadedDataset = datasetRepository.save(uploadedDataset);
    recordAudit(uploadedDataset, "UPLOAD_ACCEPTED", "accepted", "EEG upload accepted for processing.", traceId);

    try {
      StoragePersistReceipt storageReceipt = persistFile(
          datasetId,
          file.getOriginalFilename(),
          normalizedFormat,
          subjectCode,
          fileBytes
      );
      applyStorageReceipt(uploadedDataset, storageReceipt, uploadedAt);
      recordAudit(uploadedDataset, "STORAGE_PERSISTED", "success", storageReceipt.storageUri(), traceId);
      applyResolvedMetadata(uploadedDataset, storageReceipt.storageUri(), traceId);

      ChainRegistrationReceipt chainReceipt = chainGateway.registerDataAsset(
          new ChainRegistrationCommand(
              datasetId,
              subjectCode.trim(),
              title.trim(),
              ownerOrganization.trim(),
              normalizedFormat,
              proofFingerprint,
              storageReceipt.offChainReference(),
              normalizedTags,
              uploadedAt
          )
      );
      applyChainReceipt(uploadedDataset, chainReceipt, uploadedAt);
      recordAudit(uploadedDataset, "CHAIN_REGISTERED", "success", chainReceipt.chainTxHash(), traceId);
    } catch (RuntimeException exception) {
      uploadedDataset.setUploadStatus("failed");
      uploadedDataset.setProofStatus("failed");
      uploadedDataset.setTrainingReadiness("blocked");
      uploadedDataset.setUpdatedAt(Instant.now());
      DataAssetProofEntity proof = uploadedDataset.getProof();
      if (proof != null && (proof.getAuditState() == null || proof.getAuditState().isBlank())) {
        proof.setAuditState("upload-failed");
      }
      recordAudit(uploadedDataset, "UPLOAD_FAILED", "failed", exception.getMessage(), traceId);
      throw exception;
    }

    return new DatasetUploadResult(
        toDetail(uploadedDataset),
        traceId
    );
  }

  private StoragePersistReceipt persistFile(
      String datasetId,
      String originalFilename,
      String format,
      String subjectCode,
      byte[] fileBytes
  ) {
    try {
      return storageGateway.persist(
          new StoragePersistCommand(datasetId, originalFilename, format, subjectCode.trim(), fileBytes)
      );
    } catch (RuntimeException exception) {
      throw new ResponseStatusException(
          INTERNAL_SERVER_ERROR,
          "Failed to persist uploaded EEG file.",
          exception
      );
    }
  }

  private void validateUpload(
      MultipartFile file,
      String subjectCode,
      String title,
      String ownerOrganization
  ) {
    if (file == null || file.isEmpty()) {
      throw new ResponseStatusException(BAD_REQUEST, "Please upload a non-empty EEG file.");
    }
    if (subjectCode == null || subjectCode.isBlank()) {
      throw new ResponseStatusException(BAD_REQUEST, "subjectCode is required.");
    }
    if (title == null || title.isBlank()) {
      throw new ResponseStatusException(BAD_REQUEST, "title is required.");
    }
    if (ownerOrganization == null || ownerOrganization.isBlank()) {
      throw new ResponseStatusException(BAD_REQUEST, "ownerOrganization is required.");
    }

    String format = extractFormat(file.getOriginalFilename());
    if (!List.of("EDF", "GDF", "BDF").contains(format)) {
      throw new ResponseStatusException(BAD_REQUEST, "Only EDF, GDF, and BDF files are supported.");
    }
  }

  private String extractFormat(String filename) {
    if (filename == null || !filename.contains(".")) {
      return "";
    }

    return filename.substring(filename.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT);
  }

  private byte[] readFileBytes(MultipartFile file) {
    try {
      return file.getBytes();
    } catch (Exception exception) {
      throw new ResponseStatusException(
          INTERNAL_SERVER_ERROR,
          "Failed to read uploaded file bytes.",
          exception
      );
    }
  }

  private int deriveSamplingRate(String format) {
    return switch (format) {
      case "GDF" -> 128;
      case "BDF" -> 256;
      default -> 160;
    };
  }

  private int deriveChannelCount(String format) {
    return switch (format) {
      case "GDF" -> 32;
      case "BDF" -> 128;
      default -> 64;
    };
  }

  private int deriveSampleCount(int byteLength, int channelCount) {
    int estimated = Math.max(4096, byteLength / Math.max(channelCount / 4, 1));
    return Math.min(estimated, 86400);
  }

  private List<String> parseTags(String tagsCsv, String normalizedFormat) {
    if (tagsCsv == null || tagsCsv.isBlank()) {
      return List.of("uploaded", normalizedFormat.toLowerCase(Locale.ROOT), "pending-review");
    }

    return Arrays.stream(tagsCsv.split(","))
        .map(String::trim)
        .filter(tag -> !tag.isBlank())
        .distinct()
        .toList();
  }

  private String createFingerprint(byte[] fileBytes, String subjectCode, String title) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(fileBytes);
      digest.update(subjectCode.getBytes(StandardCharsets.UTF_8));
      digest.update(title.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException exception) {
      throw new ResponseStatusException(
          INTERNAL_SERVER_ERROR,
          "Unable to generate upload fingerprint.",
          exception
      );
    }
  }

  private void seedCatalog() {
    String ds101SampleSource = resolveDemoSampleSource(DEFAULT_SEED_SAMPLE_PATH);
    int ds101SamplingRate = 160;
    int ds101ChannelCount = 64;
    int ds101SampleCount = 19200;
    double ds101DurationSeconds = 300.0;

    if (!"file://bootstrap/ds-101/physionet_s001.edf".equals(ds101SampleSource)) {
      Optional<EegDatasetMetadataResponse> ds101Metadata = eegMetadataGateway.getMetadata(ds101SampleSource);
      if (ds101Metadata.isPresent()) {
        EegDatasetMetadataResponse metadata = ds101Metadata.get();
        ds101SamplingRate = metadata.samplingRate();
        ds101ChannelCount = metadata.channelCount();
        ds101SampleCount = metadata.sampleCount();
        ds101DurationSeconds = metadata.durationSeconds();
      }
    }

    datasetRepository.saveAll(List.of(
        seedDataset(
            "ds-101",
            "PMMI-S001",
            "Motor Imagery Session A",
            "PhysioNet 64 导样本，经预处理后用于运动想象分类与脑区活跃度可视化。",
            "physionet_s001.edf",
            14_221_312L,
            "Huaxi Medical Union",
            "EDF",
            "stored",
            "notarized",
            "authorized-ready",
            ds101ChannelCount,
            ds101SampleCount,
            ds101DurationSeconds,
            ds101SamplingRate,
            List.of("motor-imagery", "alpha-band", "demo"),
            "bootstrap",
            "seed/ds-101/physionet_s001.edf",
            ds101SampleSource,
            "bootstrap",
            "group0",
            "BootstrapDataNotary",
            "bootstrap://contracts/data-notary",
            "SM3:9e0f8b1e4c74d6e19b5ab2d5f801c993",
            "bafybeif6d4brainweb3demo101",
            ds101SampleSource,
            "0x7ad2ef71bc101",
            "did:brainweb3:org-huaxi",
            "owner-approved research-only",
            "indexed",
            Instant.parse("2026-04-20T01:30:00Z")
        ),
        seedDataset(
            "ds-205",
            "PMMI-S014",
            "Resting State Screening",
            "静息态 EEG 演示数据，适合作为审计追踪和授权回放样例。",
            "resting_state_014.gdf",
            7_842_144L,
            "West China Research Lab",
            "GDF",
            "encrypted",
            "hash-pending",
            "review-required",
            32,
            9600,
            180.0,
            128,
            List.of("resting-state", "theta-band", "governance"),
            "bootstrap",
            "seed/ds-205/resting_state_014.gdf",
            "file://bootstrap/ds-205/resting_state_014.gdf",
            "bootstrap",
            "group0",
            "BootstrapDataNotary",
            "bootstrap://contracts/data-notary",
            "SM3:c6da1c4f97b2f5d52d8fa2e4a5012048",
            "bafybeibwbrainweb3demo205",
            "file://bootstrap/ds-205/resting_state_014.gdf",
            "0x0000000000000000",
            "did:brainweb3:org-westchina",
            "approval-in-flight",
            "awaiting-chain-write",
            Instant.parse("2026-04-19T09:10:00Z")
        )
    ));
  }

  private DatasetSummaryResponse toSummary(DatasetEntity dataset) {
    return new DatasetSummaryResponse(
        dataset.getId(),
        dataset.getSubjectCode(),
        dataset.getTitle(),
        dataset.getOwnerOrganization(),
        dataset.getFormat(),
        dataset.getUploadStatus(),
        dataset.getProofStatus(),
        dataset.getTrainingReadiness(),
        dataset.getUpdatedAt()
    );
  }

  public DatasetDetailResponse toDetail(DatasetEntity dataset) {
    DataAssetProofEntity proof = dataset.getProof();
    return new DatasetDetailResponse(
        dataset.getId(),
        dataset.getSubjectCode(),
        dataset.getTitle(),
        dataset.getDescription(),
        dataset.getOriginalFilename(),
        dataset.getFileSizeBytes(),
        dataset.getOwnerOrganization(),
        dataset.getFormat(),
        dataset.getUploadStatus(),
        dataset.getProofStatus(),
        dataset.getTrainingReadiness(),
        dataset.getChannelCount(),
        dataset.getSampleCount(),
        dataset.getDurationSeconds(),
        dataset.getSamplingRate(),
        List.copyOf(dataset.getTags()),
        new DataAssetProofResponse(
            valueOrEmpty(proof == null ? null : proof.getChainProvider()),
            valueOrEmpty(proof == null ? null : proof.getChainGroup()),
            valueOrEmpty(proof == null ? null : proof.getContractName()),
            valueOrEmpty(proof == null ? null : proof.getContractAddress()),
            valueOrEmpty(proof == null ? null : proof.getSm3Hash()),
            valueOrEmpty(proof == null ? null : proof.getIpfsCid()),
            valueOrEmpty(proof == null ? null : proof.getOffChainReference()),
            valueOrEmpty(proof == null ? null : proof.getChainTxHash()),
            valueOrEmpty(proof == null ? null : proof.getDidHolder()),
            valueOrEmpty(proof == null ? null : proof.getAccessPolicy()),
            valueOrEmpty(proof == null ? null : proof.getAuditState())
        ),
        dataset.getUpdatedAt()
    );
  }

  private DatasetEntity createPendingDataset(
      String datasetId,
      String subjectCode,
      String title,
      String description,
      String ownerOrganization,
      MultipartFile file,
      String normalizedFormat,
      int channelCount,
      int sampleCount,
      double durationSeconds,
      int samplingRate,
      List<String> normalizedTags,
      Instant uploadedAt
  ) {
    DatasetEntity dataset = new DatasetEntity();
    dataset.setId(datasetId);
    dataset.setSubjectCode(subjectCode.trim());
    dataset.setTitle(title.trim());
    dataset.setDescription(description == null || description.isBlank()
        ? "新上传 EEG 数据资产，等待进入预处理与授权链路。"
        : description.trim());
    dataset.setOriginalFilename(file.getOriginalFilename());
    dataset.setFileSizeBytes(file.getSize());
    dataset.setOwnerOrganization(ownerOrganization.trim());
    dataset.setFormat(normalizedFormat);
    dataset.setUploadStatus("received");
    dataset.setProofStatus("pending-storage");
    dataset.setTrainingReadiness("review-required");
    dataset.setChannelCount(channelCount);
    dataset.setSampleCount(sampleCount);
    dataset.setDurationSeconds(durationSeconds);
    dataset.setSamplingRate(samplingRate);
    dataset.setTags(normalizedTags);
    dataset.setCreatedAt(uploadedAt);
    dataset.setUpdatedAt(uploadedAt);

    DataAssetProofEntity proof = new DataAssetProofEntity();
    proof.setAuditState("pending-proof");
    dataset.setProof(proof);
    return dataset;
  }

  private void applyStorageReceipt(
      DatasetEntity dataset,
      StoragePersistReceipt storageReceipt,
      Instant updatedAt
  ) {
    dataset.setStorageProvider(storageReceipt.provider());
    dataset.setStorageKey(storageReceipt.storageKey());
    dataset.setStorageUri(storageReceipt.storageUri());
    dataset.setUploadStatus("uploaded");
    dataset.setProofStatus("storage-persisted");
    dataset.setUpdatedAt(updatedAt);

    DataAssetProofEntity proof = dataset.getProof();
    proof.setOffChainReference(storageReceipt.offChainReference());
    proof.setAuditState("storage-persisted");
  }

  private void applyChainReceipt(
      DatasetEntity dataset,
      ChainRegistrationReceipt chainReceipt,
      Instant updatedAt
  ) {
    dataset.setUploadStatus("uploaded");
    dataset.setProofStatus("notarized");
    dataset.setTrainingReadiness("review-required");
    dataset.setUpdatedAt(updatedAt);

    DataAssetProofEntity proof = dataset.getProof();
    proof.setChainProvider(chainReceipt.chainProvider());
    proof.setChainGroup(chainReceipt.chainGroup());
    proof.setContractName(chainReceipt.contractName());
    proof.setContractAddress(chainReceipt.contractAddress());
    proof.setSm3Hash(chainReceipt.sm3Hash());
    proof.setIpfsCid(chainReceipt.ipfsCid());
    proof.setOffChainReference(chainReceipt.offChainReference());
    proof.setChainTxHash(chainReceipt.chainTxHash());
    proof.setDidHolder(chainReceipt.didHolder());
    proof.setAccessPolicy(chainReceipt.accessPolicy());
    proof.setAuditState(chainReceipt.auditState());
  }

  private void recordAudit(
      DatasetEntity dataset,
      String action,
      String status,
      String message,
      String traceId
  ) {
    UploadAuditEntity audit = new UploadAuditEntity();
    audit.setDataset(dataset);
    audit.setAction(action);
    audit.setStatus(status);
    audit.setMessage(message);
    audit.setTraceId(traceId);
    audit.setCreatedAt(Instant.now());
    uploadAuditRepository.save(audit);
  }

  private void applyResolvedMetadata(
      DatasetEntity dataset,
      String sourceUri,
      String traceId
  ) {
    eegMetadataGateway.getMetadata(sourceUri).ifPresentOrElse(metadata -> {
      dataset.setFormat(metadata.format());
      dataset.setSamplingRate(metadata.samplingRate());
      dataset.setChannelCount(metadata.channelCount());
      dataset.setSampleCount(metadata.sampleCount());
      dataset.setDurationSeconds(metadata.durationSeconds());
      dataset.setUpdatedAt(Instant.now());
      recordAudit(
          dataset,
          "EEG_METADATA_PARSED",
          "success",
          "Resolved metadata via EEG service: %d channels @ %dHz."
              .formatted(metadata.channelCount(), metadata.samplingRate()),
          traceId
      );
    }, () -> recordAudit(
        dataset,
        "EEG_METADATA_PARSED",
        "fallback",
        "Metadata service unavailable, retained derived upload estimates.",
        traceId
    ));
  }

  private DatasetEntity seedDataset(
      String datasetId,
      String subjectCode,
      String title,
      String description,
      String originalFilename,
      long fileSizeBytes,
      String ownerOrganization,
      String format,
      String uploadStatus,
      String proofStatus,
      String trainingReadiness,
      int channelCount,
      int sampleCount,
      double durationSeconds,
      int samplingRate,
      List<String> tags,
      String storageProvider,
      String storageKey,
      String storageUri,
      String chainProvider,
      String chainGroup,
      String contractName,
      String contractAddress,
      String sm3Hash,
      String ipfsCid,
      String offChainReference,
      String chainTxHash,
      String didHolder,
      String accessPolicy,
      String auditState,
      Instant updatedAt
  ) {
    DatasetEntity dataset = new DatasetEntity();
    dataset.setId(datasetId);
    dataset.setSubjectCode(subjectCode);
    dataset.setTitle(title);
    dataset.setDescription(description);
    dataset.setOriginalFilename(originalFilename);
    dataset.setFileSizeBytes(fileSizeBytes);
    dataset.setOwnerOrganization(ownerOrganization);
    dataset.setFormat(format);
    dataset.setUploadStatus(uploadStatus);
    dataset.setProofStatus(proofStatus);
    dataset.setTrainingReadiness(trainingReadiness);
    dataset.setChannelCount(channelCount);
    dataset.setSampleCount(sampleCount);
    dataset.setDurationSeconds(durationSeconds);
    dataset.setSamplingRate(samplingRate);
    dataset.setTags(tags);
    dataset.setStorageProvider(storageProvider);
    dataset.setStorageKey(storageKey);
    dataset.setStorageUri(storageUri);
    dataset.setCreatedAt(updatedAt);
    dataset.setUpdatedAt(updatedAt);

    DataAssetProofEntity proof = new DataAssetProofEntity();
    proof.setChainProvider(chainProvider);
    proof.setChainGroup(chainGroup);
    proof.setContractName(contractName);
    proof.setContractAddress(contractAddress);
    proof.setSm3Hash(sm3Hash);
    proof.setIpfsCid(ipfsCid);
    proof.setOffChainReference(offChainReference);
    proof.setChainTxHash(chainTxHash);
    proof.setDidHolder(didHolder);
    proof.setAccessPolicy(accessPolicy);
    proof.setAuditState(auditState);
    dataset.setProof(proof);
    return dataset;
  }

  private String nextDatasetId() {
    int maxKnownId = datasetRepository.findAllIds().stream()
        .mapToInt(this::parseDatasetNumericId)
        .max()
        .orElse(0);
    return "ds-%d".formatted(Math.max(300, maxKnownId) + 1);
  }

  private int parseDatasetNumericId(String datasetId) {
    Matcher matcher = DATASET_ID_PATTERN.matcher(datasetId == null ? "" : datasetId.trim());
    if (!matcher.matches()) {
      return 0;
    }
    return Integer.parseInt(matcher.group(1));
  }

  private String valueOrEmpty(String value) {
    return value == null ? "" : value;
  }

  private String resolveDemoSampleSource(String relativePath) {
    Path candidate = demoSampleRoot.resolve(relativePath).normalize().toAbsolutePath();
    if (Files.exists(candidate)) {
      return candidate.toString();
    }
    return "file://bootstrap/ds-101/physionet_s001.edf";
  }
}
