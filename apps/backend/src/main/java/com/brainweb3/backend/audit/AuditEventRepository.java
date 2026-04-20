package com.brainweb3.backend.audit;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEventEntity, Long> {

  List<AuditEventEntity> findAllByOrderByCreatedAtDesc();
}
