package com.brainweb3.backend.audit;

import com.brainweb3.backend.access.ActorContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audits")
public class AuditController {

  private final ActorContextResolver actorContextResolver;
  private final AuditService auditService;

  public AuditController(
      ActorContextResolver actorContextResolver,
      AuditService auditService
  ) {
    this.actorContextResolver = actorContextResolver;
    this.auditService = auditService;
  }

  @GetMapping
  public List<AuditEventResponse> list(
      @RequestParam(required = false) String datasetId,
      @RequestParam(required = false) String actorId,
      HttpServletRequest servletRequest
  ) {
    actorContextResolver.resolveRequired(servletRequest);
    return auditService.listEvents(datasetId, actorId);
  }
}
