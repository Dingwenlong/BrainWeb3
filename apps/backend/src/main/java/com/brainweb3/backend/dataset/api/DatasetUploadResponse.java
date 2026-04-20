package com.brainweb3.backend.dataset.api;

public record DatasetUploadResponse(
    DatasetDetailResponse dataset,
    String uploadReceipt
) {
}
