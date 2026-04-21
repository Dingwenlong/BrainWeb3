package com.brainweb3.backend.auth;

import java.time.Instant;

public record PasswordResetTicketIssue(
    String resetToken,
    Instant expiresAt
) {
}
