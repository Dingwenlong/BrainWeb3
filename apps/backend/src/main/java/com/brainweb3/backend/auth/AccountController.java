package com.brainweb3.backend.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

  private final AccountService accountService;

  public AccountController(AccountService accountService) {
    this.accountService = accountService;
  }

  @GetMapping("/me")
  public AccountUserResponse me() {
    return accountService.getCurrentAccount(requirePrincipal());
  }

  @GetMapping
  public List<AccountUserResponse> list() {
    return accountService.listAccounts(requirePrincipal());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public AccountUserResponse create(@Valid @RequestBody CreateAccountRequest request) {
    return accountService.createAccount(requirePrincipal(), request);
  }

  @PatchMapping("/{actorId}")
  public AccountUserResponse update(@PathVariable String actorId, @Valid @RequestBody UpdateAccountRequest request) {
    return accountService.updateAccount(requirePrincipal(), actorId, request);
  }

  @PatchMapping("/{actorId}/credential-status")
  public AccountUserResponse updateCredentialStatus(
      @PathVariable String actorId,
      @Valid @RequestBody UpdateCredentialStatusRequest request
  ) {
    return accountService.updateCredentialStatus(requirePrincipal(), actorId, request);
  }

  @PostMapping("/{actorId}/reset-password")
  public AccountUserResponse resetPassword(@PathVariable String actorId, @Valid @RequestBody ResetPasswordRequest request) {
    return accountService.resetPassword(requirePrincipal(), actorId, request);
  }

  private AppUserPrincipal requirePrincipal() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated session is required.");
    }
    return principal;
  }
}
