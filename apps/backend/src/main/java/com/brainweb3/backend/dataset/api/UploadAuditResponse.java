package com.brainweb3.backend.dataset.api;

import java.time.Instant;

public record UploadAuditResponse(
    String action,
    String status,
    String message,
    String traceId,
    Instant createdAt
) {
}
