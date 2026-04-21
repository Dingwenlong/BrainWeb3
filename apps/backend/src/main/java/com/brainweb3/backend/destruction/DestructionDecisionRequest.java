package com.brainweb3.backend.destruction;

import jakarta.validation.constraints.NotBlank;

public record DestructionDecisionRequest(
    @NotBlank String policy
) {
}
