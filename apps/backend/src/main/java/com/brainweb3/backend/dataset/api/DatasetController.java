package com.brainweb3.backend.dataset.api;

import com.brainweb3.backend.access.ActorContext;
import com.brainweb3.backend.access.ActorContextResolver;
import com.brainweb3.backend.access.AccessRequestService;
import com.brainweb3.backend.audit.AuditService;
import com.brainweb3.backend.dataset.service.BrainActivityGateway;
import com.brainweb3.backend.dataset.service.DatasetCatalogService;
import com.brainweb3.backend.dataset.service.DatasetUploadResult;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/v1/datasets")
public class DatasetController {

  private final DatasetCatalogService datasetCatalogService;
  private final BrainActivityGateway brainActivityGateway;
  private final ActorContextResolver actorContextResolver;
  private final AccessRequestService accessRequestService;
  private final AuditService auditService;

  public DatasetController(
      DatasetCatalogService datasetCatalogService,
      BrainActivityGateway brainActivityGateway,
      ActorContextResolver actorContextResolver,
      AccessRequestService accessRequestService,
      AuditService auditService
  ) {
    this.datasetCatalogService = datasetCatalogService;
    this.brainActivityGateway = brainActivityGateway;
    this.actorContextResolver = actorContextResolver;
    this.accessRequestService = accessRequestService;
    this.auditService = auditService;
  }

  @GetMapping
  public List<DatasetSummaryResponse> listDatasets() {
    return datasetCatalogService.listDatasets();
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public DatasetUploadResponse uploadDataset(
      @RequestParam MultipartFile file,
      @RequestParam String subjectCode,
      @RequestParam String title,
      @RequestParam(required = false) String description,
      @RequestParam String ownerOrganization,
      @RequestParam(required = false) String tags
  ) {
    DatasetUploadResult result = datasetCatalogService.uploadDataset(
        file,
        subjectCode,
        title,
        description,
        ownerOrganization,
        tags
    );

    return new DatasetUploadResponse(
        result.dataset(),
        result.uploadReceipt()
    );
  }

  @GetMapping("/{datasetId}")
  public DatasetDetailResponse getDataset(@PathVariable String datasetId) {
    return datasetCatalogService.getDataset(datasetId)
        .orElseThrow(() -> new ResponseStatusException(
            NOT_FOUND,
            "Dataset %s was not found.".formatted(datasetId)
        ));
  }

  @PostMapping("/{datasetId}/retry-finalization")
  public DatasetDetailResponse retryFinalization(@PathVariable String datasetId, HttpServletRequest request) {
    return datasetCatalogService.retryFinalization(
        datasetId,
        actorContextResolver.resolveRequired(request)
    );
  }

  @GetMapping("/{datasetId}/brain-activity")
  public BrainActivityResponse getBrainActivity(
      @PathVariable String datasetId,
      @RequestParam(defaultValue = "alpha") String band,
      @RequestParam(defaultValue = "2.0") double windowSize,
      @RequestParam(defaultValue = "0.5") double stepSize,
      @RequestParam(required = false) Double timeStart,
      @RequestParam(required = false) Double timeEnd,
      HttpServletRequest request
  ) {
    String sourceUri = datasetCatalogService.getDatasetActivitySourceUri(datasetId)
        .orElseThrow(() -> new ResponseStatusException(
            NOT_FOUND,
            "Dataset %s was not found.".formatted(datasetId)
        ));
    ActorContext actor = actorContextResolver.resolveRequired(request);
    if (!accessRequestService.canReadBrainActivity(datasetId, actor)) {
      auditService.record(
          datasetId,
          actor,
          "BRAIN_ACTIVITY_READ",
          "denied",
          "Access request approval is required."
      );
      throw new ResponseStatusException(FORBIDDEN, "Actor is not allowed to read brain activity.");
    }

    auditService.record(
        datasetId,
        actor,
        "BRAIN_ACTIVITY_READ",
        "granted",
        "Band %s requested.".formatted(band)
    );
    return brainActivityGateway.getBrainActivity(
        datasetId,
        band,
        windowSize,
        stepSize,
        timeStart,
        timeEnd,
        sourceUri
    );
  }
}
