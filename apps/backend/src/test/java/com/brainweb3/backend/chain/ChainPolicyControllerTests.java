package com.brainweb3.backend.chain;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
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
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ChainPolicyControllerTests {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  void listsDefaultChainPoliciesForPrivilegedActors() throws Exception {
        mockMvc.perform(get("/api/v1/chain-policy")
            .header("Authorization", bearer(loginAs("admin-01"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(8)))
        .andExpect(jsonPath("$[0].eventType").value("ACCESS_APPROVED"))
        .andExpect(jsonPath("$[0].anchorPolicy").value("required"))
        .andExpect(jsonPath("$[4].eventType").value("DESTRUCTION_COMPLETED"))
        .andExpect(jsonPath("$[5].eventType").value("DESTRUCTION_STORAGE_PURGED"))
        .andExpect(jsonPath("$[5].anchorPolicy").value("optional"))
        .andExpect(jsonPath("$[6].eventType").value("MODEL_REGISTERED"))
        .andExpect(jsonPath("$[6].anchorPolicy").value("optional"))
        .andExpect(jsonPath("$[7].eventType").value("MODEL_GOVERNED"));
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
