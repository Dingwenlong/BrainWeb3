package com.brainweb3.backend.audit;

import java.time.Instant;

public record AuditEventResponse(
    long id,
    String datasetId,
    String actorId,
    String actorRole,
    String actorOrg,
    String action,
    String status,
    String detail,
    Instant createdAt
) {
}
