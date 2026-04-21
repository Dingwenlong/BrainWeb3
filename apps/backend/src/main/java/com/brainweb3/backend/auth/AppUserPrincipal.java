package com.brainweb3.backend.auth;

import com.brainweb3.backend.access.ActorContext;

public record AppUserPrincipal(
    String actorId,
    String displayName,
    String actorRole,
    String actorOrg,
    String status
) {
  public static AppUserPrincipal fromEntity(AppUserEntity entity) {
    return new AppUserPrincipal(
        entity.getId(),
        entity.getDisplayName(),
        entity.getRoleCode(),
        entity.getOrganization(),
        entity.getStatus()
    );
  }

  public ActorContext toActorContext() {
    return new ActorContext(actorId, actorRole, actorOrg);
  }
}
