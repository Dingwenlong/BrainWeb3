package com.brainweb3.backend.access;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ActorContextResolver {

  public ActorContext resolveRequired(HttpServletRequest request) {
    String actorId = normalize(request.getHeader("X-Actor-Id"));
    String actorRole = normalize(request.getHeader("X-Actor-Role"));
    String actorOrg = normalize(request.getHeader("X-Actor-Org"));

    if (actorId.isBlank() || actorRole.isBlank() || actorOrg.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED,
          "X-Actor-Id, X-Actor-Role, and X-Actor-Org headers are required."
      );
    }

    return new ActorContext(actorId, actorRole, actorOrg);
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim();
  }
}
