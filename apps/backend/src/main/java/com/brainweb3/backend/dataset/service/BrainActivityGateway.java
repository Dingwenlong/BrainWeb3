package com.brainweb3.backend.dataset.service;

import com.brainweb3.backend.dataset.api.BrainActivityResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@Service
public class BrainActivityGateway {

  private final RestTemplate eegRestTemplate;

  public BrainActivityGateway(RestTemplate eegRestTemplate) {
    this.eegRestTemplate = eegRestTemplate;
  }

  public BrainActivityResponse getBrainActivity(
      String datasetId,
      String band,
      double windowSize,
      double stepSize,
      Double timeStart,
      Double timeEnd,
      String sourceUri
  ) {
    UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/api/v1/datasets/{datasetId}/brain-activity")
        .queryParam("band", band)
        .queryParam("windowSize", windowSize)
        .queryParam("stepSize", stepSize)
        .queryParam("sourceUri", sourceUri);

    if (timeStart != null) {
      builder.queryParam("timeStart", timeStart);
    }
    if (timeEnd != null) {
      builder.queryParam("timeEnd", timeEnd);
    }

    String path = builder.buildAndExpand(datasetId).toUriString();

    try {
      return eegRestTemplate.getForObject(path, BrainActivityResponse.class);
    } catch (RestClientException exception) {
      throw new ResponseStatusException(
          BAD_GATEWAY,
          "EEG brain activity service is unavailable.",
          exception
      );
    }
  }
}
