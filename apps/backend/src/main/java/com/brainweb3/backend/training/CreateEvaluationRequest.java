package com.brainweb3.backend.training;

import jakarta.validation.constraints.NotBlank;

public record CreateEvaluationRequest(
    @NotBlank String testSetHash,
    @NotBlank String evalScriptHash,
    @NotBlank String metricsJson,
    String notes
) {
}
