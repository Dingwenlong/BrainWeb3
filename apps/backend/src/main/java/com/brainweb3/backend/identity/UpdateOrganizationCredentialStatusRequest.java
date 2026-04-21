package com.brainweb3.backend.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateOrganizationCredentialStatusRequest(
    @NotBlank @Size(max = 160) String organizationName,
    @NotBlank @Pattern(regexp = "issued|suspended|revoked") String status,
    @Size(max = 255) String reason
) {
}
