package com.brainweb3.backend.api;

import java.util.List;

public record ChainStatusResponse(
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
