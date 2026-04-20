package com.brainweb3.backend.dataset.api;

public record DataAssetProofResponse(
    String chainProvider,
    String chainGroup,
    String contractName,
    String contractAddress,
    String sm3Hash,
    String ipfsCid,
    String offChainReference,
    String chainTxHash,
    String didHolder,
    String accessPolicy,
    String auditState
) {
}
