package com.brainweb3.backend.chain;

public interface ChainGateway {

  ChainRegistrationReceipt registerDataAsset(ChainRegistrationCommand command);

  ChainBusinessEventReceipt recordBusinessEvent(ChainBusinessEventCommand command);

  ChainRuntimeStatus describeStatus();
}
