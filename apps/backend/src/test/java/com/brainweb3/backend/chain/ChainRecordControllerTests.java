package com.brainweb3.backend.chain;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brainweb3.backend.access.AccessRequestRepository;
import com.brainweb3.backend.audit.AuditEventRepository;
import com.brainweb3.backend.dataset.service.DatasetCatalogService;
import com.brainweb3.backend.training.ModelRecordRepository;
import com.brainweb3.backend.training.TrainingJobRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ChainRecordControllerTests {

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
  void listsChainRecordsForPrivilegedActors() throws Exception {
    createApprovedTrainingFlow();
    String chainTxHash = chainBusinessRecordRepository.findAllByOrderByAnchoredAtDesc().get(0).getChainTxHash();

        mockMvc.perform(get("/api/v1/chain-records")
            .header("Authorization", bearer(ownerToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(3)))
        .andExpect(jsonPath("$[0].eventType").value("MODEL_REGISTERED"))
        .andExpect(jsonPath("$[0].contractName").value("MockBusinessEventAnchor"))
        .andExpect(jsonPath("$[1].eventType").value("TRAINING_COMPLETED"))
        .andExpect(jsonPath("$[2].eventType").value("ACCESS_APPROVED"))
        .andExpect(jsonPath("$[2].contractAddress").value("mock://contracts/business-event-anchor"));

    mockMvc.perform(get("/api/v1/chain-records")
            .queryParam("eventType", "ACCESS_APPROVED")
            .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].referenceId").value("ar-1"));

    mockMvc.perform(get("/api/v1/chain-records")
            .queryParam("eventType", "MODEL_REGISTERED")
            .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].businessStatus").value("candidate"));

    mockMvc.perform(get("/api/v1/chain-records")
            .queryParam("chainTxHash", chainTxHash)
            .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].chainTxHash").value(chainTxHash));
  }

  @Test
  void rejectsResearchersFromChainRecordWorkspace() throws Exception {
    createApprovedTrainingFlow();

    mockMvc.perform(get("/api/v1/chain-records")
            .header("Authorization", bearer(researcherToken)))
        .andExpect(status().isForbidden());
  }

  @Test
  void redactsSensitiveDetailAndAnchorErrorInChainWorkspace() throws Exception {
    ChainBusinessRecordEntity failedRecord = new ChainBusinessRecordEntity();
    failedRecord.setDatasetId("ds-101");
    failedRecord.setEventType("ACCESS_REVOKED");
    failedRecord.setReferenceId("ar-sensitive");
    failedRecord.setBusinessStatus("revoked");
    failedRecord.setAnchorStatus("failed");
    failedRecord.setActorId("owner-01");
    failedRecord.setActorRole("owner");
    failedRecord.setActorOrg("Huaxi Medical Union");
    failedRecord.setChainProvider("mock");
    failedRecord.setChainGroup("sandbox");
    failedRecord.setContractName("MockBusinessEventAnchor");
    failedRecord.setContractAddress("mock://contracts/business-event-anchor");
    failedRecord.setDetail("token=chain-raw-123 retry password=brainweb3-demo");
    failedRecord.setAnchorError("Authorization=Bearer eyJhbGciOiJIUzI1NiJ9.payload.signature");
    failedRecord.setAnchoredAt(Instant.parse("2026-04-21T02:00:00Z"));
    chainBusinessRecordRepository.save(failedRecord);

    mockMvc.perform(get("/api/v1/chain-records")
            .queryParam("eventType", "ACCESS_REVOKED")
            .header("Authorization", bearer(ownerToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].detail", containsString("token=[REDACTED]")))
        .andExpect(jsonPath("$[0].detail", containsString("password=[REDACTED]")))
        .andExpect(jsonPath("$[0].detail", not(containsString("chain-raw-123"))))
        .andExpect(jsonPath("$[0].detail", not(containsString("brainweb3-demo"))))
        .andExpect(jsonPath("$[0].anchorError", containsString("Authorization=[REDACTED]")))
        .andExpect(jsonPath("$[0].anchorError", not(containsString("eyJhbGciOiJIUzI1NiJ9"))));
  }

  @Test
  void retriesFailedChainRecordsForPrivilegedActors() throws Exception {
    ChainBusinessRecordEntity failedRecord = new ChainBusinessRecordEntity();
    failedRecord.setDatasetId("ds-101");
    failedRecord.setEventType("ACCESS_REVOKED");
    failedRecord.setReferenceId("ar-failed");
    failedRecord.setBusinessStatus("revoked");
    failedRecord.setAnchorStatus("failed");
    failedRecord.setActorId("owner-01");
    failedRecord.setActorRole("owner");
    failedRecord.setActorOrg("Huaxi Medical Union");
    failedRecord.setChainProvider("mock");
    failedRecord.setChainGroup("sandbox");
    failedRecord.setContractName("MockDataNotary");
    failedRecord.setContractAddress("mock://contracts/data-notary");
    failedRecord.setDetail("Retrying a failed revoke anchor.");
    failedRecord.setAnchorError("Simulated chain outage.");
    failedRecord.setAnchoredAt(Instant.parse("2026-04-21T02:00:00Z"));
    long recordId = chainBusinessRecordRepository.save(failedRecord).getId();

    mockMvc.perform(post("/api/v1/chain-records/{recordId}/retry", recordId)
            .header("Authorization", bearer(ownerToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.anchorStatus").value("anchored"))
        .andExpect(jsonPath("$.contractName").value("MockBusinessEventAnchor"))
        .andExpect(jsonPath("$.chainTxHash").isNotEmpty())
        .andExpect(jsonPath("$.anchorError").value(""));
  }

  @Test
  void rejectsRetryForAlreadyAnchoredRecord() throws Exception {
    createApprovedTrainingFlow();
    long anchoredRecordId = chainBusinessRecordRepository.findAllByOrderByAnchoredAtDesc().get(0).getId();

    mockMvc.perform(post("/api/v1/chain-records/{recordId}/retry", anchoredRecordId)
            .header("Authorization", bearer(adminToken)))
        .andExpect(status().isConflict());
  }

  private void createApprovedTrainingFlow() throws Exception {
    mockMvc.perform(post("/api/v1/access-requests")
            .contentType(APPLICATION_JSON)
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
            .contentType(APPLICATION_JSON)
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
            .contentType(APPLICATION_JSON)
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
        .andExpect(status().isCreated());

    mockMvc.perform(post("/api/v1/training-jobs/tj-1/refresh")
            .header("Authorization", bearer(researcherToken)))
        .andExpect(status().isOk());
  }

  private String loginAs(String actorId) {
    try {
      String response = mockMvc.perform(post("/api/v1/auth/login")
              .contentType(APPLICATION_JSON)
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
