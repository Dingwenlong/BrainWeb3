package com.brainweb3.backend.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateAccountRequest(
    @NotBlank @Size(min = 3, max = 80) String actorId,
    @NotBlank @Size(min = 2, max = 120) String displayName,
    @NotBlank @Pattern(regexp = "researcher|owner|approver|admin") String actorRole,
    @NotBlank @Size(min = 2, max = 160) String actorOrg,
    @NotBlank @Pattern(regexp = "active|disabled") String status,
    @NotBlank @Size(min = 8, max = 72) String password
) {
}
