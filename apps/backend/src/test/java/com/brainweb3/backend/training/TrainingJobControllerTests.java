package com.brainweb3.backend.training;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brainweb3.backend.access.AccessRequestRepository;
import com.brainweb3.backend.audit.AuditEventRepository;
import com.brainweb3.backend.dataset.service.DatasetCatalogService;
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
class TrainingJobControllerTests {

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
  private ModelRecordRepository modelRecordRepository;

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
    modelRecordRepository.deleteAll();
    researcherToken = loginAs("researcher-01");
    ownerToken = loginAs("owner-01");
    adminToken = loginAs("admin-01");
  }

  @Test
  void deniesTrainingRunWithoutApprovedAccess() throws Exception {
    mockMvc.perform(post("/api/v1/training-jobs")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                    {
                      "datasetId": "ds-101",
                      "modelName": "Unauthorized Probe",
                      "objective": "baseline-check",
                      "algorithm": "hetero-logistic-regression",
                      "requestedRounds": 4
                    }
                    """
            )
            .header("Authorization", bearer(researcherToken)))
        .andExpect(status().isForbidden());
  }

  @Test
  void createsAndRefreshesTrainingRunAfterAccessApproval() throws Exception {
    mockMvc.perform(post("/api/v1/access-requests")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                    {
                      "datasetId": "ds-101",
                      "purpose": "federated-training",
                      "requestedDurationHours": 24,
                      "reason": "Need approved access for a FATE dry run."
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
                      "approvedDurationHours": 24,
                      "policy": "training-approved"
                    }
                    """
            )
            .header("Authorization", bearer(ownerToken)))
        .andExpect(status().isOk());

    mockMvc.perform(post("/api/v1/training-jobs")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                    {
                      "datasetId": "ds-101",
                      "modelName": "Motor Intent Decoder",
                      "objective": "cross-site rehearsal",
                      "algorithm": "hetero-logistic-regression",
                      "requestedRounds": 5
                    }
                    """
            )
            .header("Authorization", bearer(researcherToken)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value("tj-1"))
        .andExpect(jsonPath("$.status").value("running"))
        .andExpect(jsonPath("$.orchestrator").value("mock-fate"))
        .andExpect(jsonPath("$.externalJobRef").value(startsWith("fate-job-")));

    mockMvc.perform(get("/api/v1/training-jobs")
            .header("Authorization", bearer(researcherToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].status").value("running"));

    mockMvc.perform(post("/api/v1/training-jobs/tj-1/refresh")
            .header("Authorization", bearer(researcherToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("succeeded"))
        .andExpect(jsonPath("$.completedRounds").value(5))
        .andExpect(jsonPath("$.metricSummary").value("AUC 0.91 | F1 0.87"));

    mockMvc.perform(get("/api/v1/model-records")
            .queryParam("trainingJobId", "tj-1")
            .header("Authorization", bearer(researcherToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].governanceStatus").value("candidate"))
        .andExpect(jsonPath("$[0].artifactRef").value("registry://models/tj-1"));

    mockMvc.perform(get("/api/v1/audits")
            .queryParam("datasetId", "ds-101")
            .queryParam("action", "TRAINING_RUN_COMPLETED")
            .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].status").value("succeeded"));

    mockMvc.perform(get("/api/v1/audits")
            .queryParam("datasetId", "ds-101")
            .queryParam("action", "MODEL_VERSION_REGISTERED")
            .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].status").value("candidate"));

    mockMvc.perform(get("/api/v1/datasets/ds-101")
            .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.chainRecords", hasSize(3)))
        .andExpect(jsonPath("$.chainRecords[0].eventType").value("MODEL_REGISTERED"))
        .andExpect(jsonPath("$.chainRecords[0].businessStatus").value("candidate"))
        .andExpect(jsonPath("$.chainRecords[1].eventType").value("TRAINING_COMPLETED"))
        .andExpect(jsonPath("$.chainRecords[1].businessStatus").value("succeeded"))
        .andExpect(jsonPath("$.chainRecords[2].eventType").value("ACCESS_APPROVED"));
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
