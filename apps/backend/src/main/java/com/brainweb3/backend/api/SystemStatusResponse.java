package com.brainweb3.backend.api;

import java.time.Instant;
import java.util.Map;

public record SystemStatusResponse(
    String application,
    String stage,
    Instant generatedAt,
    ChainStatusResponse chain,
    Map<String, String> modules
) {
}
