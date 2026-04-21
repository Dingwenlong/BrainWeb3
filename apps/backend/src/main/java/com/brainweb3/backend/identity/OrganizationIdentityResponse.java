package com.brainweb3.backend.identity;

public record OrganizationIdentityResponse(
    String organizationName,
    String organizationDid,
    VerifiableCredentialResponse credential,
    CredentialStatusSnapshot statusSnapshot,
    java.util.List<CredentialHistoryEntryResponse> credentialHistory
) {
}
