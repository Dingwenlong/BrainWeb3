package com.brainweb3.backend.training;

import com.brainweb3.backend.access.ActorContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/model-records")
public class ModelRecordController {

  private final ActorContextResolver actorContextResolver;
  private final ModelRecordService modelRecordService;

  public ModelRecordController(
      ActorContextResolver actorContextResolver,
      ModelRecordService modelRecordService
  ) {
    this.actorContextResolver = actorContextResolver;
    this.modelRecordService = modelRecordService;
  }

  @GetMapping
  public List<ModelRecordResponse> list(
      @RequestParam(required = false) String datasetId,
      @RequestParam(required = false) String governanceStatus,
      @RequestParam(required = false) String trainingJobId,
      HttpServletRequest request
  ) {
    return modelRecordService.list(
        actorContextResolver.resolveRequired(request),
        datasetId,
        governanceStatus,
        trainingJobId
    );
  }

  @GetMapping("/{modelId}")
  public ModelRecordResponse get(@PathVariable String modelId, HttpServletRequest request) {
    return modelRecordService.get(modelId, actorContextResolver.resolveRequired(request));
  }

  @GetMapping("/{modelId}/governance-lane")
  public ModelGovernanceLaneResponse governanceLane(@PathVariable String modelId, HttpServletRequest request) {
    return modelRecordService.governanceLane(modelId, actorContextResolver.resolveRequired(request));
  }

  @PatchMapping("/{modelId}/governance")
  public ModelRecordResponse updateGovernance(
      @PathVariable String modelId,
      @Valid @RequestBody UpdateModelGovernanceRequest request,
      HttpServletRequest servletRequest
  ) {
    return modelRecordService.updateGovernance(
        modelId,
        actorContextResolver.resolveRequired(servletRequest),
        request
    );
  }
}
