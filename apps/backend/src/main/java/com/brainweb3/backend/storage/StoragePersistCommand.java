package com.brainweb3.backend.storage;

public record StoragePersistCommand(
    String datasetId,
    String originalFilename,
    String format,
    String subjectCode,
    byte[] content
) {
}
