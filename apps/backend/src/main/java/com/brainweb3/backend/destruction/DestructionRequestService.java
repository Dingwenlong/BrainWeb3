package com.brainweb3.backend.destruction;

import com.brainweb3.backend.access.AccessRequestService;
import com.brainweb3.backend.access.ActorContext;
import com.brainweb3.backend.audit.AuditService;
import com.brainweb3.backend.chain.ChainBusinessRecordService;
import com.brainweb3.backend.dataset.persistence.DatasetEntity;
import com.brainweb3.backend.dataset.persistence.DatasetRepository;
import com.brainweb3.backend.storage.StorageDeleteCommand;
import com.brainweb3.backend.storage.StorageGateway;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DestructionRequestService {

  private static final Pattern DESTRUCTION_REQUEST_ID_PATTERN = Pattern.compile("^dr-(\\d+)$");
  private static final String ACTIVE_STATUS = "active";

  private final DestructionRequestRepository destructionRequestRepository;
  private final DatasetRepository datasetRepository;
  private final AccessRequestService accessRequestService;
  private final AuditService auditService;
  private final ChainBusinessRecordService chainBusinessRecordService;
  private final StorageGateway storageGateway;

  public DestructionRequestService(
      DestructionRequestRepository destructionRequestRepository,
      DatasetRepository datasetRepository,
      AccessRequestService accessRequestService,
      AuditService auditService,
      ChainBusinessRecordService chainBusinessRecordService,
      StorageGateway storageGateway
  ) {
    this.destructionRequestRepository = destructionRequestRepository;
    this.datasetRepository = datasetRepository;
    this.accessRequestService = accessRequestService;
    this.auditService = auditService;
    this.chainBusinessRecordService = chainBusinessRecordService;
    this.storageGateway = storageGateway;
  }

  @Transactional(readOnly = true)
  public java.util.List<DestructionRequestResponse> list(
      ActorContext viewer,
      String datasetId,
      String actorId,
      String status
  ) {
    return destructionRequestRepository.findAllByOrderByCreatedAtDesc().stream()
        .filter(request -> isVisibleTo(viewer, request))
        .filter(request -> matchesIgnoreCase(datasetId, request.getDatasetId()))
        .filter(request -> matchesIgnoreCase(actorId, request.getRequesterId()))
        .filter(request -> matchesIgnoreCase(status, request.getStatus()))
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public DestructionRequestResponse create(ActorContext actor, CreateDestructionRequest request) {
    DatasetEntity dataset = requireDataset(request.datasetId());
    ensureRequestAllowed(actor, dataset);
    ensureDatasetCanOpenRequest(dataset);
    ensureNoOpenRequest(dataset.getId());

    Instant now = Instant.now();
    DestructionRequestEntity entity = new DestructionRequestEntity();
    entity.setId(nextRequestId());
    entity.setDatasetId(dataset.getId());
    entity.setRequesterId(actor.actorId());
    entity.setRequesterRole(actor.actorRole());
    entity.setRequesterOrg(actor.actorOrg());
    entity.setReason(request.reason().trim());
    entity.setStatus("pending");
    entity.setCleanupStatus("not-requested");
    entity.setCleanupEvidenceRef(null);
    entity.setCleanupEvidenceHash(null);
    entity.setCleanupVerifiedBy(null);
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);

    dataset.setDestructionStatus("pending-destruction");
    dataset.setUpdatedAt(now);

    destructionRequestRepository.save(entity);
    auditService.record(
        dataset.getId(),
        actor,
        "DESTRUCTION_REQUEST_CREATED",
        "pending",
        "Requested destruction via %s".formatted(entity.getId())
    );
    return toResponse(entity);
  }

  @Transactional
  public DestructionRequestResponse approve(String requestId, ActorContext actor, DestructionDecisionRequest request) {
    DestructionRequestEntity entity = requirePendingRequest(requestId);
    DatasetEntity dataset = requireDataset(entity.getDatasetId());
    ensureApprover(actor, dataset);

    Instant now = Instant.now();
    entity.setStatus("approved");
    entity.setPolicyNote(request.policy().trim());
    entity.setApproverId(actor.actorId());
    entity.setApproverRole(actor.actorRole());
    entity.setApproverOrg(actor.actorOrg());
    entity.setDecidedAt(now);
    entity.setUpdatedAt(now);

    dataset.setDestructionStatus("approved-for-destruction");
    dataset.setUpdatedAt(now);

    auditService.record(
        dataset.getId(),
        actor,
        "DESTRUCTION_REQUEST_APPROVED",
        "approved",
        "%s approved for destruction".formatted(entity.getId())
    );
    return toResponse(entity);
  }

  @Transactional
  public DestructionRequestResponse reject(String requestId, ActorContext actor, DestructionDecisionRequest request) {
    DestructionRequestEntity entity = requirePendingRequest(requestId);
    DatasetEntity dataset = requireDataset(entity.getDatasetId());
    ensureApprover(actor, dataset);

    Instant now = Instant.now();
    entity.setStatus("rejected");
    entity.setPolicyNote(request.policy().trim());
    entity.setApproverId(actor.actorId());
    entity.setApproverRole(actor.actorRole());
    entity.setApproverOrg(actor.actorOrg());
    entity.setDecidedAt(now);
    entity.setUpdatedAt(now);

    dataset.setDestructionStatus(ACTIVE_STATUS);
    dataset.setUpdatedAt(now);

    auditService.record(
        dataset.getId(),
        actor,
        "DESTRUCTION_REQUEST_REJECTED",
        "rejected",
        "%s rejected".formatted(entity.getId())
    );
    return toResponse(entity);
  }

  @Transactional
  public DestructionRequestResponse execute(String requestId, ActorContext actor) {
    DestructionRequestEntity entity = requireApprovedRequest(requestId);
    DatasetEntity dataset = requireDataset(entity.getDatasetId());
    ensureApprover(actor, dataset);

    if ("destroyed".equalsIgnoreCase(entity.getStatus())) {
      return toResponse(entity);
    }

    Instant now = Instant.now();
    entity.setStatus("destroyed");
    entity.setExecutedBy(actor.actorId());
    entity.setCleanupStatus("pending");
    entity.setCleanupError(null);
    entity.setCleanupEvidenceRef(null);
    entity.setCleanupEvidenceHash(null);
    entity.setCleanupVerifiedBy(null);
    entity.setExecutedAt(now);
    entity.setUpdatedAt(now);

    dataset.setDestructionStatus("destroyed");
    dataset.setDestroyedAt(now);
    dataset.setDestroyedBy(actor.actorId());
    dataset.setTrainingReadiness("blocked");
    dataset.setUpdatedAt(now);

    auditService.record(
        dataset.getId(),
        actor,
        "DESTRUCTION_EXECUTED",
        "destroyed",
        "%s executed as logical destruction; storage deletion follow-up may still be required.".formatted(entity.getId())
    );
    chainBusinessRecordService.record(
        dataset.getId(),
        actor,
        "DESTRUCTION_COMPLETED",
        entity.getId(),
        "destroyed",
        "%s executed as logical destruction.".formatted(entity.getId())
    );
    return toResponse(entity);
  }

  @Transactional
  public DestructionRequestResponse purgeStorage(String requestId, ActorContext actor) {
    DestructionRequestEntity entity = requireDestroyedRequest(requestId);
    DatasetEntity dataset = requireDataset(entity.getDatasetId());
    ensureApprover(actor, dataset);

    auditService.record(
        dataset.getId(),
        actor,
        "DESTRUCTION_STORAGE_PURGE_REQUESTED",
        "accepted",
        "%s requested storage purge.".formatted(entity.getId())
    );

    try {
      storageGateway.delete(new StorageDeleteCommand(
          dataset.getId(),
          dataset.getStorageKey(),
          dataset.getStorageProvider()
      ));
      Instant cleanedAt = Instant.now();
      String evidenceRef = buildCleanupEvidenceRef(dataset, cleanedAt);
      String evidenceHash = buildCleanupEvidenceHash(dataset, actor, cleanedAt);
      entity.setCleanupStatus("completed");
      entity.setCleanupError(null);
      entity.setCleanupEvidenceRef(evidenceRef);
      entity.setCleanupEvidenceHash(evidenceHash);
      entity.setCleanupVerifiedBy(actor.actorId());
      entity.setCleanupCompletedAt(cleanedAt);
      entity.setUpdatedAt(entity.getCleanupCompletedAt());
      auditService.record(
          dataset.getId(),
          actor,
          "DESTRUCTION_STORAGE_PURGE_COMPLETED",
          "success",
          "%s storage purge completed with evidence %s.".formatted(entity.getId(), evidenceHash)
      );
      chainBusinessRecordService.record(
          dataset.getId(),
          actor,
          "DESTRUCTION_STORAGE_PURGED",
          entity.getId(),
          "completed",
          "%s storage purge evidence %s".formatted(entity.getId(), evidenceHash)
      );
    } catch (RuntimeException exception) {
      entity.setCleanupStatus("failed");
      entity.setCleanupError(exception.getMessage());
      entity.setCleanupEvidenceRef(null);
      entity.setCleanupEvidenceHash(null);
      entity.setCleanupVerifiedBy(null);
      entity.setUpdatedAt(Instant.now());
      auditService.record(
          dataset.getId(),
          actor,
          "DESTRUCTION_STORAGE_PURGE_FAILED",
          "failed",
          exception.getMessage()
      );
    }

    return toResponse(entity);
  }

  private void ensureRequestAllowed(ActorContext actor, DatasetEntity dataset) {
    if (actor.hasRole("admin")) {
      return;
    }
    if (actor.belongsTo(dataset.getOwnerOrganization())) {
      return;
    }
    if (accessRequestService.canAccessDataset(dataset.getId(), actor)) {
      return;
    }
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Actor is not allowed to request dataset destruction.");
  }

  private void ensureApprover(ActorContext actor, DatasetEntity dataset) {
    if (actor.hasRole("admin")) {
      return;
    }
    boolean privilegedRole = actor.hasRole("owner") || actor.hasRole("approver");
    if (!privilegedRole || !actor.belongsTo(dataset.getOwnerOrganization())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Actor is not allowed to decide destruction requests.");
    }
  }

  private void ensureDatasetCanOpenRequest(DatasetEntity dataset) {
    if ("destroyed".equalsIgnoreCase(dataset.getDestructionStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Dataset has already been destroyed.");
    }
    if ("approved-for-destruction".equalsIgnoreCase(dataset.getDestructionStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Dataset is already approved for destruction.");
    }
  }

  private void ensureNoOpenRequest(String datasetId) {
    boolean hasOpenRequest = destructionRequestRepository.findAllByDatasetIdOrderByCreatedAtDesc(datasetId).stream()
        .anyMatch(request -> "pending".equalsIgnoreCase(request.getStatus()) || "approved".equalsIgnoreCase(request.getStatus()));
    if (hasOpenRequest) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Dataset already has an open destruction request.");
    }
  }

  private DestructionRequestEntity requirePendingRequest(String requestId) {
    DestructionRequestEntity entity = destructionRequestRepository.findById(requestId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Destruction request not found."));
    if (!"pending".equalsIgnoreCase(entity.getStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending destruction requests can be decided.");
    }
    return entity;
  }

  private DestructionRequestEntity requireApprovedRequest(String requestId) {
    DestructionRequestEntity entity = destructionRequestRepository.findById(requestId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Destruction request not found."));
    if ("destroyed".equalsIgnoreCase(entity.getStatus())) {
      return entity;
    }
    if (!"approved".equalsIgnoreCase(entity.getStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Only approved destruction requests can be executed.");
    }
    return entity;
  }

  private DestructionRequestEntity requireDestroyedRequest(String requestId) {
    DestructionRequestEntity entity = destructionRequestRepository.findById(requestId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Destruction request not found."));
    if (!"destroyed".equalsIgnoreCase(entity.getStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Only destroyed requests can trigger storage purge.");
    }
    if ("completed".equalsIgnoreCase(entity.getCleanupStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Storage purge has already completed.");
    }
    return entity;
  }

  private DatasetEntity requireDataset(String datasetId) {
    return datasetRepository.findById(datasetId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dataset not found."));
  }

  private boolean isVisibleTo(ActorContext viewer, DestructionRequestEntity request) {
    if (viewer.hasRole("admin")) {
      return true;
    }
    if (viewer.hasRole("owner") || viewer.hasRole("approver")) {
      return datasetRepository.findById(request.getDatasetId())
          .map(dataset -> viewer.belongsTo(dataset.getOwnerOrganization()))
          .orElse(false);
    }
    return viewer.actorId() != null && viewer.actorId().equalsIgnoreCase(request.getRequesterId());
  }

  private String nextRequestId() {
    int next = destructionRequestRepository.findAllIds().stream()
        .map(this::parseNumericId)
        .max(Comparator.naturalOrder())
        .orElse(0) + 1;
    return "dr-%d".formatted(next);
  }

  private int parseNumericId(String value) {
    Matcher matcher = DESTRUCTION_REQUEST_ID_PATTERN.matcher(value == null ? "" : value.trim());
    if (!matcher.matches()) {
      return 0;
    }
    return Integer.parseInt(matcher.group(1));
  }

  private boolean matchesIgnoreCase(String expected, String actual) {
    return expected == null || expected.isBlank() || (actual != null && expected.equalsIgnoreCase(actual));
  }

  private DestructionRequestResponse toResponse(DestructionRequestEntity entity) {
    DatasetEntity dataset = requireDataset(entity.getDatasetId());
    return new DestructionRequestResponse(
        entity.getId(),
        entity.getDatasetId(),
        dataset.getTitle(),
        dataset.getOwnerOrganization(),
        entity.getRequesterId(),
        entity.getRequesterRole(),
        entity.getRequesterOrg(),
        entity.getReason(),
        entity.getStatus(),
        valueOrEmpty(entity.getPolicyNote()),
        valueOrEmpty(entity.getApproverId()),
        valueOrEmpty(entity.getApproverRole()),
        valueOrEmpty(entity.getApproverOrg()),
        valueOrEmpty(entity.getExecutedBy()),
        valueOrEmpty(entity.getCleanupStatus()),
        valueOrEmpty(entity.getCleanupError()),
        valueOrEmpty(entity.getCleanupEvidenceRef()),
        valueOrEmpty(entity.getCleanupEvidenceHash()),
        valueOrEmpty(entity.getCleanupVerifiedBy()),
        entity.getCreatedAt(),
        entity.getUpdatedAt(),
        entity.getDecidedAt(),
        entity.getExecutedAt(),
        entity.getCleanupCompletedAt()
    );
  }

  private String valueOrEmpty(String value) {
    return value == null ? "" : value;
  }

  private String buildCleanupEvidenceRef(DatasetEntity dataset, Instant cleanedAt) {
    return "purge://%s/%s?dataset=%s&completedAt=%s".formatted(
        valueOrEmpty(dataset.getStorageProvider()),
        valueOrEmpty(dataset.getStorageKey()),
        dataset.getId(),
        cleanedAt
    );
  }

  private String buildCleanupEvidenceHash(DatasetEntity dataset, ActorContext actor, Instant cleanedAt) {
    String payload = "%s|%s|%s|%s|%s".formatted(
        dataset.getId(),
        valueOrEmpty(dataset.getStorageProvider()),
        valueOrEmpty(dataset.getStorageKey()),
        actor.actorId(),
        cleanedAt
    );
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 digest unavailable.", exception);
    }
  }
}
