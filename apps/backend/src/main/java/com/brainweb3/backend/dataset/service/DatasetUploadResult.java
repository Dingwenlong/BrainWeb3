package com.brainweb3.backend.dataset.service;

import com.brainweb3.backend.dataset.api.DatasetDetailResponse;

public record DatasetUploadResult(
    DatasetDetailResponse dataset,
    String uploadReceipt
) {
}
