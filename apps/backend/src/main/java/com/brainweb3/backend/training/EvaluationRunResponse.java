package com.brainweb3.backend.training;

import java.time.Instant;

public record EvaluationRunResponse(
    String id,
    String modelRecordId,
    String datasetId,
    String evaluatorActorId,
    String evaluatorRole,
    String evaluatorOrg,
    String testSetHash,
    String evalScriptHash,
    String metricsJson,
    String resultHash,
    String verificationStatus,
    String notes,
    Instant createdAt
) {
}
