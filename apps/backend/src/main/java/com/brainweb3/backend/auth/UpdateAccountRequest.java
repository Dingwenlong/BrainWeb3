package com.brainweb3.backend.auth;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateAccountRequest(
    @Size(min = 2, max = 120) String displayName,
    @Pattern(regexp = "researcher|owner|approver|admin") String actorRole,
    @Size(min = 2, max = 160) String actorOrg,
    @Pattern(regexp = "active|disabled") String status
) {
}
