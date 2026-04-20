package com.brainweb3.backend.access;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AccessRequestRepository extends JpaRepository<AccessRequestEntity, String> {

  @Query("select a.id from AccessRequestEntity a")
  List<String> findAllIds();

  List<AccessRequestEntity> findAllByOrderByCreatedAtDesc();

  Optional<AccessRequestEntity> findFirstByDatasetIdAndActorIdAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
      String datasetId,
      String actorId,
      String status,
      Instant expiresAt
  );
}
