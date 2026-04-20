package com.brainweb3.backend.dataset.api;

import java.time.Instant;

public record DatasetSummaryResponse(
    String id,
    String subjectCode,
    String title,
    String ownerOrganization,
    String format,
    String uploadStatus,
    String proofStatus,
    String trainingReadiness,
    Instant updatedAt
) {
}
