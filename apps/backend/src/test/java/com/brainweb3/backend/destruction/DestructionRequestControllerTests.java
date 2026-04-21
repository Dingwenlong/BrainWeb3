package com.brainweb3.backend.destruction;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brainweb3.backend.access.AccessRequestRepository;
import com.brainweb3.backend.audit.AuditEventRepository;
import com.brainweb3.backend.dataset.persistence.DatasetRepository;
import com.brainweb3.backend.dataset.service.DatasetCatalogService;
import com.brainweb3.backend.training.TrainingJobRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DestructionRequestControllerTests {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private DatasetCatalogService datasetCatalogService;

  @Autowired
  private AccessRequestRepository accessRequestRepository;

  @Autowired
  private AuditEventRepository auditEventRepository;

  @Autowired
  private TrainingJobRepository trainingJobRepository;

  @Autowired
  private DatasetRepository datasetRepository;

  @Autowired
  private DestructionRequestRepository destructionRequestRepository;

  @Autowired
  private ObjectMapper objectMapper;

  private String researcherToken;
  private String ownerToken;
  private String adminToken;

  @BeforeEach
  void setUp() {
    datasetCatalogService.resetCatalog();
    accessRequestRepository.deleteAll();
    auditEventRepository.deleteAll();
    trainingJobRepository.deleteAll();
    destructionRequestRepository.deleteAll();
    researcherToken = loginAs("researcher-01");
    ownerToken = loginAs("owner-01");
    adminToken = loginAs("admin-01");
  }

  @Test
  void createsApprovesExecutesAndLocksDestroyedDataset() throws Exception {
    mockMvc.perform(post("/api/v1/access-requests")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                    {
                      "datasetId": "ds-101",
                      "purpose": "destruction-review",
                      "requestedDurationHours": 12,
                      "reason": "Need access before filing destruction request."
                    }
                    """
            )
            .header("Authorization", bearer(researcherToken)))
        .andExpect(status().isCreated());

    mockMvc.perform(post("/api/v1/access-requests/ar-1/approve")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                    {
                      "approvedDurationHours": 12,
                      "policy": "destruction-review"
                    }
                    """
            )
            .header("Authorization", bearer(ownerToken)))
        .andExpect(status().isOk());

    mockMvc.perform(post("/api/v1/destruction-requests")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                    {
                      "datasetId": "ds-101",
                      "reason": "Consent window closed and retention period reached."
                    }
                    """
            )
            .header("Authorization", bearer(researcherToken)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value("dr-1"))
        .andExpect(jsonPath("$.status").value("pending"));

    mockMvc.perform(post("/api/v1/destruction-requests/dr-1/approve")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                    {
                      "policy": "owner-approved destruction"
                    }
                    """
            )
            .header("Authorization", bearer(ownerToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("approved"));

    mockMvc.perform(post("/api/v1/destruction-requests/dr-1/execute")
            .header("Authorization", bearer(ownerToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("destroyed"))
        .andExpect(jsonPath("$.executedBy").value("owner-01"))
        .andExpect(jsonPath("$.cleanupStatus").value("pending"));

    mockMvc.perform(post("/api/v1/destruction-requests/dr-1/purge-storage")
            .header("Authorization", bearer(ownerToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cleanupStatus").value("completed"))
        .andExpect(jsonPath("$.cleanupCompletedAt").isNotEmpty())
        .andExpect(jsonPath("$.cleanupEvidenceRef").value(org.hamcrest.Matchers.startsWith("purge://")))
        .andExpect(jsonPath("$.cleanupEvidenceHash").isNotEmpty())
        .andExpect(jsonPath("$.cleanupVerifiedBy").value("owner-01"));

    mockMvc.perform(get("/api/v1/datasets/ds-101")
            .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.destructionStatus").value("destroyed"))
        .andExpect(jsonPath("$.destroyedAt").isNotEmpty())
        .andExpect(jsonPath("$.trainingReadiness").value("blocked"))
        .andExpect(jsonPath("$.chainRecords", hasSize(3)))
        .andExpect(jsonPath("$.chainRecords[0].eventType").value("DESTRUCTION_STORAGE_PURGED"))
        .andExpect(jsonPath("$.chainRecords[1].eventType").value("DESTRUCTION_COMPLETED"));

    mockMvc.perform(get("/api/v1/audits")
            .queryParam("datasetId", "ds-101")
            .queryParam("action", "DESTRUCTION_EXECUTED")
            .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].status").value("destroyed"));

    mockMvc.perform(get("/api/v1/audits")
            .queryParam("datasetId", "ds-101")
            .queryParam("action", "DESTRUCTION_STORAGE_PURGE_COMPLETED")
            .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)));

    mockMvc.perform(get("/api/v1/chain-records")
            .queryParam("eventType", "DESTRUCTION_STORAGE_PURGED")
            .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].businessStatus").value("completed"));

    mockMvc.perform(get("/api/v1/datasets/ds-101/brain-activity")
            .header("Authorization", bearer(researcherToken)))
        .andExpect(status().isForbidden());

    mockMvc.perform(post("/api/v1/training-jobs")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                    {
                      "datasetId": "ds-101",
                      "modelName": "Blocked Destroyed Dataset",
                      "objective": "post-destroy guard check",
                      "algorithm": "hetero-logistic-regression",
                      "requestedRounds": 3
                    }
                    """
            )
            .header("Authorization", bearer(researcherToken)))
        .andExpect(status().isConflict());
  }

  @Test
  void filtersVisibilityByRole() throws Exception {
    mockMvc.perform(post("/api/v1/destruction-requests")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                    {
                      "datasetId": "ds-101",
                      "reason": "Owner initiated cleanup review."
                    }
                    """
            )
            .header("Authorization", bearer(ownerToken)))
        .andExpect(status().isCreated());

    mockMvc.perform(get("/api/v1/destruction-requests")
            .header("Authorization", bearer(researcherToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));

    mockMvc.perform(get("/api/v1/destruction-requests")
            .header("Authorization", bearer(ownerToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)));

    mockMvc.perform(get("/api/v1/destruction-requests")
            .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)));
  }

  @Test
  void recordsFailedCleanupAndAllowsRetry() throws Exception {
    datasetRepository.findById("ds-101").ifPresent(dataset -> {
      dataset.setStorageKey("");
      datasetRepository.save(dataset);
    });

    mockMvc.perform(post("/api/v1/destruction-requests")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                    {
                      "datasetId": "ds-101",
                      "reason": "Cleanup failure rehearsal."
                    }
                    """
            )
            .header("Authorization", bearer(ownerToken)))
        .andExpect(status().isCreated());

    mockMvc.perform(post("/api/v1/destruction-requests/dr-1/approve")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                    {
                      "policy": "approve cleanup rehearsal"
                    }
                    """
            )
            .header("Authorization", bearer(ownerToken)))
        .andExpect(status().isOk());

    mockMvc.perform(post("/api/v1/destruction-requests/dr-1/execute")
            .header("Authorization", bearer(ownerToken)))
        .andExpect(status().isOk());

    mockMvc.perform(post("/api/v1/destruction-requests/dr-1/purge-storage")
            .header("Authorization", bearer(ownerToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cleanupStatus").value("failed"))
        .andExpect(jsonPath("$.cleanupError").isNotEmpty())
        .andExpect(jsonPath("$.cleanupEvidenceRef").value(""))
        .andExpect(jsonPath("$.cleanupEvidenceHash").value(""));

    mockMvc.perform(get("/api/v1/audits")
            .queryParam("datasetId", "ds-101")
            .queryParam("action", "DESTRUCTION_STORAGE_PURGE_FAILED")
            .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)));
  }

  private String loginAs(String actorId) {
    try {
      String response = mockMvc.perform(post("/api/v1/auth/login")
              .contentType(MediaType.APPLICATION_JSON)
              .content(
                  """
                      {
                        "actorId": "%s",
                        "password": "brainweb3-demo"
                      }
                      """.formatted(actorId)
              ))
          .andExpect(status().isOk())
          .andReturn()
          .getResponse()
          .getContentAsString();
      JsonNode payload = objectMapper.readTree(response);
      return payload.path("token").asText();
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to obtain auth token for tests.", exception);
    }
  }

  private String bearer(String token) {
    return "Bearer " + token;
  }
}
