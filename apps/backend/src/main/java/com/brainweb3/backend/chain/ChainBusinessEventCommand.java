package com.brainweb3.backend.chain;

import java.time.Instant;

public record ChainBusinessEventCommand(
    String datasetId,
    String datasetTitle,
    String ownerOrganization,
    String eventType,
    String referenceId,
    String status,
    String actorId,
    String actorRole,
    String actorOrg,
    String detail,
    Instant occurredAt
) {
}
