package com.brainweb3.backend.identity;

public record CredentialVerificationResponse(
    boolean verified,
    String status,
    String reason
) {
}
