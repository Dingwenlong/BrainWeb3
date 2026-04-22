package com.brainweb3.backend.chain;

import com.brainweb3.backend.access.ActorContext;
import com.brainweb3.backend.config.SensitiveTextSanitizer;
import com.brainweb3.backend.dataset.persistence.DatasetEntity;
import com.brainweb3.backend.dataset.persistence.DatasetRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChainBusinessRecordService {

  private final ChainBusinessRecordRepository chainBusinessRecordRepository;
  private final DatasetRepository datasetRepository;
  private final ChainGateway chainGateway;
  private final ChainPolicyService chainPolicyService;
  private final SensitiveTextSanitizer sensitiveTextSanitizer;

  public ChainBusinessRecordService(
      ChainBusinessRecordRepository chainBusinessRecordRepository,
      DatasetRepository datasetRepository,
      ChainGateway chainGateway,
      ChainPolicyService chainPolicyService,
      SensitiveTextSanitizer sensitiveTextSanitizer
  ) {
    this.chainBusinessRecordRepository = chainBusinessRecordRepository;
    this.datasetRepository = datasetRepository;
    this.chainGateway = chainGateway;
    this.chainPolicyService = chainPolicyService;
    this.sensitiveTextSanitizer = sensitiveTextSanitizer;
  }

  @Transactional
  public ChainBusinessRecordResponse record(
      String datasetId,
      ActorContext actor,
      String eventType,
      String referenceId,
      String businessStatus,
      String detail
  ) {
    DatasetEntity dataset = datasetRepository.findById(datasetId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dataset not found."));
    String sanitizedDetail = sensitiveTextSanitizer.sanitize(detail);

    ChainBusinessRecordEntity entity = new ChainBusinessRecordEntity();
    entity.setDatasetId(dataset.getId());
    entity.setEventType(eventType);
    entity.setReferenceId(referenceId);
    entity.setBusinessStatus(businessStatus);
    entity.setActorId(actor.actorId());
    entity.setActorRole(actor.actorRole());
    entity.setActorOrg(actor.actorOrg());
    entity.setDetail(sanitizedDetail);
    entity.setAnchoredAt(Instant.now());

    if (!chainPolicyService.shouldAnchor(eventType)) {
      entity.setAnchorStatus("policy-skipped");
    } else {
      try {
        ChainBusinessEventReceipt receipt = chainGateway.recordBusinessEvent(
            new ChainBusinessEventCommand(
                dataset.getId(),
                dataset.getTitle(),
                dataset.getOwnerOrganization(),
                eventType,
                referenceId,
                businessStatus,
                actor.actorId(),
                actor.actorRole(),
                actor.actorOrg(),
                sanitizedDetail,
                Instant.now()
            )
        );
        applyReceipt(entity, receipt);
      } catch (RuntimeException exception) {
        applyFailure(entity, exception);
      }
    }

    return toResponse(chainBusinessRecordRepository.save(entity));
  }

  @Transactional(readOnly = true)
  public List<ChainBusinessRecordResponse> listByDataset(String datasetId) {
    return chainBusinessRecordRepository.findAllByDatasetIdOrderByAnchoredAtDesc(datasetId).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<ChainBusinessRecordResponse> list(
      ActorContext viewer,
      String datasetId,
      String eventType,
      String anchorStatus,
      String businessStatus,
      String chainTxHash
  ) {
    ensureViewerAllowed(viewer);
    return chainBusinessRecordRepository.findAllByOrderByAnchoredAtDesc().stream()
        .filter(record -> isVisibleTo(viewer, record))
        .filter(record -> matchesIgnoreCase(datasetId, record.getDatasetId()))
        .filter(record -> matchesIgnoreCase(eventType, record.getEventType()))
        .filter(record -> matchesIgnoreCase(anchorStatus, record.getAnchorStatus()))
        .filter(record -> matchesIgnoreCase(businessStatus, record.getBusinessStatus()))
        .filter(record -> matchesContainsIgnoreCase(chainTxHash, record.getChainTxHash()))
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public ChainBusinessRecordResponse retry(long recordId, ActorContext viewer) {
    ensureViewerAllowed(viewer);

    ChainBusinessRecordEntity entity = chainBusinessRecordRepository.findById(recordId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chain business record not found."));
    if (!isVisibleTo(viewer, entity)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chain business record not found.");
    }
    if (!chainPolicyService.shouldAnchor(entity.getEventType())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "This event is configured as audit-only and cannot be anchored.");
    }
    if (!"failed".equalsIgnoreCase(entity.getAnchorStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Only failed chain business records can be retried.");
    }

    DatasetEntity dataset = datasetRepository.findById(entity.getDatasetId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dataset not found."));

    try {
      ChainBusinessEventReceipt receipt = chainGateway.recordBusinessEvent(buildCommand(dataset, entity));
      applyReceipt(entity, receipt);
      entity.setAnchorError(null);
    } catch (RuntimeException exception) {
      applyFailure(entity, exception);
    }

    return toResponse(chainBusinessRecordRepository.save(entity));
  }

  private ChainBusinessRecordResponse toResponse(ChainBusinessRecordEntity entity) {
    return new ChainBusinessRecordResponse(
        entity.getId(),
        entity.getDatasetId(),
        entity.getEventType(),
        entity.getReferenceId(),
        entity.getBusinessStatus(),
        chainPolicyService.resolvePolicy(entity.getEventType()),
        entity.getAnchorStatus(),
        entity.getActorId(),
        entity.getActorRole(),
        entity.getActorOrg(),
        valueOrEmpty(entity.getChainProvider()),
        valueOrEmpty(entity.getChainGroup()),
        valueOrEmpty(entity.getContractName()),
        valueOrEmpty(entity.getContractAddress()),
        valueOrEmpty(entity.getEventHash()),
        valueOrEmpty(entity.getChainTxHash()),
        valueOrEmpty(sensitiveTextSanitizer.sanitize(entity.getDetail())),
        valueOrEmpty(sensitiveTextSanitizer.sanitize(entity.getAnchorError())),
        entity.getAnchoredAt()
    );
  }

  private ChainBusinessEventCommand buildCommand(DatasetEntity dataset, ChainBusinessRecordEntity entity) {
    return new ChainBusinessEventCommand(
        dataset.getId(),
        dataset.getTitle(),
        dataset.getOwnerOrganization(),
        entity.getEventType(),
        entity.getReferenceId(),
        entity.getBusinessStatus(),
        entity.getActorId(),
        entity.getActorRole(),
        entity.getActorOrg(),
        entity.getDetail(),
        Instant.now()
    );
  }

  private void applyReceipt(ChainBusinessRecordEntity entity, ChainBusinessEventReceipt receipt) {
    entity.setAnchorStatus("anchored");
    entity.setChainProvider(receipt.chainProvider());
    entity.setChainGroup(receipt.chainGroup());
    entity.setContractName(receipt.contractName());
    entity.setContractAddress(receipt.contractAddress());
    entity.setEventHash(receipt.eventHash());
    entity.setChainTxHash(receipt.chainTxHash());
    entity.setAnchorError(null);
    entity.setAnchoredAt(receipt.anchoredAt());
  }

  private void applyFailure(ChainBusinessRecordEntity entity, RuntimeException exception) {
    ChainRuntimeStatus runtimeStatus = chainGateway.describeStatus();
    entity.setAnchorStatus("failed");
    entity.setChainProvider(runtimeStatus.provider());
    entity.setChainGroup(runtimeStatus.group());
    entity.setContractName(runtimeStatus.contractName());
    entity.setContractAddress(runtimeStatus.contractAddress());
    entity.setEventHash(null);
    entity.setChainTxHash(null);
    entity.setAnchorError(sensitiveTextSanitizer.sanitize(exception.getMessage()));
    entity.setAnchoredAt(Instant.now());
  }

  private String valueOrEmpty(String value) {
    return value == null ? "" : value;
  }

  public void ensureViewerAllowed(ActorContext viewer) {
    if (viewer.hasRole("admin") || viewer.hasRole("owner") || viewer.hasRole("approver")) {
      return;
    }
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Actor is not allowed to inspect chain business records.");
  }

  private boolean isVisibleTo(ActorContext viewer, ChainBusinessRecordEntity record) {
    if (viewer.hasRole("admin")) {
      return true;
    }
    return datasetRepository.findById(record.getDatasetId())
        .map(dataset -> viewer.belongsTo(dataset.getOwnerOrganization()))
        .orElse(false);
  }

  private boolean matchesIgnoreCase(String expected, String actual) {
    return expected == null || expected.isBlank() || (actual != null && expected.equalsIgnoreCase(actual));
  }

  private boolean matchesContainsIgnoreCase(String expected, String actual) {
    return expected == null
        || expected.isBlank()
        || (actual != null && actual.toLowerCase().contains(expected.toLowerCase()));
  }
}
