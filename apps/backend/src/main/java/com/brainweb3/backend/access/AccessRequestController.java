package com.brainweb3.backend.access;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/access-requests")
public class AccessRequestController {

  private final ActorContextResolver actorContextResolver;
  private final AccessRequestService accessRequestService;

  public AccessRequestController(
      ActorContextResolver actorContextResolver,
      AccessRequestService accessRequestService
  ) {
    this.actorContextResolver = actorContextResolver;
    this.accessRequestService = accessRequestService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public AccessRequestResponse create(
      @Valid @RequestBody CreateAccessRequest request,
      HttpServletRequest servletRequest
  ) {
    return accessRequestService.create(actorContextResolver.resolveRequired(servletRequest), request);
  }

  @GetMapping
  public List<AccessRequestResponse> list(
      @RequestParam(required = false) String datasetId,
      @RequestParam(required = false) String actorId,
      @RequestParam(required = false) String status,
      HttpServletRequest servletRequest
  ) {
    return accessRequestService.list(actorContextResolver.resolveRequired(servletRequest), datasetId, actorId, status);
  }

  @PostMapping("/{requestId}/approve")
  public AccessRequestResponse approve(
      @PathVariable String requestId,
      @Valid @RequestBody AccessDecisionRequest request,
      HttpServletRequest servletRequest
  ) {
    return accessRequestService.approve(requestId, actorContextResolver.resolveRequired(servletRequest), request);
  }

  @PostMapping("/{requestId}/reject")
  public AccessRequestResponse reject(
      @PathVariable String requestId,
      @Valid @RequestBody AccessDecisionRequest request,
      HttpServletRequest servletRequest
  ) {
    return accessRequestService.reject(requestId, actorContextResolver.resolveRequired(servletRequest), request);
  }

  @PostMapping("/{requestId}/revoke")
  public AccessRequestResponse revoke(
      @PathVariable String requestId,
      HttpServletRequest servletRequest
  ) {
    return accessRequestService.revoke(requestId, actorContextResolver.resolveRequired(servletRequest));
  }
}
