package com.brainweb3.backend.chain;

import java.util.List;
import java.util.Locale;

public class MockChainGateway implements ChainGateway {

  @Override
  public ChainRegistrationReceipt registerDataAsset(ChainRegistrationCommand command) {
    return new ChainRegistrationReceipt(
        "mock",
        "sandbox",
        "MockDataNotary",
        "mock://contracts/data-notary",
        "SM3:%s".formatted(command.fingerprint().substring(0, 32)),
        "bafy%s".formatted(command.fingerprint().substring(0, 20)),
        command.offChainReference(),
        "0x%s".formatted(command.fingerprint().substring(0, 24)),
        "did:brainweb3:%s".formatted(slugify(command.ownerOrganization())),
        "owner-review required before training",
        "fresh-upload"
    );
  }

  @Override
  public ChainRuntimeStatus describeStatus() {
    return new ChainRuntimeStatus(
        "mock",
        false,
        "mock-active",
        "sandbox",
        "MockDataNotary",
        "mock://contracts/data-notary",
        List.of(),
        "simulated"
    );
  }

  private String slugify(String input) {
    return input.toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", "-")
        .replaceAll("^-|-$", "");
  }
}
