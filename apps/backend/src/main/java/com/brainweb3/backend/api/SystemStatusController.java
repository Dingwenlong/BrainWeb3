package com.brainweb3.backend.api;

import com.brainweb3.backend.chain.ChainGateway;
import com.brainweb3.backend.chain.ChainRuntimeStatus;
import com.brainweb3.backend.config.RuntimeSecurityGuardrails;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemStatusController {

  private final ChainGateway chainGateway;
  private final String stage;

  public SystemStatusController(
      ChainGateway chainGateway,
      @Value("${brainweb3.stage:bootstrap}") String stage
  ) {
    this.chainGateway = chainGateway;
    this.stage = stage;
  }

  @GetMapping("/status")
  public SystemStatusResponse getStatus() {
    ChainRuntimeStatus chainStatus = chainGateway.describeStatus();
    return new SystemStatusResponse(
        "brainweb3-backend",
        stage,
        Instant.now(),
        toResponse(chainStatus),
        Map.of(
            "backend", "ready",
            "chain", chainStatus.provider() + "-" + chainStatus.mode(),
            "frontend", "scaffolded",
            "eeg-service", "scaffolded",
            "federated-service", "mock-active",
            "contracts", "scaffolded"
        )
    );
  }

  private ChainStatusResponse toResponse(ChainRuntimeStatus chainStatus) {
    boolean detailsRedacted = RuntimeSecurityGuardrails.requiresProductionGuardrails(stage);
    return new ChainStatusResponse(
        chainStatus.provider(),
        chainStatus.enabled(),
        chainStatus.mode(),
        chainStatus.group(),
        chainStatus.contractName(),
        detailsRedacted ? "" : chainStatus.contractAddress(),
        detailsRedacted ? List.of() : chainStatus.rpcPeers(),
        chainStatus.transportSecurity(),
        detailsRedacted
    );
  }
}
