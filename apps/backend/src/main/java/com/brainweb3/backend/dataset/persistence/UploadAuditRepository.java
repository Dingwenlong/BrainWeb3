package com.brainweb3.backend.dataset.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadAuditRepository extends JpaRepository<UploadAuditEntity, Long> {
}
