package com.brainweb3.backend.chain;

import java.time.Instant;

public record ChainBusinessEventReceipt(
    String chainProvider,
    String chainGroup,
    String contractName,
    String contractAddress,
    String eventHash,
    String chainTxHash,
    Instant anchoredAt
) {
}
