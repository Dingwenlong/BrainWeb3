package com.brainweb3.backend.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmPasswordResetRequest(
    @NotBlank @Size(min = 20, max = 200) String resetToken,
    @NotBlank @Size(min = 8, max = 72) String nextPassword
) {
}
