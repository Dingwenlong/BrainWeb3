package com.brainweb3.backend.access;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

@SpringBootTest
@AutoConfigureMockMvc
class AccessRequestControllerTests {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private RestTemplate eegRestTemplate;

  @Autowired
  private DatasetCatalogService datasetCatalogService;

  @Autowired
  private AccessRequestRepository accessRequestRepository;

  @Autowired
  private AuditEventRepository auditEventRepository;

  @Autowired
  private ObjectMapper objectMapper;

  private MockRestServiceServer mockServer;
  private String researcherToken;
  private String ownerToken;
  private String adminToken;
  private String approverToken;

  @BeforeEach
  void setUp() {
    datasetCatalogService.resetCatalog();
    accessRequestRepository.deleteAll();
    auditEventRepository.deleteAll();
    mockServer = MockRestServiceServer.bindTo(eegRestTemplate).build();
    researcherToken = loginAs("researcher-01");
    ownerToken = loginAs("owner-01");
    adminToken = loginAs("admin-01");
    approverToken = loginAs("approver-01");
  }

  @Test
  void deniesBrainActivityWithoutApprovedAccess() throws Exception {
    mockMvc.perform(get("/api/v1/datasets/ds-101/brain-activity")
            .queryParam("band", "beta")
            .header("Authorization", bearer(researcherToken)))
        .andExpect(status().isForbidden());
  }

  @Test
  void createsApprovesAndUsesAccessRequest() throws Exception {
    mockMvc.perform(post("/api/v1/access-requests")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                    {
                      "datasetId": "ds-101",
                      "purpose": "research-analysis",
                      "requestedDurationHours": 24,
                      "reason": "Need to inspect alpha activity before training."
                    }
                    """
            )
            .header("Authorization", bearer(researcherToken)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value("ar-1"))
        .andExpect(jsonPath("$.status").value("pending"));

    mockMvc.perform(post("/api/v1/access-requests/ar-1/approve")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                    {
                      "approvedDurationHours": 24,
                      "policy": "research-only"
                    }
                    """
            )
            .header("Authorization", bearer(ownerToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("approved"))
        .andExpect(jsonPath("$.policyNote").value("research-only"));

    mockServer.expect(requestTo(
            "http://localhost:8101/api/v1/datasets/ds-101/brain-activity?band=alpha&windowSize=2.0&stepSize=0.5&sourceUri=file://bootstrap/ds-101/physionet_s001.edf"
        ))
        .andExpect(method(GET))
        .andRespond(withSuccess(
            """
                {
                  "datasetId": "ds-101",
                  "samplingRate": 160,
                  "band": "alpha",
                  "windowSize": 2.0,
                  "stepSize": 0.5,
                  "regions": [
                    { "code": "LEFT_FRONTAL", "label": "Left Frontal", "electrodes": ["Fp1", "F3"] }
                  ],
                  "frames": [
                    { "timestamp": 0.0, "intensities": { "LEFT_FRONTAL": 0.62 } }
                  ],
                  "qualityFlags": ["real-data"],
                  "generatedAt": "2026-04-20T02:00:00Z"
                }
                """,
            MediaType.APPLICATION_JSON
        ));

    mockMvc.perform(get("/api/v1/datasets/ds-101/brain-activity")
            .header("Authorization", bearer(researcherToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.qualityFlags[0]").value("real-data"));

    mockMvc.perform(get("/api/v1/audits")
            .queryParam("datasetId", "ds-101")
            .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(3)))
        .andExpect(jsonPath("$[0].action").value("BRAIN_ACTIVITY_READ"))
        .andExpect(jsonPath("$[1].action").value("ACCESS_REQUEST_APPROVED"))
        .andExpect(jsonPath("$[2].action").value("ACCESS_REQUEST_CREATED"));

    mockMvc.perform(get("/api/v1/audits")
            .queryParam("datasetId", "ds-101")
            .header("Authorization", bearer(ownerToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].action").value("ACCESS_REQUEST_APPROVED"));

    mockMvc.perform(post("/api/v1/access-requests/ar-1/revoke")
            .header("Authorization", bearer(ownerToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("revoked"));

    mockMvc.perform(get("/api/v1/datasets/ds-101")
            .header("Authorization", bearer(ownerToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.chainRecords", hasSize(2)))
        .andExpect(jsonPath("$.chainRecords[0].eventType").value("ACCESS_REVOKED"))
        .andExpect(jsonPath("$.chainRecords[0].anchorStatus").value("anchored"))
        .andExpect(jsonPath("$.chainRecords[1].eventType").value("ACCESS_APPROVED"))
        .andExpect(jsonPath("$.chainRecords[1].chainTxHash").isNotEmpty());

    mockServer.verify();
  }

  @Test
  void filtersAccessRequestVisibilityByRole() throws Exception {
    mockMvc.perform(post("/api/v1/access-requests")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                    {
                      "datasetId": "ds-101",
                      "purpose": "federated-training",
                      "requestedDurationHours": 24,
                      "reason": "Researcher self request."
                    }
                    """
            )
            .header("Authorization", bearer(researcherToken)))
        .andExpect(status().isCreated());

    mockMvc.perform(post("/api/v1/access-requests")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                    {
                      "datasetId": "ds-205",
                      "purpose": "governance-review",
                      "requestedDurationHours": 12,
                      "reason": "Approver org request."
                    }
                    """
            )
            .header("Authorization", bearer(approverToken)))
        .andExpect(status().isCreated());

    mockMvc.perform(get("/api/v1/access-requests")
            .header("Authorization", bearer(researcherToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].actorId").value("researcher-01"));

    mockMvc.perform(get("/api/v1/access-requests")
            .header("Authorization", bearer(ownerToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)));

    mockMvc.perform(get("/api/v1/access-requests")
            .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)));
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
