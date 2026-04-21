package com.brainweb3.backend.training;

import java.time.Instant;

public record ModelVersionComparisonResponse(
    int currentVersionRank,
    int totalVisibleVersions,
    int newerVersionCount,
    int olderVersionCount,
    boolean latestVersion,
    String latestVersionId,
    Instant latestVersionCompletedAt,
    int sameAlgorithmVersionCount,
    int sameStatusVersionCount,
    String latestActiveVersionId,
    Instant latestActiveGovernedAt
) {
}
