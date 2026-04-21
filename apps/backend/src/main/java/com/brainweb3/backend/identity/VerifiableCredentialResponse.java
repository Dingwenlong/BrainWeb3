package com.brainweb3.backend.identity;

import java.time.Instant;
import java.util.Map;

public record VerifiableCredentialResponse(
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
    String verificationStatus,
    Map<String, String> claims
) {
}
