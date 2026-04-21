package com.brainweb3.backend.chain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChainBusinessRecordRepository extends JpaRepository<ChainBusinessRecordEntity, Long> {

  List<ChainBusinessRecordEntity> findAllByOrderByAnchoredAtDesc();

  List<ChainBusinessRecordEntity> findAllByDatasetIdOrderByAnchoredAtDesc(String datasetId);
}
