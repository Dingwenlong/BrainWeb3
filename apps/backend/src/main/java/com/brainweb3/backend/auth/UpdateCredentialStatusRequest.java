package com.brainweb3.backend.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateCredentialStatusRequest(
    @NotBlank @Pattern(regexp = "issued|suspended|revoked") String status,
    @Size(max = 255) String reason
) {
}
