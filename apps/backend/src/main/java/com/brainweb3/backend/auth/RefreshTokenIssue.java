package com.brainweb3.backend.auth;

import java.time.Instant;

public record RefreshTokenIssue(
    String tokenId,
    String refreshToken,
    Instant expiresAt
) {
}
