package com.brainweb3.backend.dataset.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadAuditRepository extends JpaRepository<UploadAuditEntity, Long> {
  List<UploadAuditEntity> findAllByDataset_IdOrderByCreatedAtDesc(String datasetId);
}
