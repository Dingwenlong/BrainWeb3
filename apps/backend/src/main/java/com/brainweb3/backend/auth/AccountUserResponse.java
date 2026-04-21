package com.brainweb3.backend.auth;

import com.brainweb3.backend.identity.CredentialStatusSnapshot;
import com.brainweb3.backend.identity.CredentialHistoryEntryResponse;
import java.time.Instant;
import java.util.List;

public record AccountUserResponse(
    String actorId,
    String displayName,
    String actorRole,
    String actorOrg,
    String status,
    CredentialStatusSnapshot credentialStatus,
    List<CredentialHistoryEntryResponse> credentialHistory,
    Instant createdAt,
    Instant updatedAt,
    Instant passwordChangedAt,
    Instant lastLoginAt
) {
}
