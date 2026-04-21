package com.brainweb3.backend.identity;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentityCredentialStatusRepository extends JpaRepository<IdentityCredentialStatusEntity, Long> {
  Optional<IdentityCredentialStatusEntity> findBySubjectTypeIgnoreCaseAndSubjectKeyIgnoreCase(
      String subjectType,
      String subjectKey
  );
}
