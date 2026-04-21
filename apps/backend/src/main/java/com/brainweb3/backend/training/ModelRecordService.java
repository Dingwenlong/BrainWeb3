package com.brainweb3.backend.training;

import com.brainweb3.backend.access.ActorContext;
import com.brainweb3.backend.audit.AuditEventResponse;
import com.brainweb3.backend.audit.AuditService;
import com.brainweb3.backend.chain.ChainBusinessRecordResponse;
import com.brainweb3.backend.chain.ChainBusinessRecordService;
import com.brainweb3.backend.dataset.persistence.DatasetRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ModelRecordService {

  private static final Pattern MODEL_RECORD_ID_PATTERN = Pattern.compile("^mr-(\\d+)$");
  private static final Map<String, List<String>> GOVERNANCE_TRANSITIONS = Map.of(
      "candidate", List.of("active", "archived"),
      "active", List.of("archived"),
      "archived", List.of("active")
  );

  private final ModelRecordRepository modelRecordRepository;
  private final DatasetRepository datasetRepository;
  private final AuditService auditService;
  private final ChainBusinessRecordService chainBusinessRecordService;

  public ModelRecordService(
      ModelRecordRepository modelRecordRepository,
      DatasetRepository datasetRepository,
      AuditService auditService,
      ChainBusinessRecordService chainBusinessRecordService
  ) {
    this.modelRecordRepository = modelRecordRepository;
    this.datasetRepository = datasetRepository;
    this.auditService = auditService;
    this.chainBusinessRecordService = chainBusinessRecordService;
  }

  @Transactional(readOnly = true)
  public List<ModelRecordResponse> list(ActorContext actor, String datasetId, String governanceStatus, String trainingJobId) {
    return modelRecordRepository.findAllByOrderByCreatedAtDesc().stream()
        .filter(record -> isVisibleTo(actor, record))
        .filter(record -> matchesIgnoreCase(datasetId, record.getDatasetId()))
        .filter(record -> matchesIgnoreCase(governanceStatus, record.getGovernanceStatus()))
        .filter(record -> matchesIgnoreCase(trainingJobId, record.getTrainingJobId()))
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public ModelRecordResponse get(String modelId, ActorContext actor) {
    return toResponse(requireVisibleModel(modelId, actor));
  }

  @Transactional(readOnly = true)
  public ModelGovernanceLaneResponse governanceLane(String modelId, ActorContext actor) {
    ModelRecordEntity record = requireVisibleModel(modelId, actor);
    List<ModelRecordEntity> datasetRecords = modelRecordRepository.findAllByOrderByCreatedAtDesc().stream()
        .filter(candidate -> isVisibleTo(actor, candidate))
        .filter(candidate -> record.getDatasetId().equalsIgnoreCase(candidate.getDatasetId()))
        .toList();
    List<ModelRecordEntity> sortedDatasetRecords = datasetRecords.stream()
        .sorted(datasetVersionComparator())
        .toList();

    List<AuditEventResponse> auditEvents = auditService.listEvents(
            actor,
            record.getDatasetId(),
            null,
            null,
            null,
            null
        ).stream()
        .filter(event -> event.action() != null && event.action().startsWith("MODEL_"))
        .filter(event -> modelId.equalsIgnoreCase(extractModelId(event.detail())))
        .toList();

    boolean chainVisible = canInspectChain(actor);
    List<ChainBusinessRecordResponse> chainRecords = chainVisible
        ? chainBusinessRecordService.list(actor, record.getDatasetId(), null, null, null, null).stream()
            .filter(event -> event.eventType() != null && event.eventType().startsWith("MODEL_"))
            .filter(event -> modelId.equalsIgnoreCase(event.referenceId()))
            .toList()
        : List.of();

    List<ModelRecordResponse> relatedModels = datasetRecords.stream()
        .filter(candidate -> !candidate.getId().equalsIgnoreCase(modelId))
        .sorted(datasetVersionComparator())
        .limit(3)
        .map(this::toResponse)
        .toList();

    return new ModelGovernanceLaneResponse(
        toResponse(record),
        summarizeDatasetRecords(datasetRecords),
        summarizeVersionComparison(record, sortedDatasetRecords),
        relatedModels,
        auditEvents,
        chainRecords,
        chainVisible
    );
  }

  @Transactional
  public ModelRecordResponse updateGovernance(String modelId, ActorContext actor, UpdateModelGovernanceRequest request) {
    ModelRecordEntity record = requireVisibleModel(modelId, actor);
    ensureGovernanceAllowed(actor, record);

    String currentStatus = normalizeStatus(record.getGovernanceStatus());
    String nextStatus = normalizeStatus(request.status());
    ensureTransitionAllowed(currentStatus, nextStatus);

    String nextNote = normalizeNote(request.note());
    Instant now = Instant.now();
    record.setGovernanceStatus(nextStatus);
    record.setGovernanceNote(nextNote);
    record.setLastGovernedBy(actor.actorId());
    record.setGovernedAt(now);
    record.setUpdatedAt(now);
    modelRecordRepository.save(record);

    auditService.record(
        record.getDatasetId(),
        actor,
        "MODEL_GOVERNANCE_UPDATED",
        nextStatus,
        buildGovernanceDetail(record.getId(), currentStatus, nextStatus, nextNote)
    );
    chainBusinessRecordService.record(
        record.getDatasetId(),
        actor,
        "MODEL_GOVERNED",
        record.getId(),
        nextStatus,
        buildGovernanceDetail(record.getId(), currentStatus, nextStatus, nextNote)
    );
    return toResponse(record);
  }

  @Transactional
  public ModelRecordResponse registerCompletedModel(TrainingJobEntity job, ActorContext actor) {
    Optional<ModelRecordEntity> existing = modelRecordRepository.findByTrainingJobId(job.getId());
    if (existing.isPresent()) {
      ModelRecordEntity record = existing.get();
      record.setMetricSummary(job.getMetricSummary());
      record.setResultSummary(job.getResultSummary());
      record.setUpdatedAt(Instant.now());
      record.setCompletedAt(job.getCompletedAt());
      return toResponse(modelRecordRepository.save(record));
    }

    Instant now = Instant.now();
    ModelRecordEntity record = new ModelRecordEntity();
    record.setId(nextModelRecordId());
    record.setTrainingJobId(job.getId());
    record.setDatasetId(job.getDatasetId());
    record.setDatasetTitle(job.getDatasetTitle());
    record.setActorId(job.getActorId());
    record.setActorRole(job.getActorRole());
    record.setActorOrg(job.getActorOrg());
    record.setOrchestrator(job.getOrchestrator());
    record.setAlgorithm(job.getAlgorithm());
    record.setModelName(job.getModelName());
    record.setObjective(job.getObjective());
    record.setGovernanceStatus("candidate");
    record.setGovernanceNote("Auto-registered from completed training run.");
    record.setArtifactRef("registry://models/%s".formatted(job.getId()));
    record.setMetricSummary(valueOrEmpty(job.getMetricSummary()));
    record.setResultSummary(valueOrEmpty(job.getResultSummary()));
    record.setCreatedAt(now);
    record.setUpdatedAt(now);
    record.setCompletedAt(job.getCompletedAt());
    modelRecordRepository.save(record);

    auditService.record(
        job.getDatasetId(),
        actor,
        "MODEL_VERSION_REGISTERED",
        "candidate",
        "%s registered from %s".formatted(record.getId(), job.getId())
    );
    chainBusinessRecordService.record(
        job.getDatasetId(),
        actor,
        "MODEL_REGISTERED",
        record.getId(),
        "candidate",
        "%s registered from %s".formatted(record.getId(), job.getId())
    );
    return toResponse(record);
  }

  private ModelRecordEntity requireVisibleModel(String modelId, ActorContext actor) {
    ModelRecordEntity record = modelRecordRepository.findById(modelId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Model record not found."));
    if (!isVisibleTo(actor, record)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Actor is not allowed to view this model record.");
    }
    return record;
  }

  private boolean isVisibleTo(ActorContext actor, ModelRecordEntity record) {
    if (actor.hasRole("admin")) {
      return true;
    }
    if (actor.hasRole("owner") || actor.hasRole("approver")) {
      return datasetRepository.findById(record.getDatasetId())
          .map(dataset -> actor.belongsTo(dataset.getOwnerOrganization()))
          .orElse(false);
    }
    return actor.actorId() != null && actor.actorId().equalsIgnoreCase(record.getActorId());
  }

  private boolean canInspectChain(ActorContext actor) {
    return actor.hasRole("admin") || actor.hasRole("owner") || actor.hasRole("approver");
  }

  private void ensureGovernanceAllowed(ActorContext actor, ModelRecordEntity record) {
    if (actor.hasRole("admin")) {
      return;
    }
    if ((actor.hasRole("owner") || actor.hasRole("approver"))
        && datasetRepository.findById(record.getDatasetId())
            .map(dataset -> actor.belongsTo(dataset.getOwnerOrganization()))
            .orElse(false)) {
      return;
    }
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Actor is not allowed to govern this model record.");
  }

  private String nextModelRecordId() {
    int next = modelRecordRepository.findAllIds().stream()
        .map(this::parseNumericId)
        .max(Comparator.naturalOrder())
        .orElse(0) + 1;
    return "mr-%d".formatted(next);
  }

  private int parseNumericId(String value) {
    Matcher matcher = MODEL_RECORD_ID_PATTERN.matcher(value == null ? "" : value.trim());
    if (!matcher.matches()) {
      return 0;
    }
    return Integer.parseInt(matcher.group(1));
  }

  private boolean matchesIgnoreCase(String expected, String actual) {
    return expected == null || expected.isBlank() || (actual != null && expected.equalsIgnoreCase(actual));
  }

  private String normalizeStatus(String status) {
    String normalized = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "candidate", "active", "archived" -> normalized;
      default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported governance status.");
    };
  }

  private String normalizeNote(String note) {
    return note == null ? "" : note.trim();
  }

  private void ensureTransitionAllowed(String currentStatus, String nextStatus) {
    if (currentStatus.equals(nextStatus)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Model record is already in the requested governance status."
      );
    }

    if (!allowedGovernanceTransitions(currentStatus).contains(nextStatus)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Unsupported model governance transition: %s -> %s.".formatted(currentStatus, nextStatus)
      );
    }
  }

  private List<String> allowedGovernanceTransitions(String currentStatus) {
    return GOVERNANCE_TRANSITIONS.getOrDefault(currentStatus, List.of());
  }

  private String buildGovernanceDetail(String modelId, String currentStatus, String nextStatus, String note) {
    if (note == null || note.isBlank()) {
      return "%s %s -> %s".formatted(modelId, currentStatus, nextStatus);
    }
    return "%s %s -> %s | %s".formatted(modelId, currentStatus, nextStatus, note);
  }

  private String extractModelId(String detail) {
    if (detail == null || detail.isBlank()) {
      return "";
    }
    Matcher matcher = MODEL_RECORD_ID_PATTERN.matcher(detail.trim());
    if (matcher.matches()) {
      return detail.trim();
    }

    Matcher embedded = Pattern.compile("\\b(mr-\\d+)\\b", Pattern.CASE_INSENSITIVE).matcher(detail);
    if (!embedded.find()) {
      return "";
    }
    return embedded.group(1);
  }

  private ModelGovernanceSummaryResponse summarizeDatasetRecords(List<ModelRecordEntity> records) {
    long candidateCount = records.stream()
        .filter(record -> "candidate".equalsIgnoreCase(record.getGovernanceStatus()))
        .count();
    long activeCount = records.stream()
        .filter(record -> "active".equalsIgnoreCase(record.getGovernanceStatus()))
        .count();
    long archivedCount = records.stream()
        .filter(record -> "archived".equalsIgnoreCase(record.getGovernanceStatus()))
        .count();

    ModelRecordEntity latestGoverned = records.stream()
        .filter(record -> record.getGovernedAt() != null)
        .max(Comparator.comparing(ModelRecordEntity::getGovernedAt))
        .orElse(null);

    return new ModelGovernanceSummaryResponse(
        records.size(),
        Math.toIntExact(candidateCount),
        Math.toIntExact(activeCount),
        Math.toIntExact(archivedCount),
        latestGoverned == null ? null : latestGoverned.getGovernedAt(),
        latestGoverned == null ? "" : valueOrEmpty(latestGoverned.getLastGovernedBy())
    );
  }

  private ModelVersionComparisonResponse summarizeVersionComparison(
      ModelRecordEntity current,
      List<ModelRecordEntity> sortedRecords
  ) {
    int currentIndex = 0;
    for (int index = 0; index < sortedRecords.size(); index += 1) {
      if (sortedRecords.get(index).getId().equalsIgnoreCase(current.getId())) {
        currentIndex = index;
        break;
      }
    }

    ModelRecordEntity latestRecord = sortedRecords.isEmpty() ? null : sortedRecords.get(0);
    ModelRecordEntity latestActiveRecord = sortedRecords.stream()
        .filter(record -> "active".equalsIgnoreCase(record.getGovernanceStatus()))
        .findFirst()
        .orElse(null);

    long sameAlgorithmCount = sortedRecords.stream()
        .filter(record -> matchesIgnoreCase(current.getAlgorithm(), record.getAlgorithm()))
        .count();
    long sameStatusCount = sortedRecords.stream()
        .filter(record -> matchesIgnoreCase(current.getGovernanceStatus(), record.getGovernanceStatus()))
        .count();

    return new ModelVersionComparisonResponse(
        currentIndex + 1,
        sortedRecords.size(),
        currentIndex,
        Math.max(0, sortedRecords.size() - currentIndex - 1),
        currentIndex == 0,
        latestRecord == null ? "" : valueOrEmpty(latestRecord.getId()),
        latestRecord == null ? null : latestRecord.getCompletedAt(),
        Math.toIntExact(sameAlgorithmCount),
        Math.toIntExact(sameStatusCount),
        latestActiveRecord == null ? "" : valueOrEmpty(latestActiveRecord.getId()),
        latestActiveRecord == null ? null : latestActiveRecord.getGovernedAt()
    );
  }

  private Comparator<ModelRecordEntity> datasetVersionComparator() {
    return Comparator.comparing(ModelRecordEntity::getCompletedAt, Comparator.nullsLast(Comparator.reverseOrder()))
        .thenComparing(ModelRecordEntity::getCreatedAt, Comparator.reverseOrder());
  }

  private ModelRecordResponse toResponse(ModelRecordEntity record) {
    return new ModelRecordResponse(
        record.getId(),
        record.getTrainingJobId(),
        record.getDatasetId(),
        record.getDatasetTitle(),
        record.getActorId(),
        record.getActorRole(),
        record.getActorOrg(),
        record.getOrchestrator(),
        record.getAlgorithm(),
        record.getModelName(),
        record.getObjective(),
        record.getGovernanceStatus(),
        valueOrEmpty(record.getGovernanceNote()),
        record.getArtifactRef(),
        valueOrEmpty(record.getMetricSummary()),
        valueOrEmpty(record.getResultSummary()),
        valueOrEmpty(record.getLastGovernedBy()),
        allowedGovernanceTransitions(normalizeStatus(record.getGovernanceStatus())),
        record.getCreatedAt(),
        record.getUpdatedAt(),
        record.getGovernedAt(),
        record.getCompletedAt()
    );
  }

  private String valueOrEmpty(String value) {
    return value == null ? "" : value;
  }
}
