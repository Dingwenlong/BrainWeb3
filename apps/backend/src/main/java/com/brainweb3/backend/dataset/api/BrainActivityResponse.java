package com.brainweb3.backend.dataset.api;

import java.time.Instant;
import java.util.List;

public record BrainActivityResponse(
    String datasetId,
    int samplingRate,
    String band,
    double windowSize,
    double stepSize,
    List<BrainRegionResponse> regions,
    List<ActivityFrameResponse> frames,
    List<String> qualityFlags,
    Instant generatedAt
) {
}
