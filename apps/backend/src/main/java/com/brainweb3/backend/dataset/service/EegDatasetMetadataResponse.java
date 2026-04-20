package com.brainweb3.backend.dataset.service;

import java.time.Instant;
import java.util.List;

public record EegDatasetMetadataResponse(
    String format,
    int samplingRate,
    int channelCount,
    int sampleCount,
    double durationSeconds,
    List<String> qualityFlags,
    Instant generatedAt
) {
}
