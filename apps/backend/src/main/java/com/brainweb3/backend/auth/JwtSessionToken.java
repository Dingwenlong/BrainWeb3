package com.brainweb3.backend.auth;

import java.time.Instant;

public record JwtSessionToken(
    String token,
    Instant expiresAt
) {
}
