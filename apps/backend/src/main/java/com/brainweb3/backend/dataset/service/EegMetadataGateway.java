package com.brainweb3.backend.dataset.service;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class EegMetadataGateway {

  private final RestTemplate eegRestTemplate;

  public EegMetadataGateway(RestTemplate eegRestTemplate) {
    this.eegRestTemplate = eegRestTemplate;
  }

  public Optional<EegDatasetMetadataResponse> getMetadata(String sourceUri) {
    if (sourceUri == null || sourceUri.isBlank()) {
      return Optional.empty();
    }

    String path = UriComponentsBuilder.fromPath("/api/v1/eeg/metadata")
        .queryParam("sourceUri", sourceUri)
        .build()
        .toUriString();

    try {
      return Optional.ofNullable(eegRestTemplate.getForObject(path, EegDatasetMetadataResponse.class));
    } catch (RestClientException exception) {
      return Optional.empty();
    }
  }
}
