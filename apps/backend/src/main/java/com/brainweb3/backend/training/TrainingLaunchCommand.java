package com.brainweb3.backend.training;

public record TrainingLaunchCommand(
    String jobId,
    String datasetId,
    String datasetTitle,
    String actorId,
    String actorOrg,
    String algorithm,
    String modelName,
    String objective,
    int requestedRounds
) {
}
