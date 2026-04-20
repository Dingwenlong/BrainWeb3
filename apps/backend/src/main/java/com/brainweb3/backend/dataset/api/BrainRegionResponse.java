package com.brainweb3.backend.dataset.api;

import java.util.List;

public record BrainRegionResponse(
    String code,
    String label,
    List<String> electrodes
) {
}
