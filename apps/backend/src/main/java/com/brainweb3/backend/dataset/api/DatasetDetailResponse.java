package com.brainweb3.backend.dataset.api;

import com.brainweb3.backend.chain.ChainBusinessRecordResponse;
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
    String destructionStatus,
    int channelCount,
    int sampleCount,
    double durationSeconds,
    int samplingRate,
    List<String> tags,
    String lastUploadTraceId,
    String lastErrorMessage,
    boolean retryAllowed,
    List<UploadAuditResponse> uploadAudits,
    DataAssetProofResponse proof,
    List<ChainBusinessRecordResponse> chainRecords,
    Instant destroyedAt,
    Instant updatedAt
) {
}
