package com.brainweb3.backend.chain;

import com.brainweb3.backend.access.ActorContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chain-records")
public class ChainRecordController {

  private final ActorContextResolver actorContextResolver;
  private final ChainBusinessRecordService chainBusinessRecordService;

  public ChainRecordController(
      ActorContextResolver actorContextResolver,
      ChainBusinessRecordService chainBusinessRecordService
  ) {
    this.actorContextResolver = actorContextResolver;
    this.chainBusinessRecordService = chainBusinessRecordService;
  }

  @GetMapping
  public List<ChainBusinessRecordResponse> list(
      @RequestParam(required = false) String datasetId,
      @RequestParam(required = false) String eventType,
      @RequestParam(required = false) String anchorStatus,
      @RequestParam(required = false) String businessStatus,
      @RequestParam(required = false) String chainTxHash,
      HttpServletRequest servletRequest
  ) {
    return chainBusinessRecordService.list(
        actorContextResolver.resolveRequired(servletRequest),
        datasetId,
        eventType,
        anchorStatus,
        businessStatus,
        chainTxHash
    );
  }

  @PostMapping("/{recordId}/retry")
  public ChainBusinessRecordResponse retry(
      @PathVariable long recordId,
      HttpServletRequest servletRequest
  ) {
    return chainBusinessRecordService.retry(
        recordId,
        actorContextResolver.resolveRequired(servletRequest)
    );
  }
}
