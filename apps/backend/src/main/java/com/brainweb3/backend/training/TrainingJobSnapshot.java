package com.brainweb3.backend.training;

public record TrainingJobSnapshot(
    String status,
    int completedRounds,
    String latestMessage,
    String metricSummary,
    String resultSummary
) {
}
