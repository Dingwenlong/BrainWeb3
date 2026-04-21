package com.brainweb3.backend.training;

import jakarta.validation.constraints.NotBlank;

public record UpdateModelGovernanceRequest(
    @NotBlank String status,
    String note
) {
}
