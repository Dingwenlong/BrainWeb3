package com.brainweb3.backend.training;

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
@RequestMapping("/api/v1/training-jobs")
public class TrainingJobController {

  private final ActorContextResolver actorContextResolver;
  private final TrainingJobService trainingJobService;

  public TrainingJobController(
      ActorContextResolver actorContextResolver,
      TrainingJobService trainingJobService
  ) {
    this.actorContextResolver = actorContextResolver;
    this.trainingJobService = trainingJobService;
  }

  @GetMapping
  public List<TrainingJobResponse> list(
      @RequestParam(required = false) String datasetId,
      @RequestParam(required = false) String status,
      HttpServletRequest request
  ) {
    return trainingJobService.list(actorContextResolver.resolveRequired(request), datasetId, status);
  }

  @GetMapping("/{jobId}")
  public TrainingJobResponse get(@PathVariable String jobId, HttpServletRequest request) {
    return trainingJobService.get(jobId, actorContextResolver.resolveRequired(request));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public TrainingJobResponse create(
      @Valid @RequestBody CreateTrainingJobRequest request,
      HttpServletRequest servletRequest
  ) {
    return trainingJobService.create(actorContextResolver.resolveRequired(servletRequest), request);
  }

  @PostMapping("/{jobId}/refresh")
  public TrainingJobResponse refresh(@PathVariable String jobId, HttpServletRequest request) {
    return trainingJobService.refresh(jobId, actorContextResolver.resolveRequired(request));
  }
}
