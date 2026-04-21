package com.brainweb3.backend.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank String actorId,
    @NotBlank String password
) {
}
