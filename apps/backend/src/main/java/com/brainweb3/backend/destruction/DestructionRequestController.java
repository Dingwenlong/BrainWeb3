package com.brainweb3.backend.destruction;

import com.brainweb3.backend.access.ActorContextResolver;
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
@RequestMapping("/api/v1/destruction-requests")
public class DestructionRequestController {

  private final DestructionRequestService destructionRequestService;
  private final ActorContextResolver actorContextResolver;

  public DestructionRequestController(
      DestructionRequestService destructionRequestService,
      ActorContextResolver actorContextResolver
  ) {
    this.destructionRequestService = destructionRequestService;
    this.actorContextResolver = actorContextResolver;
  }

  @GetMapping
  public List<DestructionRequestResponse> list(
      @RequestParam(required = false) String datasetId,
      @RequestParam(required = false) String actorId,
      @RequestParam(required = false) String status,
      HttpServletRequest request
  ) {
    return destructionRequestService.list(
        actorContextResolver.resolveRequired(request),
        datasetId,
        actorId,
        status
    );
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public DestructionRequestResponse create(
      @Valid @RequestBody CreateDestructionRequest payload,
      HttpServletRequest request
  ) {
    return destructionRequestService.create(actorContextResolver.resolveRequired(request), payload);
  }

  @PostMapping("/{requestId}/approve")
  public DestructionRequestResponse approve(
      @PathVariable String requestId,
      @Valid @RequestBody DestructionDecisionRequest payload,
      HttpServletRequest request
  ) {
    return destructionRequestService.approve(requestId, actorContextResolver.resolveRequired(request), payload);
  }

  @PostMapping("/{requestId}/reject")
  public DestructionRequestResponse reject(
      @PathVariable String requestId,
      @Valid @RequestBody DestructionDecisionRequest payload,
      HttpServletRequest request
  ) {
    return destructionRequestService.reject(requestId, actorContextResolver.resolveRequired(request), payload);
  }

  @PostMapping("/{requestId}/execute")
  public DestructionRequestResponse execute(@PathVariable String requestId, HttpServletRequest request) {
    return destructionRequestService.execute(requestId, actorContextResolver.resolveRequired(request));
  }

  @PostMapping("/{requestId}/purge-storage")
  public DestructionRequestResponse purgeStorage(@PathVariable String requestId, HttpServletRequest request) {
    return destructionRequestService.purgeStorage(requestId, actorContextResolver.resolveRequired(request));
  }
}
