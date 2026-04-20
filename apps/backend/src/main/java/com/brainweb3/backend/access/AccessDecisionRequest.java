package com.brainweb3.backend.access;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record AccessDecisionRequest(
    @Min(1) @Max(720) int approvedDurationHours,
    @NotBlank String policy
) {
}
