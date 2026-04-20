package com.brainweb3.backend.dataset.api;

import java.util.Map;

public record ActivityFrameResponse(
    double timestamp,
    Map<String, Double> intensities
) {
}
