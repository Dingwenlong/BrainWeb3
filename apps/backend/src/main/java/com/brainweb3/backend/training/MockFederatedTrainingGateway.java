package com.brainweb3.backend.training;

import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class MockFederatedTrainingGateway implements FederatedTrainingGateway {

  @Override
  public TrainingLaunchReceipt submit(TrainingLaunchCommand command) {
    return new TrainingLaunchReceipt(
        "mock-fate",
        "fate-job-%s".formatted(command.jobId().replace("tj-", "")),
        "FATE sandbox accepted %s with %d rounds.".formatted(command.modelName(), command.requestedRounds())
    );
  }

  @Override
  public TrainingJobSnapshot refresh(TrainingJobEntity job) {
    String signal = (job.getModelName() + " " + job.getObjective() + " " + job.getAlgorithm())
        .toLowerCase(Locale.ROOT);
    if (signal.contains("fail") || signal.contains("drift")) {
      return new TrainingJobSnapshot(
          "failed",
          Math.max(1, job.getRequestedRounds() / 2),
          "FATE sandbox reported convergence drift and aborted the run.",
          "AUC 0.51 | PSI drift 0.34",
          "Run aborted after drift guardrail triggered."
      );
    }

    return new TrainingJobSnapshot(
        "succeeded",
        job.getRequestedRounds(),
        "FATE sandbox finished aggregation and exported a demo summary.",
        "AUC 0.91 | F1 0.87",
        "Motor imagery classifier ready for demo replay."
    );
  }
}
