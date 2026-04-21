package com.brainweb3.backend.dataset.api;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brainweb3.backend.chain.ChainGateway;
import com.brainweb3.backend.chain.ChainRegistrationReceipt;
import com.brainweb3.backend.dataset.service.DatasetCatalogService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

@SpringBootTest
@AutoConfigureMockMvc
class DatasetUploadRecoveryTests {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private RestTemplate eegRestTemplate;

  @Autowired
  private DatasetCatalogService datasetCatalogService;

  @Autowired
  private ObjectMapper objectMapper;

  @MockBean
  private ChainGateway chainGateway;

  private MockRestServiceServer mockServer;
  private String ownerToken;

  @BeforeEach
  void setUp() {
    datasetCatalogService.resetCatalog();
    mockServer = MockRestServiceServer.bindTo(eegRestTemplate).ignoreExpectOrder(true).build();
    ownerToken = loginAs("owner-01");
  }

  @Test
  void persistsRecoverableFailureAndAllowsRetry() throws Exception {
    when(chainGateway.registerDataAsset(any()))
        .thenThrow(new IllegalStateException("Mock chain unavailable."))
        .thenReturn(new ChainRegistrationReceipt(
            "mock",
            "sandbox",
            "MockDataNotary",
            "mock://contracts/data-notary",
            "SM3:retry-ok",
            "bafyretryok",
            "local://datasets/ds-301/recoverable.edf",
            "0xretryok",
            "did:brainweb3:huaxi-medical-union",
            "owner-review required before training",
            "fresh-upload"
        ));

    mockServer.expect(requestTo(org.hamcrest.Matchers.containsString("http://localhost:8101/api/v1/eeg/metadata?sourceUri=")))
        .andExpect(method(GET))
        .andRespond(withSuccess(
            """
                {
                  "format": "EDF",
                  "samplingRate": 256,
                  "channelCount": 32,
                  "sampleCount": 8192,
                  "durationSeconds": 32.0,
                  "qualityFlags": ["metadata-read"],
                  "generatedAt": "2026-04-20T02:00:00Z"
                }
                """,
            MediaType.APPLICATION_JSON
        ));
    mockServer.expect(requestTo(org.hamcrest.Matchers.containsString("http://localhost:8101/api/v1/eeg/metadata?sourceUri=")))
        .andExpect(method(GET))
        .andRespond(withSuccess(
            """
                {
                  "format": "EDF",
                  "samplingRate": 256,
                  "channelCount": 32,
                  "sampleCount": 8192,
                  "durationSeconds": 32.0,
                  "qualityFlags": ["metadata-read"],
                  "generatedAt": "2026-04-20T02:00:00Z"
                }
                """,
            MediaType.APPLICATION_JSON
        ));

    MockMultipartFile file = new MockMultipartFile(
        "file",
        "recoverable.edf",
        "application/octet-stream",
        "recoverable-demo".getBytes()
    );

    mockMvc.perform(multipart("/api/v1/datasets")
            .file(file)
            .param("subjectCode", "PMMI-S399")
            .param("title", "Recoverable Upload")
            .param("description", "Should remain persisted after chain failure.")
            .param("ownerOrganization", "Huaxi Medical Union")
            .param("tags", "recoverable, retry")
            .header("Authorization", bearer(ownerToken)))
        .andExpect(status().isInternalServerError());

    mockMvc.perform(get("/api/v1/datasets/ds-301")
            .header("Authorization", bearer(ownerToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.proofStatus").value("finalization-failed"))
        .andExpect(jsonPath("$.retryAllowed").value(true))
        .andExpect(jsonPath("$.lastErrorMessage").value("500 INTERNAL_SERVER_ERROR \"Failed to register dataset on chain.\""))
        .andExpect(jsonPath("$.uploadAudits", hasSize(4)));

    mockMvc.perform(post("/api/v1/datasets/ds-301/retry-finalization")
            .header("Authorization", bearer(ownerToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.proofStatus").value("notarized"))
        .andExpect(jsonPath("$.proof.chainTxHash").value("0xretryok"))
        .andExpect(jsonPath("$.lastErrorMessage").value(""))
        .andExpect(jsonPath("$.uploadAudits[0].action").value("FINALIZATION_RETRY_COMPLETED"));

    mockServer.verify();
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
