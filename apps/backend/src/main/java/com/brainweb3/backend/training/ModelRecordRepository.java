package com.brainweb3.backend.training;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ModelRecordRepository extends JpaRepository<ModelRecordEntity, String> {

  List<ModelRecordEntity> findAllByOrderByCreatedAtDesc();

  Optional<ModelRecordEntity> findByTrainingJobId(String trainingJobId);

  @Query("select m.id from ModelRecordEntity m")
  List<String> findAllIds();
}
