package com.brainweb3.backend.training;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TrainingJobRepository extends JpaRepository<TrainingJobEntity, String> {

  List<TrainingJobEntity> findAllByOrderByCreatedAtDesc();

  @Query("select t.id from TrainingJobEntity t")
  List<String> findAllIds();
}
