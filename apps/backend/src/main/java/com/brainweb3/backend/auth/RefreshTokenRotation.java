package com.brainweb3.backend.auth;

import java.time.Instant;

public record RefreshTokenRotation(
    String userId,
    String refreshToken,
    Instant refreshExpiresAt
) {
}
