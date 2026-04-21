package com.brainweb3.backend.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/login")
  public AuthSessionResponse login(@Valid @RequestBody LoginRequest request) {
    return authService.login(request);
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public AuthSessionResponse register(@Valid @RequestBody RegisterRequest request) {
    return authService.register(request);
  }

  @PostMapping("/refresh")
  public AuthSessionResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
    return authService.refresh(request);
  }

  @GetMapping("/session")
  @ResponseStatus(HttpStatus.OK)
  public AuthActorResponse session() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated session is required.");
    }
    return authService.getSessionActor(principal);
  }

  @PostMapping("/change-password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated session is required.");
    }
    authService.changePassword(principal, request);
  }

  @PostMapping("/password-reset/request")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public PasswordResetTicketResponse requestPasswordReset(@Valid @RequestBody ForgotPasswordRequest request) {
    return authService.requestPasswordReset(request);
  }

  @PostMapping("/password-reset/confirm")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void confirmPasswordReset(@Valid @RequestBody ConfirmPasswordResetRequest request) {
    authService.confirmPasswordReset(request);
  }
}
