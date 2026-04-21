package com.brainweb3.backend.destruction;

import jakarta.validation.constraints.NotBlank;

public record CreateDestructionRequest(
    @NotBlank String datasetId,
    @NotBlank String reason
) {
}
