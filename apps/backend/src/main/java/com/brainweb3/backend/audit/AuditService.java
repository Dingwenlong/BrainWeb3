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
  public List<AuditEventResponse> listEvents(String datasetId, String actorId) {
    return auditEventRepository.findAllByOrderByCreatedAtDesc().stream()
        .filter(event -> datasetId == null || datasetId.isBlank() || datasetId.equalsIgnoreCase(event.getDatasetId()))
        .filter(event -> actorId == null || actorId.isBlank() || actorId.equalsIgnoreCase(event.getActorId()))
        .map(this::toResponse)
        .toList();
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
