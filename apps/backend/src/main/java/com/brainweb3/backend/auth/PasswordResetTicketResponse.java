package com.brainweb3.backend.auth;

import java.time.Instant;

public record PasswordResetTicketResponse(
    String actorId,
    String resetToken,
    Instant expiresAt
) {
}
