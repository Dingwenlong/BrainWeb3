package com.brainweb3.backend.training;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateTrainingJobRequest(
    @NotBlank String datasetId,
    @NotBlank String modelName,
    @NotBlank String objective,
    String algorithm,
    @Min(1) @Max(20) Integer requestedRounds
) {
}
