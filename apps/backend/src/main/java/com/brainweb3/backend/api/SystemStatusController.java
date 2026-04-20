package com.brainweb3.backend.api;

import com.brainweb3.backend.chain.ChainGateway;
import com.brainweb3.backend.chain.ChainRuntimeStatus;
import java.time.Instant;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemStatusController {

  private final ChainGateway chainGateway;

  public SystemStatusController(ChainGateway chainGateway) {
    this.chainGateway = chainGateway;
  }

  @GetMapping("/status")
  public SystemStatusResponse getStatus() {
    ChainRuntimeStatus chainStatus = chainGateway.describeStatus();
    return new SystemStatusResponse(
        "brainweb3-backend",
        "bootstrap",
        Instant.now(),
        toResponse(chainStatus),
        Map.of(
            "backend", "ready",
            "chain", chainStatus.provider() + "-" + chainStatus.mode(),
            "frontend", "scaffolded",
            "eeg-service", "scaffolded",
            "federated-service", "scaffolded",
            "contracts", "scaffolded"
        )
    );
  }

  private ChainStatusResponse toResponse(ChainRuntimeStatus chainStatus) {
    return new ChainStatusResponse(
        chainStatus.provider(),
        chainStatus.enabled(),
        chainStatus.mode(),
        chainStatus.group(),
        chainStatus.contractName(),
        chainStatus.contractAddress(),
        chainStatus.rpcPeers(),
        chainStatus.transportSecurity()
    );
  }
}
