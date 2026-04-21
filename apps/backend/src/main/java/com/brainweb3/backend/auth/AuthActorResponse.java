package com.brainweb3.backend.auth;

public record AuthActorResponse(
    String actorId,
    String actorRole,
    String actorOrg,
    String displayName
) {
}
