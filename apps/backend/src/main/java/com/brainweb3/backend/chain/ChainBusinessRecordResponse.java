package com.brainweb3.backend.chain;

import java.time.Instant;

public record ChainBusinessRecordResponse(
    long id,
    String datasetId,
    String eventType,
    String referenceId,
    String businessStatus,
    String anchorPolicy,
    String anchorStatus,
    String actorId,
    String actorRole,
    String actorOrg,
    String chainProvider,
    String chainGroup,
    String contractName,
    String contractAddress,
    String eventHash,
    String chainTxHash,
    String detail,
    String anchorError,
    Instant anchoredAt
) {
}
