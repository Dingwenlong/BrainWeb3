package com.brainweb3.backend.access;

import com.brainweb3.backend.audit.AuditService;
import com.brainweb3.backend.dataset.persistence.DatasetEntity;
import com.brainweb3.backend.dataset.persistence.DatasetRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccessRequestService {

  private static final Pattern ACCESS_REQUEST_ID_PATTERN = Pattern.compile("^ar-(\\d+)$");

  private final AccessRequestRepository accessRequestRepository;
  private final DatasetRepository datasetRepository;
  private final AuditService auditService;

  public AccessRequestService(
      AccessRequestRepository accessRequestRepository,
      DatasetRepository datasetRepository,
      AuditService auditService
  ) {
    this.accessRequestRepository = accessRequestRepository;
    this.datasetRepository = datasetRepository;
    this.auditService = auditService;
  }

  @Transactional
  public AccessRequestResponse create(ActorContext actor, CreateAccessRequest request) {
    DatasetEntity dataset = requireDataset(request.datasetId());

    AccessRequestEntity entity = new AccessRequestEntity();
    entity.setId(nextAccessRequestId());
    entity.setDatasetId(dataset.getId());
    entity.setActorId(actor.actorId());
    entity.setActorRole(actor.actorRole());
    entity.setActorOrg(actor.actorOrg());
    entity.setPurpose(request.purpose().trim());
    entity.setRequestedDurationHours(request.requestedDurationHours());
    entity.setReason(request.reason().trim());
    entity.setStatus("pending");
    entity.setCreatedAt(Instant.now());
    entity.setUpdatedAt(entity.getCreatedAt());

    accessRequestRepository.save(entity);
    auditService.record(
        dataset.getId(),
        actor,
        "ACCESS_REQUEST_CREATED",
        "pending",
        "Requested %d hours for %s".formatted(request.requestedDurationHours(), request.purpose().trim())
    );
    return toResponse(entity);
  }

  @Transactional(readOnly = true)
  public java.util.List<AccessRequestResponse> list(String datasetId, String actorId, String status) {
    return accessRequestRepository.findAllByOrderByCreatedAtDesc().stream()
        .filter(entity -> datasetId == null || datasetId.isBlank() || datasetId.equalsIgnoreCase(entity.getDatasetId()))
        .filter(entity -> actorId == null || actorId.isBlank() || actorId.equalsIgnoreCase(entity.getActorId()))
        .filter(entity -> status == null || status.isBlank() || status.equalsIgnoreCase(entity.getStatus()))
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public AccessRequestResponse approve(String requestId, ActorContext actor, AccessDecisionRequest request) {
    AccessRequestEntity entity = requirePendingRequest(requestId);
    DatasetEntity dataset = requireDataset(entity.getDatasetId());
    ensureApprover(actor, dataset);

    Instant now = Instant.now();
    entity.setStatus("approved");
    entity.setPolicyNote(request.policy().trim());
    entity.setApprovedDurationHours(request.approvedDurationHours());
    entity.setApproverId(actor.actorId());
    entity.setApproverRole(actor.actorRole());
    entity.setApproverOrg(actor.actorOrg());
    entity.setDecidedAt(now);
    entity.setUpdatedAt(now);
    entity.setExpiresAt(now.plusSeconds(request.approvedDurationHours() * 3600L));

    auditService.record(
        entity.getDatasetId(),
        actor,
        "ACCESS_REQUEST_APPROVED",
        "approved",
        "%s approved until %s".formatted(entity.getId(), entity.getExpiresAt())
    );
    return toResponse(entity);
  }

  @Transactional
  public AccessRequestResponse reject(String requestId, ActorContext actor, AccessDecisionRequest request) {
    AccessRequestEntity entity = requirePendingRequest(requestId);
    DatasetEntity dataset = requireDataset(entity.getDatasetId());
    ensureApprover(actor, dataset);

    Instant now = Instant.now();
    entity.setStatus("rejected");
    entity.setPolicyNote(request.policy().trim());
    entity.setApprovedDurationHours(0);
    entity.setApproverId(actor.actorId());
    entity.setApproverRole(actor.actorRole());
    entity.setApproverOrg(actor.actorOrg());
    entity.setDecidedAt(now);
    entity.setUpdatedAt(now);
    entity.setExpiresAt(null);

    auditService.record(
        entity.getDatasetId(),
        actor,
        "ACCESS_REQUEST_REJECTED",
        "rejected",
        "%s rejected".formatted(entity.getId())
    );
    return toResponse(entity);
  }

  @Transactional
  public AccessRequestResponse revoke(String requestId, ActorContext actor) {
    AccessRequestEntity entity = accessRequestRepository.findById(requestId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Access request not found."));
    DatasetEntity dataset = requireDataset(entity.getDatasetId());
    ensureApprover(actor, dataset);
    if ("revoked".equalsIgnoreCase(entity.getStatus())) {
      return toResponse(entity);
    }

    Instant now = Instant.now();
    entity.setStatus("revoked");
    entity.setApproverId(actor.actorId());
    entity.setApproverRole(actor.actorRole());
    entity.setApproverOrg(actor.actorOrg());
    entity.setDecidedAt(now);
    entity.setUpdatedAt(now);
    entity.setExpiresAt(now);

    auditService.record(
        entity.getDatasetId(),
        actor,
        "ACCESS_REQUEST_REVOKED",
        "revoked",
        "%s revoked".formatted(entity.getId())
    );
    return toResponse(entity);
  }

  @Transactional(readOnly = true)
  public boolean canReadBrainActivity(String datasetId, ActorContext actor) {
    DatasetEntity dataset = requireDataset(datasetId);
    if (actor.belongsTo(dataset.getOwnerOrganization())) {
      return true;
    }

    return accessRequestRepository
        .findFirstByDatasetIdAndActorIdAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
            datasetId,
            actor.actorId(),
            "approved",
            Instant.now()
        )
        .isPresent();
  }

  private AccessRequestEntity requirePendingRequest(String requestId) {
    AccessRequestEntity entity = accessRequestRepository.findById(requestId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Access request not found."));
    if (!"pending".equalsIgnoreCase(entity.getStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending access requests can be updated.");
    }
    return entity;
  }

  private DatasetEntity requireDataset(String datasetId) {
    return datasetRepository.findById(datasetId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dataset not found."));
  }

  private void ensureApprover(ActorContext actor, DatasetEntity dataset) {
    boolean privilegedRole = actor.hasRole("owner") || actor.hasRole("admin") || actor.hasRole("approver");
    if (!privilegedRole || !actor.belongsTo(dataset.getOwnerOrganization())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Actor is not allowed to decide access requests.");
    }
  }

  private String nextAccessRequestId() {
    int next = accessRequestRepository.findAllIds().stream()
        .map(this::parseNumericId)
        .max(Comparator.naturalOrder())
        .orElse(0) + 1;
    return "ar-%d".formatted(next);
  }

  private int parseNumericId(String id) {
    Matcher matcher = ACCESS_REQUEST_ID_PATTERN.matcher(id == null ? "" : id.trim());
    if (!matcher.matches()) {
      return 0;
    }
    return Integer.parseInt(matcher.group(1));
  }

  private AccessRequestResponse toResponse(AccessRequestEntity entity) {
    return new AccessRequestResponse(
        entity.getId(),
        entity.getDatasetId(),
        entity.getActorId(),
        entity.getActorRole(),
        entity.getActorOrg(),
        entity.getPurpose(),
        entity.getRequestedDurationHours(),
        entity.getReason(),
        entity.getStatus(),
        entity.getPolicyNote(),
        entity.getApprovedDurationHours(),
        entity.getApproverId(),
        entity.getApproverRole(),
        entity.getApproverOrg(),
        entity.getCreatedAt(),
        entity.getUpdatedAt(),
        entity.getDecidedAt(),
        entity.getExpiresAt()
    );
  }
}
