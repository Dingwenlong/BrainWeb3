package com.brainweb3.backend.access;

import java.time.Instant;

public record AccessRequestResponse(
    String id,
    String datasetId,
    String actorId,
    String actorRole,
    String actorOrg,
    String purpose,
    int requestedDurationHours,
    String reason,
    String status,
    String policyNote,
    Integer approvedDurationHours,
    String approverId,
    String approverRole,
    String approverOrg,
    Instant createdAt,
    Instant updatedAt,
    Instant decidedAt,
    Instant expiresAt
) {
}
