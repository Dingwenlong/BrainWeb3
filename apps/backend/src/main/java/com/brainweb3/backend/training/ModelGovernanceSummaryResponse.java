package com.brainweb3.backend.training;

import java.time.Instant;

public record ModelGovernanceSummaryResponse(
    int datasetVersionCount,
    int candidateVersionCount,
    int activeVersionCount,
    int archivedVersionCount,
    Instant latestGovernedAt,
    String latestGovernedBy
) {
}
