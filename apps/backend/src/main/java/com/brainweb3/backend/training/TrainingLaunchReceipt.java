package com.brainweb3.backend.training;

public record TrainingLaunchReceipt(
    String orchestrator,
    String externalJobRef,
    String latestMessage
) {
}
