package com.brainweb3.backend.identity;

import com.brainweb3.backend.auth.AppUserEntity;
import com.brainweb3.backend.auth.AppUserPrincipal;
import com.brainweb3.backend.auth.AppUserRepository;
import com.brainweb3.backend.config.IdentityProperties;
import com.brainweb3.backend.audit.AuditService;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class IdentityCredentialGovernanceService {

  private static final String SUBJECT_TYPE_ACCOUNT = "account";
  private static final String SUBJECT_TYPE_ORGANIZATION = "organization";

  private final IdentityCredentialHistoryRepository identityCredentialHistoryRepository;
  private final IdentityCredentialStatusRepository identityCredentialStatusRepository;
  private final AppUserRepository appUserRepository;
  private final AuditService auditService;
  private final Set<String> revokedActors;
  private final Set<String> revokedOrganizations;

  public IdentityCredentialGovernanceService(
      IdentityCredentialHistoryRepository identityCredentialHistoryRepository,
      IdentityCredentialStatusRepository identityCredentialStatusRepository,
      AppUserRepository appUserRepository,
      AuditService auditService,
      IdentityProperties identityProperties
  ) {
    this.identityCredentialHistoryRepository = identityCredentialHistoryRepository;
    this.identityCredentialStatusRepository = identityCredentialStatusRepository;
    this.appUserRepository = appUserRepository;
    this.auditService = auditService;
    this.revokedActors = normalizeSet(identityProperties.getRevokedActors());
    this.revokedOrganizations = normalizeSet(identityProperties.getRevokedOrganizations());
  }

  @Transactional(readOnly = true)
  public CredentialStatusSnapshot resolveActorStatus(AppUserEntity entity) {
    IdentityCredentialStatusEntity manualState = identityCredentialStatusRepository
        .findBySubjectTypeIgnoreCaseAndSubjectKeyIgnoreCase(SUBJECT_TYPE_ACCOUNT, entity.getId())
        .orElse(null);

    if (manualState != null) {
      String manualStatus = normalize(manualState.getCredentialStatus()).toLowerCase(Locale.ROOT);
      if ("revoked".equals(manualStatus)) {
        return toManualSnapshot(manualState);
      }
      if (!"active".equalsIgnoreCase(entity.getStatus())) {
        return new CredentialStatusSnapshot(
            "suspended",
            "derived",
            "Account is disabled, so the credential stays suspended.",
            manualState.getUpdatedBy(),
            manualState.getUpdatedAt()
        );
      }
      return toManualSnapshot(manualState);
    }

    String normalizedActor = normalize(entity.getId()).toLowerCase(Locale.ROOT);
    String normalizedOrg = normalize(entity.getOrganization()).toLowerCase(Locale.ROOT);
    if (revokedActors.contains(normalizedActor) || revokedOrganizations.contains(normalizedOrg)) {
      return new CredentialStatusSnapshot(
          "revoked",
          "legacy-config",
          "Credential is revoked by legacy identity configuration.",
          "system",
          null
      );
    }
    if (!"active".equalsIgnoreCase(entity.getStatus())) {
      return new CredentialStatusSnapshot(
          "suspended",
          "derived",
          "Account is disabled, so the credential is suspended.",
          "system",
          null
      );
    }
    return new CredentialStatusSnapshot(
        "issued",
        "derived",
        "Credential is active because the account is enabled.",
        "system",
        null
    );
  }

  @Transactional(readOnly = true)
  public CredentialStatusSnapshot resolveOrganizationStatus(String organizationName) {
    IdentityCredentialStatusEntity manualState = identityCredentialStatusRepository
        .findBySubjectTypeIgnoreCaseAndSubjectKeyIgnoreCase(SUBJECT_TYPE_ORGANIZATION, organizationName)
        .orElse(null);

    if (manualState != null) {
      String manualStatus = normalize(manualState.getCredentialStatus()).toLowerCase(Locale.ROOT);
      if ("revoked".equals(manualStatus)) {
        return toManualSnapshot(manualState);
      }
      boolean hasActiveUsers = appUserRepository.existsByOrganizationIgnoreCaseAndStatusIgnoreCase(organizationName, "active");
      if (!hasActiveUsers) {
        return new CredentialStatusSnapshot(
            "suspended",
            "derived",
            "Organization has no active accounts, so the credential stays suspended.",
            manualState.getUpdatedBy(),
            manualState.getUpdatedAt()
        );
      }
      return toManualSnapshot(manualState);
    }

    String normalized = normalize(organizationName).toLowerCase(Locale.ROOT);
    if (revokedOrganizations.contains(normalized)) {
      return new CredentialStatusSnapshot(
          "revoked",
          "legacy-config",
          "Organization credential is revoked by legacy identity configuration.",
          "system",
          null
      );
    }
    boolean hasActiveUsers = appUserRepository.existsByOrganizationIgnoreCaseAndStatusIgnoreCase(organizationName, "active");
    if (!hasActiveUsers) {
      return new CredentialStatusSnapshot(
          "suspended",
          "derived",
          "Organization has no active accounts, so the credential is suspended.",
          "system",
          null
      );
    }
    return new CredentialStatusSnapshot(
        "issued",
        "derived",
        "Organization credential is active because the organization still has active accounts.",
        "system",
        null
    );
  }

  @Transactional
  public CredentialStatusSnapshot updateOrganizationStatus(
      AppUserPrincipal principal,
      String organizationName,
      String requestedStatus,
      String reason
  ) {
    String normalizedOrganization = normalize(organizationName);
    CredentialStatusSnapshot currentSnapshot = resolveOrganizationStatus(normalizedOrganization);
    if (normalizedOrganization.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Organization name is required.");
    }
    String normalizedStatus = normalize(requestedStatus).toLowerCase(Locale.ROOT);
    if (!List.of("issued", "suspended", "revoked").contains(normalizedStatus)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported credential status.");
    }
    boolean hasActiveUsers = appUserRepository.existsByOrganizationIgnoreCaseAndStatusIgnoreCase(normalizedOrganization, "active");
    if ("issued".equals(normalizedStatus) && !hasActiveUsers) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Organization without active accounts cannot be marked as issued.");
    }

    IdentityCredentialStatusEntity manualState = identityCredentialStatusRepository
        .findBySubjectTypeIgnoreCaseAndSubjectKeyIgnoreCase(SUBJECT_TYPE_ORGANIZATION, normalizedOrganization)
        .orElseGet(IdentityCredentialStatusEntity::new);
    manualState.setSubjectType(SUBJECT_TYPE_ORGANIZATION);
    manualState.setSubjectKey(normalizedOrganization);
    manualState.setCredentialStatus(normalizedStatus);
    manualState.setReason(resolveOrganizationReason(normalizedStatus, reason, normalizedOrganization));
    manualState.setUpdatedBy(principal.actorId());
    manualState.setUpdatedAt(Instant.now());
    identityCredentialStatusRepository.save(manualState);
    recordHistory(
        SUBJECT_TYPE_ORGANIZATION,
        normalizedOrganization,
        currentSnapshot.effectiveStatus(),
        normalizedStatus,
        "manual",
        manualState.getReason(),
        principal.actorId(),
        manualState.getUpdatedAt()
    );

    auditService.record(
        null,
        principal.toActorContext(),
        "ORGANIZATION_CREDENTIAL_STATUS_UPDATED",
        "success",
        "Updated organization credential status for %s to %s.".formatted(normalizedOrganization, normalizedStatus)
    );
    return resolveOrganizationStatus(normalizedOrganization);
  }

  @Transactional
  public CredentialStatusSnapshot updateActorStatus(
      AppUserPrincipal principal,
      AppUserEntity entity,
      String requestedStatus,
      String reason
  ) {
    CredentialStatusSnapshot currentSnapshot = resolveActorStatus(entity);
    String normalizedStatus = normalize(requestedStatus).toLowerCase(Locale.ROOT);
    if (!List.of("issued", "suspended", "revoked").contains(normalizedStatus)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported credential status.");
    }
    if (principal.actorId().equalsIgnoreCase(entity.getId())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Admin cannot change their own credential status here.");
    }
    if ("issued".equals(normalizedStatus) && !"active".equalsIgnoreCase(entity.getStatus())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Disabled account cannot be marked as issued.");
    }

    IdentityCredentialStatusEntity manualState = identityCredentialStatusRepository
        .findBySubjectTypeIgnoreCaseAndSubjectKeyIgnoreCase(SUBJECT_TYPE_ACCOUNT, entity.getId())
        .orElseGet(IdentityCredentialStatusEntity::new);
    manualState.setSubjectType(SUBJECT_TYPE_ACCOUNT);
    manualState.setSubjectKey(entity.getId());
    manualState.setCredentialStatus(normalizedStatus);
    manualState.setReason(resolveReason(normalizedStatus, reason, entity));
    manualState.setUpdatedBy(principal.actorId());
    manualState.setUpdatedAt(Instant.now());
    identityCredentialStatusRepository.save(manualState);
    recordHistory(
        SUBJECT_TYPE_ACCOUNT,
        entity.getId(),
        currentSnapshot.effectiveStatus(),
        normalizedStatus,
        "manual",
        manualState.getReason(),
        principal.actorId(),
        manualState.getUpdatedAt()
    );

    auditService.record(
        null,
        principal.toActorContext(),
        "ACCOUNT_CREDENTIAL_STATUS_UPDATED",
        "success",
        "Updated credential status for %s to %s.".formatted(entity.getId(), normalizedStatus)
    );
    return resolveActorStatus(entity);
  }

  @Transactional(readOnly = true)
  public java.util.List<CredentialHistoryEntryResponse> getRecentHistory(
      String subjectType,
      String subjectKey,
      CredentialStatusSnapshot currentSnapshot
  ) {
    java.util.List<CredentialHistoryEntryResponse> history = identityCredentialHistoryRepository
        .findTop5BySubjectTypeIgnoreCaseAndSubjectKeyIgnoreCaseOrderByCreatedAtDesc(subjectType, subjectKey)
        .stream()
        .map(this::toHistoryResponse)
        .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));

    if (shouldPrependCurrentSnapshot(history, currentSnapshot)) {
      history.add(0, new CredentialHistoryEntryResponse(
          null,
          null,
          currentSnapshot.effectiveStatus(),
          currentSnapshot.source(),
          currentSnapshot.reason(),
          currentSnapshot.updatedBy(),
          currentSnapshot.updatedAt()
      ));
    }
    return history;
  }

  private CredentialStatusSnapshot toManualSnapshot(IdentityCredentialStatusEntity entity) {
    return new CredentialStatusSnapshot(
        normalize(entity.getCredentialStatus()).toLowerCase(Locale.ROOT),
        "manual",
        normalize(entity.getReason()),
        entity.getUpdatedBy(),
        entity.getUpdatedAt()
    );
  }

  private String resolveReason(String status, String reason, AppUserEntity entity) {
    String normalizedReason = normalize(reason);
    if (!normalizedReason.isBlank()) {
      return normalizedReason;
    }
    return switch (status) {
      case "issued" -> "Credential re-issued for %s.".formatted(entity.getId());
      case "suspended" -> "Credential suspended for operational review.";
      case "revoked" -> "Credential revoked by platform governance.";
      default -> "Credential status updated.";
    };
  }

  private String resolveOrganizationReason(String status, String reason, String organizationName) {
    String normalizedReason = normalize(reason);
    if (!normalizedReason.isBlank()) {
      return normalizedReason;
    }
    return switch (status) {
      case "issued" -> "Credential re-issued for organization %s.".formatted(organizationName);
      case "suspended" -> "Organization credential suspended for governance review.";
      case "revoked" -> "Organization credential revoked by platform governance.";
      default -> "Organization credential status updated.";
    };
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim();
  }

  private boolean shouldPrependCurrentSnapshot(
      java.util.List<CredentialHistoryEntryResponse> history,
      CredentialStatusSnapshot currentSnapshot
  ) {
    if (history.isEmpty()) {
      return true;
    }
    CredentialHistoryEntryResponse latest = history.get(0);
    return !normalize(latest.nextStatus()).equalsIgnoreCase(currentSnapshot.effectiveStatus())
        || !normalize(latest.source()).equalsIgnoreCase(currentSnapshot.source());
  }

  private void recordHistory(
      String subjectType,
      String subjectKey,
      String previousStatus,
      String nextStatus,
      String source,
      String reason,
      String updatedBy,
      Instant createdAt
  ) {
    IdentityCredentialHistoryEntity entity = new IdentityCredentialHistoryEntity();
    entity.setSubjectType(subjectType);
    entity.setSubjectKey(subjectKey);
    entity.setPreviousStatus(previousStatus);
    entity.setNextStatus(nextStatus);
    entity.setSource(source);
    entity.setReason(reason);
    entity.setUpdatedBy(updatedBy);
    entity.setCreatedAt(createdAt);
    identityCredentialHistoryRepository.save(entity);
  }

  private CredentialHistoryEntryResponse toHistoryResponse(IdentityCredentialHistoryEntity entity) {
    return new CredentialHistoryEntryResponse(
        entity.getId(),
        entity.getPreviousStatus(),
        entity.getNextStatus(),
        entity.getSource(),
        entity.getReason(),
        entity.getUpdatedBy(),
        entity.getCreatedAt()
    );
  }

  private Set<String> normalizeSet(List<String> values) {
    if (values == null) {
      return Set.of();
    }
    return values.stream()
        .map(this::normalize)
        .map(value -> value.toLowerCase(Locale.ROOT))
        .filter(value -> !value.isBlank())
        .collect(Collectors.toSet());
  }
}
