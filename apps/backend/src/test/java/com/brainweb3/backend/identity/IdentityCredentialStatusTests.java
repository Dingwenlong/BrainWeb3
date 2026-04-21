package com.brainweb3.backend.identity;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brainweb3.backend.auth.AppUserEntity;
import com.brainweb3.backend.auth.AppUserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "brainweb3.identity.revoked-actors[0]=owner-01"
})
@AutoConfigureMockMvc
class IdentityCredentialStatusTests {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private AppUserRepository appUserRepository;

  @BeforeEach
  void seedDisabledIdentityFixtures() {
    if (!appUserRepository.existsById("suspended-01")) {
      AppUserEntity entity = new AppUserEntity();
      entity.setId("suspended-01");
      entity.setDisplayName("挂起研究员");
      entity.setPasswordHash("disabled-account-placeholder");
      entity.setRoleCode("researcher");
      entity.setOrganization("Silent Neuro Lab");
      entity.setStatus("disabled");
      entity.setCreatedAt(Instant.parse("2026-04-21T00:00:00Z"));
      entity.setUpdatedAt(Instant.parse("2026-04-21T00:00:00Z"));
      entity.setPasswordChangedAt(Instant.parse("2026-04-21T00:00:00Z"));
      appUserRepository.save(entity);
    }
  }

  @Test
  void returnsRevokedCredentialForConfiguredActor() throws Exception {
    String ownerToken = loginAs("owner-01");

    String response = mockMvc.perform(get("/api/v1/identity/me")
            .header("Authorization", bearer(ownerToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.credential.credentialStatus").value("revoked"))
        .andReturn()
        .getResponse()
        .getContentAsString(StandardCharsets.UTF_8);

    JsonNode credential = objectMapper.readTree(response).path("credential");
    mockMvc.perform(post("/api/v1/identity/verify")
            .contentType(APPLICATION_JSON)
            .header("Authorization", bearer(ownerToken))
            .content(objectMapper.writeValueAsString(credential)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verified").value(false))
        .andExpect(jsonPath("$.status").value("revoked"))
        .andExpect(jsonPath("$.reason").value("Credential has been revoked."));
  }

  @Test
  void returnsSuspendedCredentialForDisabledAccount() throws Exception {
    String adminToken = loginAs("admin-01");

    String response = mockMvc.perform(get("/api/v1/identity/accounts/suspended-01")
            .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.actorDid").value("did:brainweb3:user:silent-neuro-lab:suspended-01"))
        .andExpect(jsonPath("$.credential.credentialStatus").value("suspended"))
        .andReturn()
        .getResponse()
        .getContentAsString(StandardCharsets.UTF_8);

    JsonNode credential = objectMapper.readTree(response).path("credential");
    mockMvc.perform(post("/api/v1/identity/verify")
            .contentType(APPLICATION_JSON)
            .header("Authorization", bearer(adminToken))
            .content(objectMapper.writeValueAsString(credential)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verified").value(false))
        .andExpect(jsonPath("$.status").value("suspended"))
        .andExpect(jsonPath("$.reason").value("Credential is suspended and cannot be used right now."));
  }

  @Test
  void returnsSuspendedCredentialForOrganizationWithoutActiveAccounts() throws Exception {
    String adminToken = loginAs("admin-01");

    String response = mockMvc.perform(get("/api/v1/identity/organizations")
            .queryParam("name", "Silent Neuro Lab")
            .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.organizationDid").value("did:brainweb3:org:silent-neuro-lab"))
        .andExpect(jsonPath("$.credential.credentialStatus").value("suspended"))
        .andReturn()
        .getResponse()
        .getContentAsString(StandardCharsets.UTF_8);

    JsonNode credential = objectMapper.readTree(response).path("credential");
    mockMvc.perform(post("/api/v1/identity/verify")
            .contentType(APPLICATION_JSON)
            .header("Authorization", bearer(adminToken))
            .content(objectMapper.writeValueAsString(credential)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verified").value(false))
        .andExpect(jsonPath("$.status").value("suspended"));
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
          .getContentAsString(StandardCharsets.UTF_8);
      ObjectNode payload = (ObjectNode) objectMapper.readTree(response);
      return payload.path("token").asText();
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to obtain auth token for tests.", exception);
    }
  }

  private String bearer(String token) {
    return "Bearer " + token;
  }
}
