package com.brainweb3.backend.access;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import com.brainweb3.backend.auth.AppUserPrincipal;

@Component
public class ActorContextResolver {

  public ActorContext resolveRequired(HttpServletRequest request) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal) {
      return principal.toActorContext();
    }

    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated session is required.");
  }
}
