package com.brainweb3.backend.identity;

import java.time.Instant;

public record CredentialHistoryEntryResponse(
    Long id,
    String previousStatus,
    String nextStatus,
    String source,
    String reason,
    String updatedBy,
    Instant createdAt
) {
}
