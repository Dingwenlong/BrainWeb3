package com.brainweb3.backend.training;

public interface FederatedTrainingGateway {

  TrainingLaunchReceipt submit(TrainingLaunchCommand command);

  TrainingJobSnapshot refresh(TrainingJobEntity job);
}
