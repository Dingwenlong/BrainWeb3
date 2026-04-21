package com.brainweb3.backend.identity;

import java.time.Instant;

public record CredentialStatusSnapshot(
    String effectiveStatus,
    String source,
    String reason,
    String updatedBy,
    Instant updatedAt
) {
}
