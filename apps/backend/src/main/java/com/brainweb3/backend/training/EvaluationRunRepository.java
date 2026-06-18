package com.brainweb3.backend.training;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EvaluationRunRepository extends JpaRepository<EvaluationRunEntity, String> {

  List<EvaluationRunEntity> findAllByModelRecordIdOrderByCreatedAtDesc(String modelRecordId);

  @Query("select e.id from EvaluationRunEntity e")
  List<String> findAllIds();
}
