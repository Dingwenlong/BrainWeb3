package com.brainweb3.backend.identity;

import com.brainweb3.backend.auth.AppUserEntity;
import com.brainweb3.backend.auth.AppUserPrincipal;
import com.brainweb3.backend.auth.AppUserRepository;
import com.brainweb3.backend.config.IdentityProperties;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IdentityService {

  private final AppUserRepository appUserRepository;
  private final IdentityProperties identityProperties;
  private final IdentityCredentialGovernanceService identityCredentialGovernanceService;

  public IdentityService(
      AppUserRepository appUserRepository,
      IdentityProperties identityProperties,
      IdentityCredentialGovernanceService identityCredentialGovernanceService
  ) {
    this.appUserRepository = appUserRepository;
    this.identityProperties = identityProperties;
    this.identityCredentialGovernanceService = identityCredentialGovernanceService;
  }

  @Transactional(readOnly = true)
  public ActorIdentityResponse getCurrentIdentity(AppUserPrincipal principal) {
    return toActorIdentity(loadUser(principal.actorId()));
  }

  @Transactional(readOnly = true)
  public ActorIdentityResponse getActorIdentity(AppUserPrincipal viewer, String actorId) {
    if (!viewer.actorId().equalsIgnoreCase(actorId) && !"admin".equalsIgnoreCase(viewer.actorRole())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admin can inspect other account identities.");
    }
    return toActorIdentity(loadUser(actorId));
  }

  public OrganizationIdentityResponse getOrganizationIdentity(String organizationName) {
    String normalizedName = normalize(organizationName);
    if (normalizedName.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Organization name is required.");
    }
    CredentialStatusSnapshot credentialStatus = identityCredentialGovernanceService.resolveOrganizationStatus(normalizedName);
    return new OrganizationIdentityResponse(
        normalizedName,
        buildOrganizationDid(normalizedName),
        buildOrganizationCredential(normalizedName, credentialStatus),
        credentialStatus,
        identityCredentialGovernanceService.getRecentHistory("organization", normalizedName, credentialStatus)
    );
  }

  public OrganizationIdentityResponse updateOrganizationCredentialStatus(
      AppUserPrincipal principal,
      UpdateOrganizationCredentialStatusRequest request
  ) {
    if (!"admin".equalsIgnoreCase(principal.actorRole())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role is required.");
    }
    identityCredentialGovernanceService.updateOrganizationStatus(
        principal,
        request.organizationName(),
        request.status(),
        request.reason()
    );
    return getOrganizationIdentity(request.organizationName());
  }

  public CredentialVerificationResponse verify(VerifyCredentialRequest request) {
    Instant now = Instant.now();
    if (request.expiresAt() != null && request.expiresAt().isBefore(now)) {
      return new CredentialVerificationResponse(false, "expired", "Credential has expired.");
    }
    String credentialStatus = normalize(request.credentialStatus()).toLowerCase(Locale.ROOT);
    if ("revoked".equals(credentialStatus)) {
      return new CredentialVerificationResponse(false, "revoked", "Credential has been revoked.");
    }
    if ("suspended".equals(credentialStatus)) {
      return new CredentialVerificationResponse(false, "suspended", "Credential is suspended and cannot be used right now.");
    }
    String expectedProof = signCredential(
        request.id(),
        request.type(),
        request.issuerDid(),
        request.holderDid(),
        request.subjectDid(),
        request.subjectType(),
        request.issuedAt(),
        request.expiresAt(),
        request.claims()
    );
    boolean verified = expectedProof.equals(request.proof());
    return new CredentialVerificationResponse(
        verified,
        verified ? "verified" : "invalid",
        verified ? "Credential proof matches the expected BrainWeb3 signature." : "Credential proof does not match the expected BrainWeb3 signature."
    );
  }

  public String buildActorDid(String actorId, String organization) {
    return "did:brainweb3:user:%s:%s".formatted(slugify(organization), slugify(actorId));
  }

  public String buildOrganizationDid(String organization) {
    return "did:brainweb3:org:%s".formatted(slugify(organization));
  }

  private ActorIdentityResponse toActorIdentity(AppUserEntity entity) {
    String actorDid = buildActorDid(entity.getId(), entity.getOrganization());
    String orgDid = buildOrganizationDid(entity.getOrganization());
    CredentialStatusSnapshot credentialStatus = identityCredentialGovernanceService.resolveActorStatus(entity);
    return new ActorIdentityResponse(
        entity.getId(),
        entity.getDisplayName(),
        entity.getRoleCode(),
        entity.getOrganization(),
        actorDid,
        orgDid,
        buildActorCredential(entity, actorDid, orgDid, credentialStatus)
    );
  }

  private VerifiableCredentialResponse buildActorCredential(
      AppUserEntity entity,
      String actorDid,
      String orgDid,
      CredentialStatusSnapshot credentialStatus
  ) {
    Instant issuedAt = entity.getCreatedAt();
    Instant expiresAt = issuedAt.plus(identityProperties.getCredentialTtlDays(), ChronoUnit.DAYS);
    Map<String, String> claims = new LinkedHashMap<>();
    claims.put("actorId", entity.getId());
    claims.put("displayName", entity.getDisplayName());
    claims.put("actorRole", entity.getRoleCode());
    claims.put("actorOrg", entity.getOrganization());
    claims.put("status", entity.getStatus());
    claims.put("organizationDid", orgDid);

    String id = "vc:brainweb3:account:%s".formatted(slugify(entity.getId()));
    String proof = signCredential(
        id,
        "BrainWeb3AccountCredential",
        identityProperties.getIssuerDid(),
        actorDid,
        actorDid,
        "account",
        issuedAt,
        expiresAt,
        claims
    );
    return new VerifiableCredentialResponse(
        id,
        "BrainWeb3AccountCredential",
        identityProperties.getIssuerDid(),
        actorDid,
        actorDid,
        "account",
        issuedAt,
        expiresAt,
        proof,
        credentialStatus.effectiveStatus(),
        "verified",
        claims
    );
  }

  private VerifiableCredentialResponse buildOrganizationCredential(
      String organizationName,
      CredentialStatusSnapshot credentialStatus
  ) {
    String orgDid = buildOrganizationDid(organizationName);
    Instant issuedAt = Instant.parse("2026-04-21T00:00:00Z");
    Instant expiresAt = issuedAt.plus(identityProperties.getCredentialTtlDays(), ChronoUnit.DAYS);
    Map<String, String> claims = new LinkedHashMap<>();
    claims.put("organizationName", organizationName);
    claims.put("organizationDid", orgDid);
    claims.put("assuranceLevel", "demo-trusted");

    String id = "vc:brainweb3:organization:%s".formatted(slugify(organizationName));
    String proof = signCredential(
        id,
        "BrainWeb3OrganizationCredential",
        identityProperties.getIssuerDid(),
        orgDid,
        orgDid,
        "organization",
        issuedAt,
        expiresAt,
        claims
    );
    return new VerifiableCredentialResponse(
        id,
        "BrainWeb3OrganizationCredential",
        identityProperties.getIssuerDid(),
        orgDid,
        orgDid,
        "organization",
        issuedAt,
        expiresAt,
        proof,
        credentialStatus.effectiveStatus(),
        "verified",
        claims
    );
  }

  private AppUserEntity loadUser(String actorId) {
    return appUserRepository.findById(normalize(actorId).toLowerCase(Locale.ROOT))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found."));
  }

  private String signCredential(
      String id,
      String type,
      String issuerDid,
      String holderDid,
      String subjectDid,
      String subjectType,
      Instant issuedAt,
      Instant expiresAt,
      Map<String, String> claims
  ) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(identityProperties.getCredentialSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] digest = mac.doFinal(
          canonicalize(id, type, issuerDid, holderDid, subjectDid, subjectType, issuedAt, expiresAt, claims)
              .getBytes(StandardCharsets.UTF_8)
      );
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to sign verifiable credential.", exception);
    }
  }

  private String canonicalize(
      String id,
      String type,
      String issuerDid,
      String holderDid,
      String subjectDid,
      String subjectType,
      Instant issuedAt,
      Instant expiresAt,
      Map<String, String> claims
  ) {
    StringBuilder builder = new StringBuilder();
    builder.append(normalize(id)).append('|')
        .append(normalize(type)).append('|')
        .append(normalize(issuerDid)).append('|')
        .append(normalize(holderDid)).append('|')
        .append(normalize(subjectDid)).append('|')
        .append(normalize(subjectType)).append('|')
        .append(issuedAt == null ? "" : issuedAt.toString()).append('|')
        .append(expiresAt == null ? "" : expiresAt.toString());
    if (claims != null) {
      claims.entrySet().stream()
          .sorted(Map.Entry.comparingByKey())
          .forEach(entry -> builder.append('|').append(entry.getKey()).append('=').append(normalize(entry.getValue())));
    }
    return builder.toString();
  }

  private String slugify(String input) {
    return normalize(input).toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", "-")
        .replaceAll("^-|-$", "");
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim();
  }
}
