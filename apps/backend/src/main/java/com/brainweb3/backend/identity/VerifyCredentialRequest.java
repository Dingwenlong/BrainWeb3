package com.brainweb3.backend.identity;

import java.time.Instant;
import java.util.Map;

public record VerifyCredentialRequest(
    String id,
    String type,
    String issuerDid,
    String holderDid,
    String subjectDid,
    String subjectType,
    Instant issuedAt,
    Instant expiresAt,
    String proof,
    String credentialStatus,
    Map<String, String> claims
) {
}
