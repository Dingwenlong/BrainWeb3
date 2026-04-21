package com.brainweb3.backend.training;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brainweb3.backend.access.AccessRequestRepository;
import com.brainweb3.backend.audit.AuditEventRepository;
import com.brainweb3.backend.chain.ChainBusinessRecordRepository;
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
class ModelRecordControllerTests {

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
  private ChainBusinessRecordRepository chainBusinessRecordRepository;

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
    chainBusinessRecordRepository.deleteAll();
    researcherToken = loginAs("researcher-01");
    ownerToken = loginAs("owner-01");
    adminToken = loginAs("admin-01");
  }

  @Test
  void allowsPrivilegedActorToGovernModelRecord() throws Exception {
    createSucceededTrainingRun();

    mockMvc.perform(get("/api/v1/model-records/mr-1")
            .header("Authorization", bearer(ownerToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.governanceStatus").value("candidate"))
        .andExpect(jsonPath("$.allowedGovernanceTransitions", hasSize(2)))
        .andExpect(jsonPath("$.allowedGovernanceTransitions[0]").value("active"))
        .andExpect(jsonPath("$.allowedGovernanceTransitions[1]").value("archived"));

    mockMvc.perform(patch("/api/v1/model-records/mr-1/governance")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                    {
                      "status": "active",
                      "note": "Promoted after governance review."
                    }
                    """
            )
            .header("Authorization", bearer(ownerToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.governanceStatus").value("active"))
        .andExpect(jsonPath("$.lastGovernedBy").value("owner-01"))
        .andExpect(jsonPath("$.allowedGovernanceTransitions", hasSize(1)))
        .andExpect(jsonPath("$.allowedGovernanceTransitions[0]").value("archived"));

    mockMvc.perform(get("/api/v1/audits")
            .queryParam("datasetId", "ds-101")
            .queryParam("action", "MODEL_GOVERNANCE_UPDATED")
            .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].status").value("active"));

    mockMvc.perform(get("/api/v1/chain-records")
            .queryParam("eventType", "MODEL_GOVERNED")
            .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].businessStatus").value("active"));
  }

  @Test
  void exposesGovernanceLaneForPrivilegedViewer() throws Exception {
    createSucceededTrainingRun();

    mockMvc.perform(patch("/api/v1/model-records/mr-1/governance")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                    {
                      "status": "active",
                      "note": "Promoted after governance review."
                    }
                    """
            )
            .header("Authorization", bearer(ownerToken)))
        .andExpect(status().isOk());

    mockMvc.perform(get("/api/v1/model-records/mr-1/governance-lane")
            .header("Authorization", bearer(ownerToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.model.id").value("mr-1"))
        .andExpect(jsonPath("$.summary.datasetVersionCount").value(1))
        .andExpect(jsonPath("$.comparison.currentVersionRank").value(1))
        .andExpect(jsonPath("$.comparison.latestVersion").value(true))
        .andExpect(jsonPath("$.summary.activeVersionCount").value(1))
        .andExpect(jsonPath("$.summary.latestGovernedBy").value("owner-01"))
        .andExpect(jsonPath("$.auditEvents", hasSize(1)))
        .andExpect(jsonPath("$.auditEvents[0].action").value("MODEL_GOVERNANCE_UPDATED"))
        .andExpect(jsonPath("$.chainRecords", hasSize(2)))
        .andExpect(jsonPath("$.chainRecords[0].eventType").value("MODEL_GOVERNED"))
        .andExpect(jsonPath("$.chainVisible").value(true))
        .andExpect(jsonPath("$.relatedModels", hasSize(0)));
  }

  @Test
  void exposesVersionComparisonForDatasetPeers() throws Exception {
    createSucceededTrainingRun("Registry Candidate", "model-governance rehearsal");
    createSucceededTrainingRun("Registry Follow-up", "dataset peer comparison");

    mockMvc.perform(patch("/api/v1/model-records/mr-1/governance")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                    {
                      "status": "active",
                      "note": "Promoted to active baseline."
                    }
                    """
            )
            .header("Authorization", bearer(ownerToken)))
        .andExpect(status().isOk());

    mockMvc.perform(get("/api/v1/model-records/mr-2/governance-lane")
            .header("Authorization", bearer(ownerToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.model.id").value("mr-2"))
        .andExpect(jsonPath("$.summary.datasetVersionCount").value(2))
        .andExpect(jsonPath("$.comparison.currentVersionRank").value(1))
        .andExpect(jsonPath("$.comparison.totalVisibleVersions").value(2))
        .andExpect(jsonPath("$.comparison.newerVersionCount").value(0))
        .andExpect(jsonPath("$.comparison.olderVersionCount").value(1))
        .andExpect(jsonPath("$.comparison.latestVersion").value(true))
        .andExpect(jsonPath("$.comparison.latestVersionId").value("mr-2"))
        .andExpect(jsonPath("$.comparison.sameAlgorithmVersionCount").value(2))
        .andExpect(jsonPath("$.comparison.sameStatusVersionCount").value(1))
        .andExpect(jsonPath("$.comparison.latestActiveVersionId").value("mr-1"))
        .andExpect(jsonPath("$.relatedModels", hasSize(1)))
        .andExpect(jsonPath("$.relatedModels[0].id").value("mr-1"));
  }

  @Test
  void hidesChainRecordsFromResearcherGovernanceLane() throws Exception {
    createSucceededTrainingRun();

    mockMvc.perform(get("/api/v1/model-records/mr-1/governance-lane")
            .header("Authorization", bearer(researcherToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.model.id").value("mr-1"))
        .andExpect(jsonPath("$.auditEvents", hasSize(1)))
        .andExpect(jsonPath("$.auditEvents[0].action").value("MODEL_VERSION_REGISTERED"))
        .andExpect(jsonPath("$.chainRecords", hasSize(0)))
        .andExpect(jsonPath("$.chainVisible").value(false));
  }

  @Test
  void blocksResearcherFromGovernanceUpdate() throws Exception {
    createSucceededTrainingRun();

    mockMvc.perform(patch("/api/v1/model-records/mr-1/governance")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                    {
                      "status": "archived",
                      "note": "Researcher should not archive registry records."
                    }
                    """
            )
            .header("Authorization", bearer(researcherToken)))
        .andExpect(status().isForbidden());
  }

  @Test
  void rejectsIllegalGovernanceTransitions() throws Exception {
    createSucceededTrainingRun();

    mockMvc.perform(patch("/api/v1/model-records/mr-1/governance")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                    {
                      "status": "candidate",
                      "note": "No-op transitions should be blocked."
                    }
                    """
            )
            .header("Authorization", bearer(ownerToken)))
        .andExpect(status().isBadRequest())
        .andExpect(status().reason("Model record is already in the requested governance status."));

    mockMvc.perform(patch("/api/v1/model-records/mr-1/governance")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                    {
                      "status": "active",
                      "note": "Promoted after governance review."
                    }
                    """
            )
            .header("Authorization", bearer(ownerToken)))
        .andExpect(status().isOk());

    mockMvc.perform(patch("/api/v1/model-records/mr-1/governance")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                    {
                      "status": "candidate",
                      "note": "Active models should not move back to candidate."
                    }
                    """
            )
            .header("Authorization", bearer(ownerToken)))
        .andExpect(status().isBadRequest())
        .andExpect(status().reason("Unsupported model governance transition: active -> candidate."));
  }

  private void createSucceededTrainingRun() throws Exception {
    createSucceededTrainingRun("Registry Candidate", "model-governance rehearsal");
  }

  private void createSucceededTrainingRun(String modelName, String objective) throws Exception {
    long requestIndex = accessRequestRepository.count() + 1;

    mockMvc.perform(post("/api/v1/access-requests")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                    {
                      "datasetId": "ds-101",
                      "purpose": "federated-training",
                      "requestedDurationHours": 24,
                      "reason": "Need approved access for a model governance dry run."
                    }
                    """
            )
            .header("Authorization", bearer(researcherToken)))
        .andExpect(status().isCreated());

    mockMvc.perform(post("/api/v1/access-requests/ar-%d/approve".formatted(requestIndex))
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

    long jobIndex = trainingJobRepository.count() + 1;
    mockMvc.perform(post("/api/v1/training-jobs")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                    {
                      "datasetId": "ds-101",
                      "modelName": "%s",
                      "objective": "%s",
                      "algorithm": "hetero-logistic-regression",
                      "requestedRounds": 5
                    }
                    """.formatted(modelName, objective)
            )
            .header("Authorization", bearer(researcherToken)))
        .andExpect(status().isCreated());

    mockMvc.perform(post("/api/v1/training-jobs/tj-%d/refresh".formatted(jobIndex))
            .header("Authorization", bearer(researcherToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("succeeded"));
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
