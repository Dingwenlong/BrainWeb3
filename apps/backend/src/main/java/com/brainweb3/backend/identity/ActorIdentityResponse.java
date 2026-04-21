package com.brainweb3.backend.identity;

public record ActorIdentityResponse(
    String actorId,
    String displayName,
    String actorRole,
    String actorOrg,
    String actorDid,
    String organizationDid,
    VerifiableCredentialResponse credential
) {
}
