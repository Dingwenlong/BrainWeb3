package com.brainweb3.backend.chain;

import java.util.List;

public record ChainRuntimeStatus(
    String provider,
    boolean enabled,
    String mode,
    String group,
    String contractName,
    String contractAddress,
    List<String> rpcPeers,
    String transportSecurity
) {
}
