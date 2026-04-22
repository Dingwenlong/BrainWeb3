package com.brainweb3.backend.audit;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brainweb3.backend.access.ActorContext;
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
class AuditControllerTests {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private DatasetCatalogService datasetCatalogService;

  @Autowired
  private AuditService auditService;

  @Autowired
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    datasetCatalogService.resetCatalog();
  }

  @Test
  void adminCanFilterAuditEventsByOrganization() throws Exception {
    loginAs("researcher-01");
    loginAs("owner-01");
    String adminToken = loginAs("admin-01");

    mockMvc.perform(get("/api/v1/audits")
            .header("Authorization", "Bearer " + adminToken)
            .param("action", "AUTH_LOGIN_SUCCEEDED")
            .param("actorOrg", "Huaxi Medical Union"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].actorOrg", everyItem(equalTo("Huaxi Medical Union"))));
  }

  @Test
  void ownerOnlySeesOwnOrganizationEvents() throws Exception {
    loginAs("researcher-01");
    String ownerToken = loginAs("owner-01");
    loginAs("admin-01");

    mockMvc.perform(get("/api/v1/audits")
            .header("Authorization", "Bearer " + ownerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].actorOrg", everyItem(equalTo("Huaxi Medical Union"))));
  }

  @Test
  void researcherOnlySeesOwnEvents() throws Exception {
    loginAs("owner-01");
    String researcherToken = loginAs("researcher-01");

    mockMvc.perform(get("/api/v1/audits")
            .header("Authorization", "Bearer " + researcherToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].actorId", everyItem(equalTo("researcher-01"))));
  }

  @Test
  void redactsSensitiveDetailInAuditResponses() throws Exception {
    String adminToken = loginAs("admin-01");
    auditService.record(
        "ds-101",
        new ActorContext("admin-01", "admin", "Platform Governance Center"),
        "SECURITY_REVIEWED",
        "failed",
        "password=brainweb3-demo resetToken=reset-raw-123 Authorization=Bearer eyJhbGciOiJIUzI1NiJ9.payload.signature"
    );

    mockMvc.perform(get("/api/v1/audits")
            .header("Authorization", "Bearer " + adminToken)
            .param("action", "SECURITY_REVIEWED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].detail", containsString("password=[REDACTED]")))
        .andExpect(jsonPath("$[0].detail", containsString("resetToken=[REDACTED]")))
        .andExpect(jsonPath("$[0].detail", containsString("Authorization=[REDACTED]")))
        .andExpect(jsonPath("$[0].detail", not(containsString("brainweb3-demo"))))
        .andExpect(jsonPath("$[0].detail", not(containsString("reset-raw-123"))))
        .andExpect(jsonPath("$[0].detail", not(containsString("eyJhbGciOiJIUzI1NiJ9"))));
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
}
