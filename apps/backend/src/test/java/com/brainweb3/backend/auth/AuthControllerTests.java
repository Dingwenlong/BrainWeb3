package com.brainweb3.backend.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class AuthControllerTests {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  void logsInWithSeededDemoUser() throws Exception {
    mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                    {
                      "actorId": "researcher-01",
                      "password": "brainweb3-demo"
                    }
                    """
            ))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").isString())
        .andExpect(jsonPath("$.refreshToken").isString())
        .andExpect(jsonPath("$.actor.actorRole").value("researcher"))
        .andExpect(jsonPath("$.actor.actorOrg").value("Sichuan Neuro Lab"));
  }

  @Test
  void returnsSessionForBearerToken() throws Exception {
    String token = loginAs("owner-01");

    mockMvc.perform(get("/api/v1/auth/session")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.actorId").value("owner-01"))
        .andExpect(jsonPath("$.actorRole").value("owner"))
        .andExpect(jsonPath("$.actorOrg").value("Huaxi Medical Union"));
  }

  @Test
  void rejectsInvalidPassword() throws Exception {
    mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                    {
                      "actorId": "researcher-01",
                      "password": "wrong-password"
                    }
                    """
            ))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void registersNewResearcherAccountAndReturnsSession() throws Exception {
    mockMvc.perform(post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                    {
                      "actorId": "researcher-22",
                      "displayName": "新研究员",
                      "actorOrg": "Sichuan Neuro Lab",
                      "password": "brainweb3-demo"
                    }
                    """
            ))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.token").isString())
        .andExpect(jsonPath("$.refreshToken").isString())
        .andExpect(jsonPath("$.actor.actorId").value("researcher-22"))
        .andExpect(jsonPath("$.actor.actorRole").value("researcher"));
  }

  @Test
  void allowsAuthenticatedUserToChangePassword() throws Exception {
    String actorId = "researcher-98";

    mockMvc.perform(post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                    {
                      "actorId": "%s",
                      "displayName": "待改密研究员",
                      "actorOrg": "Sichuan Neuro Lab",
                      "password": "brainweb3-demo"
                    }
                    """.formatted(actorId)
            ))
        .andExpect(status().isCreated());

    String token = loginAs(actorId, "brainweb3-demo");

    mockMvc.perform(post("/api/v1/auth/change-password")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                    {
                      "currentPassword": "brainweb3-demo",
                      "nextPassword": "brainweb3-next"
                    }
                    """
            ))
        .andExpect(status().isNoContent());

    mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                    {
                      "actorId": "%s",
                      "password": "brainweb3-next"
                    }
                    """.formatted(actorId)
            ))
        .andExpect(status().isOk());
  }

  @Test
  void refreshesSessionByRotatingRefreshToken() throws Exception {
    String response = mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                    {
                      "actorId": "researcher-01",
                      "password": "brainweb3-demo"
                    }
                    """
            ))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();

    String refreshToken = objectMapper.readTree(response).path("refreshToken").asText();

    mockMvc.perform(post("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                    {
                      "refreshToken": "%s"
                    }
                    """.formatted(refreshToken)
            ))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").isString())
        .andExpect(jsonPath("$.refreshToken").isString())
        .andExpect(jsonPath("$.actor.actorId").value("researcher-01"));
  }

  @Test
  void resetsPasswordThroughTicketFlow() throws Exception {
    mockMvc.perform(post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                    {
                      "actorId": "researcher-77",
                      "displayName": "票据重置研究员",
                      "actorOrg": "Sichuan Neuro Lab",
                      "password": "brainweb3-demo"
                    }
                    """
            ))
        .andExpect(status().isCreated());

    String response = mockMvc.perform(post("/api/v1/auth/password-reset/request")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                    {
                      "actorId": "researcher-77"
                    }
                    """
            ))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.resetToken").isString())
        .andExpect(jsonPath("$.deliveryMode").value("inline-demo"))
        .andExpect(jsonPath("$.tokenVisible").value(true))
        .andReturn()
        .getResponse()
        .getContentAsString();

    String resetToken = objectMapper.readTree(response).path("resetToken").asText();

    mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                    {
                      "resetToken": "%s",
                      "nextPassword": "brainweb3-reset"
                    }
                    """.formatted(resetToken)
            ))
        .andExpect(status().isNoContent());

    mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                    {
                      "actorId": "researcher-77",
                      "password": "brainweb3-reset"
                    }
                    """
            ))
        .andExpect(status().isOk());
  }

  private String loginAs(String actorId) {
    return loginAs(actorId, "brainweb3-demo");
  }

  private String loginAs(String actorId, String password) {
    try {
      String response = mockMvc.perform(post("/api/v1/auth/login")
              .contentType(MediaType.APPLICATION_JSON)
              .content(
                  """
                      {
                        "actorId": "%s",
                        "password": "%s"
                      }
                      """.formatted(actorId, password)
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
