package com.brainweb3.backend.access;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateAccessRequest(
    @NotBlank String datasetId,
    @NotBlank String purpose,
    @Min(1) @Max(720) int requestedDurationHours,
    @NotBlank String reason
) {
}
