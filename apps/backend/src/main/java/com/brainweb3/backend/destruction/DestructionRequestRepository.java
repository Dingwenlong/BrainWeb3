package com.brainweb3.backend.destruction;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DestructionRequestRepository extends JpaRepository<DestructionRequestEntity, String> {

  List<DestructionRequestEntity> findAllByOrderByCreatedAtDesc();

  List<DestructionRequestEntity> findAllByDatasetIdOrderByCreatedAtDesc(String datasetId);

  @Query("select d.id from DestructionRequestEntity d")
  List<String> findAllIds();
}
