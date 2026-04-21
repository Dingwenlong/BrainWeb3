package com.brainweb3.backend.identity;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class IdentityControllerTests {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  void returnsCurrentActorAndOrganizationIdentity() throws Exception {
    String ownerToken = loginAs("owner-01");

    mockMvc.perform(get("/api/v1/identity/me")
            .header("Authorization", bearer(ownerToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.actorId").value("owner-01"))
        .andExpect(jsonPath("$.actorDid").value("did:brainweb3:user:huaxi-medical-union:owner-01"))
        .andExpect(jsonPath("$.organizationDid").value("did:brainweb3:org:huaxi-medical-union"))
        .andExpect(jsonPath("$.credential.credentialStatus").value("issued"))
        .andExpect(jsonPath("$.credential.verificationStatus").value("verified"));

    mockMvc.perform(get("/api/v1/identity/organizations")
            .queryParam("name", "Huaxi Medical Union")
            .header("Authorization", bearer(ownerToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.organizationDid").value("did:brainweb3:org:huaxi-medical-union"))
        .andExpect(jsonPath("$.credential.type").value("BrainWeb3OrganizationCredential"))
        .andExpect(jsonPath("$.statusSnapshot.effectiveStatus").value("issued"))
        .andExpect(jsonPath("$.credentialHistory[0].nextStatus").value("issued"));
  }

  @Test
  void adminCanUpdateOrganizationCredentialStatus() throws Exception {
    String adminToken = loginAs("admin-01");

    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/identity/organizations/credential-status")
            .contentType(APPLICATION_JSON)
            .header("Authorization", bearer(adminToken))
            .content(
                """
                    {
                      "organizationName": "Huaxi Medical Union",
                      "status": "suspended",
                      "reason": "机构合规复核中"
                    }
                    """
            ))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.organizationName").value("Huaxi Medical Union"))
        .andExpect(jsonPath("$.credential.credentialStatus").value("suspended"))
        .andExpect(jsonPath("$.statusSnapshot.source").value("manual"))
        .andExpect(jsonPath("$.statusSnapshot.reason").value("机构合规复核中"))
        .andExpect(jsonPath("$.credentialHistory[0].nextStatus").value("suspended"))
        .andExpect(jsonPath("$.credentialHistory[0].previousStatus").value("issued"));
  }

  @Test
  void nonAdminCannotUpdateOrganizationCredentialStatus() throws Exception {
    String ownerToken = loginAs("owner-01");

    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/identity/organizations/credential-status")
            .contentType(APPLICATION_JSON)
            .header("Authorization", bearer(ownerToken))
            .content(
                """
                    {
                      "organizationName": "Huaxi Medical Union",
                      "status": "suspended",
                      "reason": "机构合规复核中"
                    }
                    """
            ))
        .andExpect(status().isForbidden());
  }

  @Test
  void verifiesCredentialProofAndRejectsTamperedProof() throws Exception {
    String adminToken = loginAs("admin-01");

    String credentialPayload = mockMvc.perform(get("/api/v1/identity/me")
            .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString(StandardCharsets.UTF_8);

    JsonNode credential = objectMapper.readTree(credentialPayload).path("credential");

    mockMvc.perform(post("/api/v1/identity/verify")
            .contentType(APPLICATION_JSON)
            .header("Authorization", bearer(adminToken))
            .content(objectMapper.writeValueAsString(credential)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verified").value(true))
        .andExpect(jsonPath("$.status").value("verified"))
        .andExpect(jsonPath("$.reason").value("Credential proof matches the expected BrainWeb3 signature."));

    ((com.fasterxml.jackson.databind.node.ObjectNode) credential).put("proof", "tampered-proof");
    mockMvc.perform(post("/api/v1/identity/verify")
            .contentType(APPLICATION_JSON)
            .header("Authorization", bearer(adminToken))
            .content(objectMapper.writeValueAsString(credential)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verified").value(false))
        .andExpect(jsonPath("$.status").value("invalid"));
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
