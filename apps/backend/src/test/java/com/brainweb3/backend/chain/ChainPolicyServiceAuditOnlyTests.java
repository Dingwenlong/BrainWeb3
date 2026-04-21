package com.brainweb3.backend.chain;

import static org.assertj.core.api.Assertions.assertThat;

import com.brainweb3.backend.access.ActorContext;
import com.brainweb3.backend.dataset.service.DatasetCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "brainweb3.chain.disabled-event-types=TRAINING_FAILED"
})
class ChainPolicyServiceAuditOnlyTests {

  @Autowired
  private DatasetCatalogService datasetCatalogService;

  @Autowired
  private ChainBusinessRecordService chainBusinessRecordService;

  @BeforeEach
  void setUp() {
    datasetCatalogService.resetCatalog();
  }

  @Test
  void skipsAnchoringWhenEventIsConfiguredAsAuditOnly() {
    ChainBusinessRecordResponse response = chainBusinessRecordService.record(
        "ds-101",
        new ActorContext("admin-01", "admin", "Huaxi Medical Union"),
        "TRAINING_FAILED",
        "tj-policy-1",
        "failed",
        "Audit-only dry run."
    );

    assertThat(response.anchorPolicy()).isEqualTo("audit-only");
    assertThat(response.anchorStatus()).isEqualTo("policy-skipped");
    assertThat(response.chainTxHash()).isBlank();
    assertThat(response.contractName()).isBlank();
  }
}
