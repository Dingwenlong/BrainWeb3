package com.brainweb3.backend.storage;

public record StorageDeleteCommand(
    String datasetId,
    String storageKey,
    String provider
) {
}
