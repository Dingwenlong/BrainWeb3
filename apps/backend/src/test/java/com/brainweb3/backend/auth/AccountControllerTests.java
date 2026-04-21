package com.brainweb3.backend.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AccountControllerTests {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  void adminCanListAndManageAccounts() throws Exception {
    String adminToken = loginAs("admin-01");

    mockMvc.perform(get("/api/v1/accounts")
            .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].actorId").isString())
        .andExpect(jsonPath("$[0].credentialStatus.effectiveStatus").isString());

    mockMvc.perform(post("/api/v1/accounts")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                    {
                      "actorId": "owner-22",
                      "displayName": "新归属方",
                      "actorRole": "owner",
                      "actorOrg": "Huaxi Medical Union",
                      "status": "active",
                      "password": "brainweb3-owner"
                    }
                    """
            ))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.actorId").value("owner-22"))
        .andExpect(jsonPath("$.actorRole").value("owner"))
        .andExpect(jsonPath("$.credentialStatus.effectiveStatus").value("issued"))
        .andExpect(jsonPath("$.credentialHistory[0].nextStatus").value("issued"));

    mockMvc.perform(patch("/api/v1/accounts/owner-22")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                    {
                      "displayName": "归属方二十二",
                      "status": "disabled"
                    }
                    """
            ))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("归属方二十二"))
        .andExpect(jsonPath("$.status").value("disabled"));

    mockMvc.perform(post("/api/v1/accounts/owner-22/reset-password")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                    {
                      "nextPassword": "brainweb3-reset"
                    }
                    """
            ))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.actorId").value("owner-22"));
  }

  @Test
  void adminCanGovernCredentialStatusAndIdentityReflectsIt() throws Exception {
    String adminToken = loginAs("admin-01");

    mockMvc.perform(post("/api/v1/accounts")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                    {
                      "actorId": "owner-33",
                      "displayName": "凭证治理对象",
                      "actorRole": "owner",
                      "actorOrg": "Huaxi Medical Union",
                      "status": "active",
                      "password": "brainweb3-owner"
                    }
                    """
            ))
        .andExpect(status().isCreated());

    mockMvc.perform(patch("/api/v1/accounts/owner-33/credential-status")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                    {
                      "status": "revoked",
                      "reason": "研究材料复核未完成"
                    }
                    """
            ))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.actorId").value("owner-33"))
        .andExpect(jsonPath("$.credentialStatus.effectiveStatus").value("revoked"))
        .andExpect(jsonPath("$.credentialStatus.source").value("manual"))
        .andExpect(jsonPath("$.credentialStatus.reason").value("研究材料复核未完成"))
        .andExpect(jsonPath("$.credentialHistory[0].nextStatus").value("revoked"))
        .andExpect(jsonPath("$.credentialHistory[0].previousStatus").value("issued"));

    mockMvc.perform(get("/api/v1/identity/accounts/owner-33")
            .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.credential.credentialStatus").value("revoked"));
  }

  @Test
  void nonAdminCannotListAccounts() throws Exception {
    String researcherToken = loginAs("researcher-01");

    mockMvc.perform(get("/api/v1/accounts")
            .header("Authorization", "Bearer " + researcherToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void currentUserCanReadOwnAccountProfile() throws Exception {
    String ownerToken = loginAs("owner-01");

    mockMvc.perform(get("/api/v1/accounts/me")
            .header("Authorization", "Bearer " + ownerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.actorId").value("owner-01"))
        .andExpect(jsonPath("$.actorRole").value("owner"));
  }

  @Test
  void adminCannotDisableOwnAccount() throws Exception {
    String adminToken = loginAs("admin-01");

    mockMvc.perform(patch("/api/v1/accounts/admin-01")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                    {
                      "status": "disabled"
                    }
                    """
            ))
        .andExpect(status().isBadRequest());
  }

  @Test
  void adminCannotChangeOwnCredentialStatus() throws Exception {
    String adminToken = loginAs("admin-01");

    mockMvc.perform(patch("/api/v1/accounts/admin-01/credential-status")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                    {
                      "status": "revoked",
                      "reason": "self-lock"
                    }
                    """
            ))
        .andExpect(status().isBadRequest());
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
