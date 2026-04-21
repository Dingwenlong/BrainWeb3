package com.brainweb3.backend.chain;

import com.brainweb3.backend.access.ActorContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chain-policy")
public class ChainPolicyController {

  private final ActorContextResolver actorContextResolver;
  private final ChainBusinessRecordService chainBusinessRecordService;
  private final ChainPolicyService chainPolicyService;

  public ChainPolicyController(
      ActorContextResolver actorContextResolver,
      ChainBusinessRecordService chainBusinessRecordService,
      ChainPolicyService chainPolicyService
  ) {
    this.actorContextResolver = actorContextResolver;
    this.chainBusinessRecordService = chainBusinessRecordService;
    this.chainPolicyService = chainPolicyService;
  }

  @GetMapping
  public List<ChainPolicyRuleResponse> list(HttpServletRequest servletRequest) {
    chainBusinessRecordService.ensureViewerAllowed(actorContextResolver.resolveRequired(servletRequest));
    return chainPolicyService.listRules();
  }
}
