package com.brainweb3.backend.dataset.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DatasetRepository extends JpaRepository<DatasetEntity, String> {

  List<DatasetEntity> findAllByOrderByUpdatedAtDesc();

  @Query("select d.id from DatasetEntity d")
  List<String> findAllIds();
}
