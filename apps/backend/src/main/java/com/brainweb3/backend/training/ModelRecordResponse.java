package com.brainweb3.backend.training;

import java.time.Instant;
import java.util.List;

public record ModelRecordResponse(
    String id,
    String trainingJobId,
    String datasetId,
    String datasetTitle,
    String actorId,
    String actorRole,
    String actorOrg,
    String orchestrator,
    String algorithm,
    String modelName,
    String objective,
    String governanceStatus,
    String governanceNote,
    String artifactRef,
    String metricSummary,
    String resultSummary,
    String lastGovernedBy,
    List<String> allowedGovernanceTransitions,
    Instant createdAt,
    Instant updatedAt,
    Instant governedAt,
    Instant completedAt
) {
}
