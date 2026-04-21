package com.brainweb3.backend.training;

import java.time.Instant;

public record TrainingJobResponse(
    String id,
    String datasetId,
    String datasetTitle,
    String actorId,
    String actorRole,
    String actorOrg,
    String orchestrator,
    String algorithm,
    String modelName,
    String objective,
    int requestedRounds,
    int completedRounds,
    String status,
    String externalJobRef,
    String latestMessage,
    String metricSummary,
    String resultSummary,
    Instant createdAt,
    Instant updatedAt,
    Instant startedAt,
    Instant completedAt
) {
}
