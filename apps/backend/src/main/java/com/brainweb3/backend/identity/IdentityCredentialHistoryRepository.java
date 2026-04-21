package com.brainweb3.backend.identity;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentityCredentialHistoryRepository extends JpaRepository<IdentityCredentialHistoryEntity, Long> {
  List<IdentityCredentialHistoryEntity> findTop5BySubjectTypeIgnoreCaseAndSubjectKeyIgnoreCaseOrderByCreatedAtDesc(
      String subjectType,
      String subjectKey
  );
}
