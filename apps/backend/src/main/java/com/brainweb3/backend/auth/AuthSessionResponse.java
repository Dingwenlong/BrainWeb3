package com.brainweb3.backend.auth;

import java.time.Instant;

public record AuthSessionResponse(
    String token,
    Instant expiresAt,
    String refreshToken,
    Instant refreshExpiresAt,
    AuthActorResponse actor
) {
}
