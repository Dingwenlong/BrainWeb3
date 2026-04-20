package com.brainweb3.backend.chain;

public record ChainRegistrationReceipt(
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
