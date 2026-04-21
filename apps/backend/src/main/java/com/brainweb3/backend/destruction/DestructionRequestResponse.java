package com.brainweb3.backend.destruction;

import java.time.Instant;

public record DestructionRequestResponse(
    String id,
    String datasetId,
    String datasetTitle,
    String ownerOrganization,
    String requesterId,
    String requesterRole,
    String requesterOrg,
    String reason,
    String status,
    String policyNote,
    String approverId,
    String approverRole,
    String approverOrg,
    String executedBy,
    String cleanupStatus,
    String cleanupError,
    String cleanupEvidenceRef,
    String cleanupEvidenceHash,
    String cleanupVerifiedBy,
    Instant createdAt,
    Instant updatedAt,
    Instant decidedAt,
    Instant executedAt,
    Instant cleanupCompletedAt
) {
}
