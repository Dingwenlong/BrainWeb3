package com.brainweb3.backend.storage;

import com.brainweb3.backend.config.StorageProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class LocalStorageGateway implements StorageGateway {

  private final Path localRoot;

  public LocalStorageGateway(StorageProperties storageProperties) {
    this.localRoot = Path.of(storageProperties.getLocalRoot());
  }

  @Override
  public StoragePersistReceipt persist(StoragePersistCommand command) {
    try {
      String sanitizedFilename = command.originalFilename().replaceAll("[^a-zA-Z0-9._-]", "_");
      String storageKey = "datasets/%s/%s".formatted(command.datasetId(), sanitizedFilename);
      Path targetPath = localRoot.resolve(storageKey);
      Files.createDirectories(targetPath.getParent());
      Files.write(targetPath, command.content());

      return new StoragePersistReceipt(
          "local",
          storageKey,
          targetPath.toAbsolutePath().toString(),
          "local://%s".formatted(storageKey)
      );
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to persist uploaded dataset file.", exception);
    }
  }

  @Override
  public void delete(StorageDeleteCommand command) {
    String storageKey = command.storageKey() == null ? "" : command.storageKey().trim();
    if (storageKey.isBlank()) {
      throw new IllegalStateException("Missing storage key for local deletion.");
    }

    try {
      Path targetPath = localRoot.resolve(storageKey);
      Files.deleteIfExists(targetPath);
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to delete dataset file from local storage.", exception);
    }
  }
}
