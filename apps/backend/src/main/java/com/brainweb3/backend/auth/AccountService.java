package com.brainweb3.backend.auth;

import com.brainweb3.backend.audit.AuditService;
import com.brainweb3.backend.identity.CredentialStatusSnapshot;
import com.brainweb3.backend.identity.IdentityCredentialGovernanceService;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountService {

  private final AppUserRepository appUserRepository;
  private final PasswordEncoder passwordEncoder;
  private final AuditService auditService;
  private final RefreshTokenService refreshTokenService;
  private final IdentityCredentialGovernanceService identityCredentialGovernanceService;

  public AccountService(
      AppUserRepository appUserRepository,
      PasswordEncoder passwordEncoder,
      AuditService auditService,
      RefreshTokenService refreshTokenService,
      IdentityCredentialGovernanceService identityCredentialGovernanceService
  ) {
    this.appUserRepository = appUserRepository;
    this.passwordEncoder = passwordEncoder;
    this.auditService = auditService;
    this.refreshTokenService = refreshTokenService;
    this.identityCredentialGovernanceService = identityCredentialGovernanceService;
  }

  @Transactional(readOnly = true)
  public AccountUserResponse getCurrentAccount(AppUserPrincipal principal) {
    AppUserEntity entity = appUserRepository.findById(principal.actorId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found."));
    return toResponse(entity);
  }

  @Transactional(readOnly = true)
  public List<AccountUserResponse> listAccounts(AppUserPrincipal principal) {
    requireAdmin(principal);
    return appUserRepository.findAllByOrderByCreatedAtDesc().stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public AccountUserResponse createAccount(AppUserPrincipal principal, CreateAccountRequest request) {
    requireAdmin(principal);
    String actorId = normalize(request.actorId()).toLowerCase();
    if (appUserRepository.existsById(actorId)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Actor ID already exists.");
    }

    Instant now = Instant.now();
    AppUserEntity entity = new AppUserEntity();
    entity.setId(actorId);
    entity.setDisplayName(request.displayName().trim());
    entity.setRoleCode(normalizeRole(request.actorRole()));
    entity.setOrganization(request.actorOrg().trim());
    entity.setStatus(normalizeStatus(request.status()));
    entity.setPasswordHash(passwordEncoder.encode(request.password()));
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    entity.setPasswordChangedAt(now);
    appUserRepository.save(entity);

    auditService.record(null, principal.toActorContext(), "ACCOUNT_CREATED", "success", "Created account " + actorId + ".");
    return toResponse(entity);
  }

  @Transactional
  public AccountUserResponse updateAccount(AppUserPrincipal principal, String actorId, UpdateAccountRequest request) {
    requireAdmin(principal);
    AppUserEntity entity = appUserRepository.findById(normalize(actorId))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found."));
    String nextRole = request.actorRole() != null && !request.actorRole().isBlank()
        ? normalizeRole(request.actorRole())
        : entity.getRoleCode();
    String nextStatus = request.status() != null && !request.status().isBlank()
        ? normalizeStatus(request.status())
        : entity.getStatus();

    if (principal.actorId().equalsIgnoreCase(entity.getId())) {
      if (!"admin".equalsIgnoreCase(nextRole)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Admin cannot remove their own admin role.");
      }
      if (!"active".equalsIgnoreCase(nextStatus)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Admin cannot disable their own account.");
      }
    }
    ensureAdminSurvives(entity, nextRole, nextStatus);

    if (request.displayName() != null && !request.displayName().isBlank()) {
      entity.setDisplayName(request.displayName().trim());
    }
    if (request.actorRole() != null && !request.actorRole().isBlank()) {
      entity.setRoleCode(nextRole);
    }
    if (request.actorOrg() != null && !request.actorOrg().isBlank()) {
      entity.setOrganization(request.actorOrg().trim());
    }
    if (request.status() != null && !request.status().isBlank()) {
      entity.setStatus(nextStatus);
    }
    entity.setUpdatedAt(Instant.now());
    refreshTokenService.revokeAllActiveTokens(entity.getId());

    auditService.record(null, principal.toActorContext(), "ACCOUNT_UPDATED", "success", "Updated account " + entity.getId() + ".");
    return toResponse(entity);
  }

  @Transactional
  public AccountUserResponse updateCredentialStatus(
      AppUserPrincipal principal,
      String actorId,
      UpdateCredentialStatusRequest request
  ) {
    requireAdmin(principal);
    AppUserEntity entity = appUserRepository.findById(normalize(actorId))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found."));
    identityCredentialGovernanceService.updateActorStatus(principal, entity, request.status(), request.reason());
    return toResponse(entity);
  }

  @Transactional
  public AccountUserResponse resetPassword(AppUserPrincipal principal, String actorId, ResetPasswordRequest request) {
    requireAdmin(principal);
    AppUserEntity entity = appUserRepository.findById(normalize(actorId))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found."));
    if (principal.actorId().equalsIgnoreCase(entity.getId())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use self-service change password for your own account.");
    }
    Instant now = Instant.now();
    entity.setPasswordHash(passwordEncoder.encode(request.nextPassword()));
    entity.setPasswordChangedAt(now);
    entity.setUpdatedAt(now);
    refreshTokenService.revokeAllActiveTokens(entity.getId());

    auditService.record(null, principal.toActorContext(), "ACCOUNT_PASSWORD_RESET", "success", "Reset password for " + entity.getId() + ".");
    return toResponse(entity);
  }

  private AccountUserResponse toResponse(AppUserEntity entity) {
    CredentialStatusSnapshot credentialStatus = identityCredentialGovernanceService.resolveActorStatus(entity);
    return new AccountUserResponse(
        entity.getId(),
        entity.getDisplayName(),
        entity.getRoleCode(),
        entity.getOrganization(),
        entity.getStatus(),
        credentialStatus,
        identityCredentialGovernanceService.getRecentHistory("account", entity.getId(), credentialStatus),
        entity.getCreatedAt(),
        entity.getUpdatedAt(),
        entity.getPasswordChangedAt(),
        entity.getLastLoginAt()
    );
  }

  private void requireAdmin(AppUserPrincipal principal) {
    if (!"admin".equalsIgnoreCase(principal.actorRole())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role is required.");
    }
  }

  private void ensureAdminSurvives(AppUserEntity entity, String nextRole, String nextStatus) {
    boolean adminWillLoseProtection = "admin".equalsIgnoreCase(entity.getRoleCode())
        && (!"admin".equalsIgnoreCase(nextRole) || !"active".equalsIgnoreCase(nextStatus));
    if (!adminWillLoseProtection) {
      return;
    }

    long remainingAdmins = appUserRepository.countByRoleCodeIgnoreCaseAndStatusIgnoreCaseAndIdNot("admin", "active", entity.getId());
    if (remainingAdmins == 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one active admin account must remain.");
    }
  }

  private String normalizeRole(String value) {
    return normalize(value).toLowerCase();
  }

  private String normalizeStatus(String value) {
    return normalize(value).toLowerCase();
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim();
  }
}
