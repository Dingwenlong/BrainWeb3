package com.brainweb3.backend.storage;

public record StoragePersistReceipt(
    String provider,
    String storageKey,
    String storageUri,
    String offChainReference
) {
}
