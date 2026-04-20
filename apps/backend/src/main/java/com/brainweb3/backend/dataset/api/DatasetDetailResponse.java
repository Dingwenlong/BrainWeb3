package com.brainweb3.backend.dataset.api;

import java.time.Instant;
import java.util.List;

public record DatasetDetailResponse(
    String id,
    String subjectCode,
    String title,
    String description,
    String originalFilename,
    long fileSizeBytes,
    String ownerOrganization,
    String format,
    String uploadStatus,
    String proofStatus,
    String trainingReadiness,
    int channelCount,
    int sampleCount,
    double durationSeconds,
    int samplingRate,
    List<String> tags,
    DataAssetProofResponse proof,
    Instant updatedAt
) {
}
