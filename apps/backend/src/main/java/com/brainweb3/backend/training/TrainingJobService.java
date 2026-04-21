package com.brainweb3.backend.training;

import com.brainweb3.backend.access.AccessRequestService;
import com.brainweb3.backend.access.ActorContext;
import com.brainweb3.backend.audit.AuditService;
import com.brainweb3.backend.chain.ChainBusinessRecordService;
import com.brainweb3.backend.dataset.persistence.DatasetEntity;
import com.brainweb3.backend.dataset.persistence.DatasetRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TrainingJobService {

  private static final Pattern TRAINING_JOB_ID_PATTERN = Pattern.compile("^tj-(\\d+)$");
  private static final String DEFAULT_ALGORITHM = "hetero-logistic-regression";

  private final TrainingJobRepository trainingJobRepository;
  private final DatasetRepository datasetRepository;
  private final AccessRequestService accessRequestService;
  private final AuditService auditService;
  private final FederatedTrainingGateway federatedTrainingGateway;
  private final ChainBusinessRecordService chainBusinessRecordService;
  private final ModelRecordService modelRecordService;

  public TrainingJobService(
      TrainingJobRepository trainingJobRepository,
      DatasetRepository datasetRepository,
      AccessRequestService accessRequestService,
      AuditService auditService,
      FederatedTrainingGateway federatedTrainingGateway,
      ChainBusinessRecordService chainBusinessRecordService,
      ModelRecordService modelRecordService
  ) {
    this.trainingJobRepository = trainingJobRepository;
    this.datasetRepository = datasetRepository;
    this.accessRequestService = accessRequestService;
    this.auditService = auditService;
    this.federatedTrainingGateway = federatedTrainingGateway;
    this.chainBusinessRecordService = chainBusinessRecordService;
    this.modelRecordService = modelRecordService;
  }

  @Transactional(readOnly = true)
  public List<TrainingJobResponse> list(ActorContext actor, String datasetId, String status) {
    return trainingJobRepository.findAllByOrderByCreatedAtDesc().stream()
        .filter(job -> isVisibleTo(actor, job))
        .filter(job -> matchesIgnoreCase(datasetId, job.getDatasetId()))
        .filter(job -> matchesIgnoreCase(status, job.getStatus()))
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public TrainingJobResponse get(String jobId, ActorContext actor) {
    TrainingJobEntity job = requireVisibleJob(jobId, actor);
    return toResponse(job);
  }

  @Transactional
  public TrainingJobResponse create(ActorContext actor, CreateTrainingJobRequest request) {
    DatasetEntity dataset = requireDataset(request.datasetId());
    ensureTrainingAllowed(dataset, actor);

    Instant now = Instant.now();
    TrainingJobEntity job = new TrainingJobEntity();
    job.setId(nextTrainingJobId());
    job.setDatasetId(dataset.getId());
    job.setDatasetTitle(dataset.getTitle());
    job.setActorId(actor.actorId());
    job.setActorRole(actor.actorRole());
    job.setActorOrg(actor.actorOrg());
    job.setAlgorithm(normalizeAlgorithm(request.algorithm()));
    job.setModelName(request.modelName().trim());
    job.setObjective(request.objective().trim());
    job.setRequestedRounds(request.requestedRounds() == null ? 6 : request.requestedRounds());
    job.setCompletedRounds(0);
    job.setStatus("running");
    job.setCreatedAt(now);
    job.setUpdatedAt(now);
    job.setStartedAt(now);

    TrainingLaunchReceipt receipt = federatedTrainingGateway.submit(
        new TrainingLaunchCommand(
            job.getId(),
            dataset.getId(),
            dataset.getTitle(),
            actor.actorId(),
            actor.actorOrg(),
            job.getAlgorithm(),
            job.getModelName(),
            job.getObjective(),
            job.getRequestedRounds()
        )
    );
    job.setOrchestrator(receipt.orchestrator());
    job.setExternalJobRef(receipt.externalJobRef());
    job.setLatestMessage(receipt.latestMessage());
    trainingJobRepository.save(job);

    auditService.record(
        dataset.getId(),
        actor,
        "TRAINING_RUN_CREATED",
        "accepted",
        "%s accepted via %s".formatted(job.getId(), receipt.orchestrator())
    );
    return toResponse(job);
  }

  @Transactional
  public TrainingJobResponse refresh(String jobId, ActorContext actor) {
    TrainingJobEntity job = requireVisibleJob(jobId, actor);
    if ("succeeded".equalsIgnoreCase(job.getStatus()) || "failed".equalsIgnoreCase(job.getStatus())) {
      return toResponse(job);
    }

    TrainingJobSnapshot snapshot = federatedTrainingGateway.refresh(job);
    job.setStatus(snapshot.status());
    job.setCompletedRounds(snapshot.completedRounds());
    job.setLatestMessage(snapshot.latestMessage());
    job.setMetricSummary(snapshot.metricSummary());
    job.setResultSummary(snapshot.resultSummary());
    job.setUpdatedAt(Instant.now());
    if ("succeeded".equalsIgnoreCase(snapshot.status()) || "failed".equalsIgnoreCase(snapshot.status())) {
      job.setCompletedAt(job.getUpdatedAt());
      auditService.record(
          job.getDatasetId(),
          actor,
          "succeeded".equalsIgnoreCase(snapshot.status()) ? "TRAINING_RUN_COMPLETED" : "TRAINING_RUN_FAILED",
          snapshot.status(),
          "%s -> %s".formatted(job.getId(), snapshot.metricSummary())
      );
      chainBusinessRecordService.record(
          job.getDatasetId(),
          actor,
          "succeeded".equalsIgnoreCase(snapshot.status()) ? "TRAINING_COMPLETED" : "TRAINING_FAILED",
          job.getId(),
          snapshot.status(),
          "%s -> %s".formatted(job.getId(), snapshot.metricSummary())
      );
      if ("succeeded".equalsIgnoreCase(snapshot.status())) {
        modelRecordService.registerCompletedModel(job, actor);
      }
    }

    return toResponse(job);
  }

  private TrainingJobEntity requireVisibleJob(String jobId, ActorContext actor) {
    TrainingJobEntity job = trainingJobRepository.findById(jobId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Training job not found."));
    if (!isVisibleTo(actor, job)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Actor is not allowed to view this training job.");
    }
    return job;
  }

  private DatasetEntity requireDataset(String datasetId) {
    return datasetRepository.findById(datasetId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dataset not found."));
  }

  private void ensureTrainingAllowed(DatasetEntity dataset, ActorContext actor) {
    if (!"active".equalsIgnoreCase(dataset.getDestructionStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Dataset is in destruction flow and is no longer eligible for training.");
    }
    if (!"notarized".equalsIgnoreCase(dataset.getProofStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Dataset proof is not ready for training orchestration.");
    }
    if (!dataset.getTrainingReadiness().toLowerCase(Locale.ROOT).contains("ready")) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Dataset is not yet marked as training-ready.");
    }
    if (actor.hasRole("admin")) {
      return;
    }
    if (actor.belongsTo(dataset.getOwnerOrganization())) {
      return;
    }
    if (accessRequestService.canAccessDataset(dataset.getId(), actor)) {
      return;
    }
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Approved access is required before training.");
  }

  private boolean isVisibleTo(ActorContext actor, TrainingJobEntity job) {
    if (actor.hasRole("admin")) {
      return true;
    }
    if (actor.hasRole("owner") || actor.hasRole("approver")) {
      return datasetRepository.findById(job.getDatasetId())
          .map(dataset -> actor.belongsTo(dataset.getOwnerOrganization()))
          .orElse(false);
    }
    return actor.actorId() != null && actor.actorId().equalsIgnoreCase(job.getActorId());
  }

  private String nextTrainingJobId() {
    int next = trainingJobRepository.findAllIds().stream()
        .map(this::parseNumericId)
        .max(Comparator.naturalOrder())
        .orElse(0) + 1;
    return "tj-%d".formatted(next);
  }

  private int parseNumericId(String value) {
    Matcher matcher = TRAINING_JOB_ID_PATTERN.matcher(value == null ? "" : value.trim());
    if (!matcher.matches()) {
      return 0;
    }
    return Integer.parseInt(matcher.group(1));
  }

  private boolean matchesIgnoreCase(String expected, String actual) {
    return expected == null || expected.isBlank() || (actual != null && expected.equalsIgnoreCase(actual));
  }

  private String normalizeAlgorithm(String value) {
    if (value == null || value.isBlank()) {
      return DEFAULT_ALGORITHM;
    }
    return value.trim().toLowerCase(Locale.ROOT);
  }

  private TrainingJobResponse toResponse(TrainingJobEntity job) {
    return new TrainingJobResponse(
        job.getId(),
        job.getDatasetId(),
        job.getDatasetTitle(),
        job.getActorId(),
        job.getActorRole(),
        job.getActorOrg(),
        job.getOrchestrator(),
        job.getAlgorithm(),
        job.getModelName(),
        job.getObjective(),
        job.getRequestedRounds(),
        job.getCompletedRounds(),
        job.getStatus(),
        valueOrEmpty(job.getExternalJobRef()),
        valueOrEmpty(job.getLatestMessage()),
        valueOrEmpty(job.getMetricSummary()),
        valueOrEmpty(job.getResultSummary()),
        job.getCreatedAt(),
        job.getUpdatedAt(),
        job.getStartedAt(),
        job.getCompletedAt()
    );
  }

  private String valueOrEmpty(String value) {
    return value == null ? "" : value;
  }
}
