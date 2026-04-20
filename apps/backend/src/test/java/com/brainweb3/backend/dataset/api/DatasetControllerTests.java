package com.brainweb3.backend.dataset.api;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brainweb3.backend.dataset.service.DatasetCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

@SpringBootTest
@AutoConfigureMockMvc
class DatasetControllerTests {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private RestTemplate eegRestTemplate;

  @Autowired
  private DatasetCatalogService datasetCatalogService;

  private MockRestServiceServer mockServer;

  @BeforeEach
  void setUp() {
    datasetCatalogService.resetCatalog();
    mockServer = MockRestServiceServer.bindTo(eegRestTemplate).build();
  }

  @Test
  void listsDatasets() throws Exception {
    mockMvc.perform(get("/api/v1/datasets"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[0].id").value("ds-101"));
  }

  @Test
  void returnsDatasetDetail() throws Exception {
    mockMvc.perform(get("/api/v1/datasets/ds-101"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.proof.ipfsCid").value("bafybeif6d4brainweb3demo101"))
        .andExpect(jsonPath("$.channelCount").value(64));
  }

  @Test
  void proxiesBrainActivityToEegService() throws Exception {
    mockServer.expect(requestTo(
            "http://localhost:8101/api/v1/datasets/ds-101/brain-activity?band=beta&windowSize=2.0&stepSize=0.5&sourceUri=file://bootstrap/ds-101/physionet_s001.edf&timeStart=5.0&timeEnd=12.0"
        ))
        .andExpect(method(GET))
        .andRespond(withSuccess(
            """
                {
                  "datasetId": "ds-101",
                  "samplingRate": 160,
                  "band": "beta",
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
            .queryParam("band", "beta")
            .queryParam("timeStart", "5")
            .queryParam("timeEnd", "12")
            .header("X-Actor-Id", "owner-01")
            .header("X-Actor-Role", "owner")
            .header("X-Actor-Org", "Huaxi Medical Union"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.band").value("beta"))
        .andExpect(jsonPath("$.frames[0].intensities.LEFT_FRONTAL").value(0.62));

    mockServer.verify();
  }

  @Test
  void uploadsSupportedEegFileAndCreatesDataset() throws Exception {
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "new_session.edf",
        "application/octet-stream",
        "demo-eeg-binary".getBytes()
    );

    mockServer.expect(requestTo(containsString("http://localhost:8101/api/v1/eeg/metadata?sourceUri=")))
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

    mockMvc.perform(multipart("/api/v1/datasets")
            .file(file)
            .param("subjectCode", "PMMI-S099")
            .param("title", "Upload Demo Session")
            .param("description", "A new upload ready for proof summary.")
            .param("ownerOrganization", "Sichuan Neuro Lab")
            .param("tags", "upload-demo, motor"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.dataset.id").value("ds-301"))
        .andExpect(jsonPath("$.dataset.format").value("EDF"))
        .andExpect(jsonPath("$.dataset.samplingRate").value(256))
        .andExpect(jsonPath("$.dataset.channelCount").value(32))
        .andExpect(jsonPath("$.dataset.durationSeconds").value(32.0))
        .andExpect(jsonPath("$.dataset.originalFilename").value("new_session.edf"))
        .andExpect(jsonPath("$.dataset.proof.didHolder").value("did:brainweb3:sichuan-neuro-lab"))
        .andExpect(jsonPath("$.dataset.proof.auditState").value("fresh-upload"))
        .andExpect(jsonPath("$.dataset.proof.ipfsCid").value(startsWith("bafy")))
        .andExpect(jsonPath("$.uploadReceipt").exists());

    mockMvc.perform(get("/api/v1/datasets"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(3)))
        .andExpect(jsonPath("$[0].id").value("ds-301"));

    mockMvc.perform(get("/api/v1/datasets/ds-301"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.uploadStatus").value("uploaded"))
        .andExpect(jsonPath("$.proofStatus").value("notarized"))
        .andExpect(jsonPath("$.proof.offChainReference").value("local://datasets/ds-301/new_session.edf"));

    mockServer.verify();
  }

  @Test
  void rejectsUnsupportedUploadFile() throws Exception {
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "notes.txt",
        MediaType.TEXT_PLAIN_VALUE,
        "invalid".getBytes()
    );

    mockMvc.perform(multipart("/api/v1/datasets")
            .file(file)
            .param("subjectCode", "PMMI-S099")
            .param("title", "Invalid Upload")
            .param("ownerOrganization", "Sichuan Neuro Lab"))
        .andExpect(status().isBadRequest());
  }
}
