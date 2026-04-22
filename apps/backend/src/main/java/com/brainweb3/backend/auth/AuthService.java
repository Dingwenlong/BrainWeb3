package com.brainweb3.backend.auth;

import com.brainweb3.backend.access.ActorContext;
import com.brainweb3.backend.audit.AuditService;
import com.brainweb3.backend.config.RuntimeSecurityGuardrails;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

  private final AppUserRepository appUserRepository;
  private final PasswordEncoder passwordEncoder;
  private final AuthProperties authProperties;
  private final JwtService jwtService;
  private final AuditService auditService;
  private final RefreshTokenService refreshTokenService;
  private final PasswordResetService passwordResetService;
  private final String stage;

  public AuthService(
      AppUserRepository appUserRepository,
      PasswordEncoder passwordEncoder,
      AuthProperties authProperties,
      JwtService jwtService,
      AuditService auditService,
      RefreshTokenService refreshTokenService,
      PasswordResetService passwordResetService,
      @Value("${brainweb3.stage:bootstrap}") String stage
  ) {
    this.appUserRepository = appUserRepository;
    this.passwordEncoder = passwordEncoder;
    this.authProperties = authProperties;
    this.jwtService = jwtService;
    this.auditService = auditService;
    this.refreshTokenService = refreshTokenService;
    this.passwordResetService = passwordResetService;
    this.stage = stage;
  }

  @Transactional
  public AuthSessionResponse login(LoginRequest request) {
    AppUserEntity user = appUserRepository.findById(normalize(request.actorId()))
        .orElseThrow(this::invalidCredentials);

    if (!"active".equalsIgnoreCase(user.getStatus())) {
      throw invalidCredentials();
    }
    if (!authProperties.isAllowDemoPasswordLogin()
        && authProperties.getDemoPassword().equals(request.password())) {
      throw invalidCredentials();
    }
    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw invalidCredentials();
    }

    Instant now = Instant.now();
    user.setLastLoginAt(now);
    user.setUpdatedAt(now);

    AppUserPrincipal principal = AppUserPrincipal.fromEntity(user);
    auditService.record(null, principal.toActorContext(), "AUTH_LOGIN_SUCCEEDED", "success", "JWT session issued.");
    return issueSession(principal);
  }

  @Transactional
  public AuthSessionResponse register(RegisterRequest request) {
    String actorId = normalize(request.actorId()).toLowerCase();
    if (actorId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Actor ID is required.");
    }
    if (appUserRepository.existsById(actorId)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Actor ID already exists.");
    }

    Instant now = Instant.now();
    AppUserEntity user = new AppUserEntity();
    user.setId(actorId);
    user.setDisplayName(request.displayName().trim());
    user.setPasswordHash(passwordEncoder.encode(request.password()));
    user.setRoleCode("researcher");
    user.setOrganization(request.actorOrg().trim());
    user.setStatus("active");
    user.setCreatedAt(now);
    user.setUpdatedAt(now);
    user.setPasswordChangedAt(now);
    user.setLastLoginAt(now);
    appUserRepository.save(user);

    AppUserPrincipal principal = AppUserPrincipal.fromEntity(user);
    auditService.record(null, principal.toActorContext(), "ACCOUNT_REGISTERED", "success", "Self-service account created.");
    return issueSession(principal);
  }

  @Transactional
  public AuthSessionResponse refresh(RefreshTokenRequest request) {
    RefreshTokenRotation rotation = refreshTokenService.rotate(request.refreshToken());
    AppUserEntity user = appUserRepository.findById(rotation.userId())
        .orElseThrow(this::invalidCredentials);
    if (!"active".equalsIgnoreCase(user.getStatus())) {
      throw invalidCredentials();
    }

    AppUserPrincipal principal = AppUserPrincipal.fromEntity(user);
    auditService.record(null, principal.toActorContext(), "AUTH_REFRESH_SUCCEEDED", "success", "Refresh token rotated.");
    return issueSession(principal, rotation.refreshToken(), rotation.refreshExpiresAt());
  }

  @Transactional
  public void changePassword(AppUserPrincipal principal, ChangePasswordRequest request) {
    AppUserEntity user = appUserRepository.findById(principal.actorId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found."));
    if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect.");
    }

    Instant now = Instant.now();
    user.setPasswordHash(passwordEncoder.encode(request.nextPassword()));
    user.setPasswordChangedAt(now);
    user.setUpdatedAt(now);
    refreshTokenService.revokeAllActiveTokens(principal.actorId());
    auditService.record(null, principal.toActorContext(), "ACCOUNT_PASSWORD_CHANGED", "success", "Password updated by current user.");
  }

  @Transactional
  public PasswordResetTicketResponse requestPasswordReset(ForgotPasswordRequest request) {
    String actorId = normalize(request.actorId()).toLowerCase();
    boolean productionLike = RuntimeSecurityGuardrails.requiresProductionGuardrails(stage);
    AppUserEntity user = appUserRepository.findById(actorId).orElse(null);
    if (user == null || !"active".equalsIgnoreCase(user.getStatus())) {
      if (productionLike) {
        return hiddenPasswordResetTicket(actorId);
      }
      throw new ResponseStatusException(
          user == null ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST,
          user == null ? "Account not found." : "Account is disabled."
      );
    }

    PasswordResetTicketIssue ticketIssue = passwordResetService.issueTicket(user.getId());
    auditService.record(
        null,
        new ActorContext("anonymous", "guest", "public"),
        "ACCOUNT_PASSWORD_RESET_REQUESTED",
        "success",
        "Password reset ticket issued for " + user.getId() + "."
    );
    if (productionLike) {
      return new PasswordResetTicketResponse(user.getId(), null, ticketIssue.expiresAt(), "out-of-band", false);
    }
    return new PasswordResetTicketResponse(user.getId(), ticketIssue.resetToken(), ticketIssue.expiresAt(), "inline-demo", true);
  }

  @Transactional
  public void confirmPasswordReset(ConfirmPasswordResetRequest request) {
    String userId = passwordResetService.consumeTicket(request.resetToken());
    AppUserEntity user = appUserRepository.findById(userId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found."));

    Instant now = Instant.now();
    user.setPasswordHash(passwordEncoder.encode(request.nextPassword()));
    user.setPasswordChangedAt(now);
    user.setUpdatedAt(now);
    refreshTokenService.revokeAllActiveTokens(userId);

    AppUserPrincipal principal = AppUserPrincipal.fromEntity(user);
    auditService.record(null, principal.toActorContext(), "ACCOUNT_PASSWORD_RESET_CONFIRMED", "success", "Password reset ticket consumed.");
  }

  public AuthActorResponse getSessionActor(AppUserPrincipal principal) {
    return toActorResponse(principal);
  }

  private AuthActorResponse toActorResponse(AppUserPrincipal principal) {
    return new AuthActorResponse(
        principal.actorId(),
        principal.actorRole(),
        principal.actorOrg(),
        principal.displayName()
    );
  }

  private AuthSessionResponse issueSession(AppUserPrincipal principal) {
    RefreshTokenIssue refreshTokenIssue = refreshTokenService.issueToken(principal.actorId());
    return issueSession(principal, refreshTokenIssue.refreshToken(), refreshTokenIssue.expiresAt());
  }

  private AuthSessionResponse issueSession(
      AppUserPrincipal principal,
      String refreshToken,
      Instant refreshExpiresAt
  ) {
    JwtSessionToken jwtSessionToken = jwtService.issueToken(principal);
    return new AuthSessionResponse(
        jwtSessionToken.token(),
        jwtSessionToken.expiresAt(),
        refreshToken,
        refreshExpiresAt,
        toActorResponse(principal)
    );
  }

  private ResponseStatusException invalidCredentials() {
    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Actor ID or password is invalid.");
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim();
  }

  private PasswordResetTicketResponse hiddenPasswordResetTicket(String actorId) {
    return new PasswordResetTicketResponse(
        actorId,
        null,
        Instant.now().plus(authProperties.getPasswordResetTtlMinutes(), ChronoUnit.MINUTES),
        "out-of-band",
        false
    );
  }
}
