package com.brainweb3.backend.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
class SystemStatusSecurityTests {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void redactsChainInfrastructureDetailsInProductionLikeStage() throws Exception {
    mockMvc.perform(get("/api/v1/system/status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stage").value("production"))
        .andExpect(jsonPath("$.chain.detailsRedacted").value(true))
        .andExpect(jsonPath("$.chain.contractAddress").value(""))
        .andExpect(jsonPath("$.chain.rpcPeers").isArray())
        .andExpect(jsonPath("$.chain.rpcPeers").isEmpty());
  }
}
