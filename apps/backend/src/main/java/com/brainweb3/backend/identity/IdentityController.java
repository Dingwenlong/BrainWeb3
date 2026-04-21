package com.brainweb3.backend.identity;

import com.brainweb3.backend.auth.AppUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/identity")
public class IdentityController {

  private final IdentityService identityService;

  public IdentityController(IdentityService identityService) {
    this.identityService = identityService;
  }

  @GetMapping("/me")
  public ActorIdentityResponse me() {
    return identityService.getCurrentIdentity(requirePrincipal());
  }

  @GetMapping("/accounts/{actorId}")
  public ActorIdentityResponse actor(@PathVariable String actorId) {
    return identityService.getActorIdentity(requirePrincipal(), actorId);
  }

  @GetMapping("/organizations")
  public OrganizationIdentityResponse organization(@RequestParam("name") String name) {
    requirePrincipal();
    return identityService.getOrganizationIdentity(name);
  }

  @PatchMapping("/organizations/credential-status")
  public OrganizationIdentityResponse updateOrganizationCredentialStatus(
      @Valid @RequestBody UpdateOrganizationCredentialStatusRequest request
  ) {
    return identityService.updateOrganizationCredentialStatus(requirePrincipal(), request);
  }

  @PostMapping("/verify")
  @ResponseStatus(HttpStatus.OK)
  public CredentialVerificationResponse verify(@Valid @RequestBody VerifyCredentialRequest request) {
    requirePrincipal();
    return identityService.verify(request);
  }

  private AppUserPrincipal requirePrincipal() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated session is required.");
    }
    return principal;
  }
}
