package com.brainweb3.backend.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "brainweb3.stage=test")
@AutoConfigureMockMvc
class SystemStatusControllerTests {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void returnsConfiguredStage() throws Exception {
    mockMvc.perform(get("/api/v1/system/status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.application").value("brainweb3-backend"))
        .andExpect(jsonPath("$.stage").value("test"))
        .andExpect(jsonPath("$.modules.backend").value("ready"))
        .andExpect(jsonPath("$.modules.chain").value("mock-mock-active"))
        .andExpect(jsonPath("$.chain.provider").value("mock"))
        .andExpect(jsonPath("$.chain.mode").value("mock-active"))
        .andExpect(jsonPath("$.chain.contractName").value("MockDataNotary"))
        .andExpect(jsonPath("$.chain.detailsRedacted").value(false));
  }
}
