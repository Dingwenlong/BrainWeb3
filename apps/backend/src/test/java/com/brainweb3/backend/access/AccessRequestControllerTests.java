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

  private MockRestServiceServer mockServer;

  @BeforeEach
  void setUp() {
    datasetCatalogService.resetCatalog();
    accessRequestRepository.deleteAll();
    auditEventRepository.deleteAll();
    mockServer = MockRestServiceServer.bindTo(eegRestTemplate).build();
  }

  @Test
  void deniesBrainActivityWithoutApprovedAccess() throws Exception {
    mockMvc.perform(get("/api/v1/datasets/ds-101/brain-activity")
            .queryParam("band", "beta")
            .header("X-Actor-Id", "researcher-01")
            .header("X-Actor-Role", "researcher")
            .header("X-Actor-Org", "Sichuan Neuro Lab"))
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
            .header("X-Actor-Id", "researcher-01")
            .header("X-Actor-Role", "researcher")
            .header("X-Actor-Org", "Sichuan Neuro Lab"))
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
            .header("X-Actor-Id", "owner-01")
            .header("X-Actor-Role", "owner")
            .header("X-Actor-Org", "Huaxi Medical Union"))
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
            .header("X-Actor-Id", "researcher-01")
            .header("X-Actor-Role", "researcher")
            .header("X-Actor-Org", "Sichuan Neuro Lab"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.qualityFlags[0]").value("real-data"));

    mockMvc.perform(get("/api/v1/audits")
            .queryParam("datasetId", "ds-101")
            .header("X-Actor-Id", "owner-01")
            .header("X-Actor-Role", "owner")
            .header("X-Actor-Org", "Huaxi Medical Union"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(3)))
        .andExpect(jsonPath("$[0].action").value("BRAIN_ACTIVITY_READ"))
        .andExpect(jsonPath("$[1].action").value("ACCESS_REQUEST_APPROVED"))
        .andExpect(jsonPath("$[2].action").value("ACCESS_REQUEST_CREATED"));

    mockServer.verify();
  }
}
