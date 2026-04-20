package com.brainweb3.backend.access;

public record ActorContext(
    String actorId,
    String actorRole,
    String actorOrg
) {
  public boolean hasRole(String role) {
    return actorRole != null && actorRole.equalsIgnoreCase(role);
  }

  public boolean belongsTo(String organization) {
    return actorOrg != null && organization != null && actorOrg.equalsIgnoreCase(organization);
  }
}
