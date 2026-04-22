package com.brainweb3.backend.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "brainweb3.stage=production",
    "brainweb3.auth.jwt-secret=production-jwt-secret-2026-04-22-strong-value",
    "brainweb3.identity.credential-secret=production-identity-secret-2026-04-22-strong-value",
    "brainweb3.auth.demo-password=production-reset-placeholder-2026-04-22",
    "brainweb3.auth.allow-demo-bootstrap=false",
    "brainweb3.auth.allow-demo-password-login=false"
})
@AutoConfigureMockMvc
class AuthControllerSecurityTests {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void hidesPasswordResetTokenInProductionLikeStage() throws Exception {
    mockMvc.perform(post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                    {
                      "actorId": "secure-user-01",
                      "displayName": "安全测试用户",
                      "actorOrg": "Sichuan Neuro Lab",
                      "password": "secure-password-2026"
                    }
                    """
            ))
        .andExpect(status().isCreated());

    mockMvc.perform(post("/api/v1/auth/password-reset/request")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                    {
                      "actorId": "secure-user-01"
                    }
                    """
            ))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.actorId").value("secure-user-01"))
        .andExpect(jsonPath("$.resetToken").doesNotExist())
        .andExpect(jsonPath("$.deliveryMode").value("out-of-band"))
        .andExpect(jsonPath("$.tokenVisible").value(false));
  }

  @Test
  void doesNotLeakWhetherResetTargetExistsInProductionLikeStage() throws Exception {
    mockMvc.perform(post("/api/v1/auth/password-reset/request")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                    {
                      "actorId": "missing-user-01"
                    }
                    """
            ))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.actorId").value("missing-user-01"))
        .andExpect(jsonPath("$.resetToken").doesNotExist())
        .andExpect(jsonPath("$.deliveryMode").value("out-of-band"))
        .andExpect(jsonPath("$.tokenVisible").value(false));
  }
}
