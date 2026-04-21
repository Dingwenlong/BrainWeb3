package com.brainweb3.backend.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank @Size(min = 3, max = 80) String actorId,
    @NotBlank @Size(min = 2, max = 120) String displayName,
    @NotBlank @Size(min = 2, max = 160) String actorOrg,
    @NotBlank @Size(min = 8, max = 72) String password
) {
}
