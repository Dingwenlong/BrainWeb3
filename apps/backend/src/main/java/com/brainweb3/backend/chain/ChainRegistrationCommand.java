package com.brainweb3.backend.chain;

import java.time.Instant;
import java.util.List;

public record ChainRegistrationCommand(
    String datasetId,
    String subjectCode,
    String title,
    String ownerOrganization,
    String format,
    String fingerprint,
    String offChainReference,
    List<String> tags,
    Instant uploadedAt
) {
}
