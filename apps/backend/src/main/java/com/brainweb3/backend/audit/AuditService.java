package com.brainweb3.backend.audit;

import com.brainweb3.backend.access.ActorContext;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

  private final AuditEventRepository auditEventRepository;

  public AuditService(AuditEventRepository auditEventRepository) {
    this.auditEventRepository = auditEventRepository;
  }

  @Transactional
  public void record(
      String datasetId,
      ActorContext actor,
      String action,
      String status,
      String detail
  ) {
    AuditEventEntity event = new AuditEventEntity();
    event.setDatasetId(datasetId);
    event.setActorId(actor.actorId());
    event.setActorRole(actor.actorRole());
    event.setActorOrg(actor.actorOrg());
    event.setAction(action);
    event.setStatus(status);
    event.setDetail(detail);
    event.setCreatedAt(Instant.now());
    auditEventRepository.save(event);
  }

  @Transactional(readOnly = true)
  public List<AuditEventResponse> listEvents(
      ActorContext viewer,
      String datasetId,
      String actorId,
      String action,
      String status,
      String actorOrg
  ) {
    return auditEventRepository.findAllByOrderByCreatedAtDesc().stream()
        .filter(event -> isVisibleTo(viewer, event))
        .filter(event -> matchesIgnoreCase(datasetId, event.getDatasetId()))
        .filter(event -> matchesIgnoreCase(actorId, event.getActorId()))
        .filter(event -> matchesIgnoreCase(action, event.getAction()))
        .filter(event -> matchesIgnoreCase(status, event.getStatus()))
        .filter(event -> matchesIgnoreCase(actorOrg, event.getActorOrg()))
        .map(this::toResponse)
        .toList();
  }

  private boolean isVisibleTo(ActorContext viewer, AuditEventEntity event) {
    if (viewer.hasRole("admin")) {
      return true;
    }
    if (viewer.hasRole("owner") || viewer.hasRole("approver")) {
      return viewer.belongsTo(event.getActorOrg());
    }
    return viewer.actorId() != null && viewer.actorId().equalsIgnoreCase(event.getActorId());
  }

  private boolean matchesIgnoreCase(String expected, String actual) {
    return expected == null || expected.isBlank() || (actual != null && expected.equalsIgnoreCase(actual));
  }

  private AuditEventResponse toResponse(AuditEventEntity entity) {
    return new AuditEventResponse(
        entity.getId(),
        entity.getDatasetId(),
        entity.getActorId(),
        entity.getActorRole(),
        entity.getActorOrg(),
        entity.getAction(),
        entity.getStatus(),
        entity.getDetail(),
        entity.getCreatedAt()
    );
  }
}
