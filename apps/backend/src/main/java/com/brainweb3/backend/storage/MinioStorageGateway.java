package com.brainweb3.backend.storage;

import com.brainweb3.backend.config.StorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.ByteArrayInputStream;

public class MinioStorageGateway implements StorageGateway {

  private final StorageProperties storageProperties;
  private final MinioClient minioClient;
  private final Object monitor = new Object();

  private volatile boolean bucketReady;

  public MinioStorageGateway(StorageProperties storageProperties) {
    this.storageProperties = storageProperties;
    this.minioClient = MinioClient.builder()
        .endpoint(requireValue(storageProperties.getMinioEndpoint(), "brainweb3.storage.minio-endpoint"))
        .credentials(
            requireValue(storageProperties.getMinioAccessKey(), "brainweb3.storage.minio-access-key"),
            requireValue(storageProperties.getMinioSecretKey(), "brainweb3.storage.minio-secret-key")
        )
        .build();
  }

  @Override
  public StoragePersistReceipt persist(StoragePersistCommand command) {
    try {
      ensureBucketReady();
      String sanitizedFilename = command.originalFilename().replaceAll("[^a-zA-Z0-9._-]", "_");
      String storageKey = "datasets/%s/%s".formatted(command.datasetId(), sanitizedFilename);
      byte[] content = command.content();
      String bucket = normalizedBucket();
      minioClient.putObject(
          PutObjectArgs.builder()
              .bucket(bucket)
              .object(storageKey)
              .stream(new ByteArrayInputStream(content), content.length, -1)
              .contentType(resolveContentType(command.format()))
              .build()
      );

      String objectUrl = "%s/%s/%s".formatted(
          normalizedEndpoint(),
          bucket,
          storageKey
      );

      return new StoragePersistReceipt(
          "minio",
          storageKey,
          objectUrl,
          "minio://%s/%s".formatted(bucket, storageKey)
      );
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to persist uploaded dataset file to MinIO.", exception);
    }
  }

  @Override
  public void delete(StorageDeleteCommand command) {
    String storageKey = normalize(command.storageKey());
    if (storageKey.isBlank()) {
      throw new IllegalStateException("Missing storage key for MinIO deletion.");
    }

    try {
      ensureBucketReady();
      minioClient.removeObject(
          RemoveObjectArgs.builder()
              .bucket(normalizedBucket())
              .object(storageKey)
              .build()
      );
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to delete dataset file from MinIO.", exception);
    }
  }

  private void ensureBucketReady() throws Exception {
    if (bucketReady) {
      return;
    }

    synchronized (monitor) {
      if (bucketReady) {
        return;
      }

      String bucket = normalizedBucket();
      boolean exists = minioClient.bucketExists(
          BucketExistsArgs.builder().bucket(bucket).build()
      );
      if (!exists) {
        if (!storageProperties.isMinioAutoCreateBucket()) {
          throw new IllegalStateException(
              "Configured MinIO bucket does not exist and auto creation is disabled."
          );
        }

        MakeBucketArgs.Builder makeBucketArgs = MakeBucketArgs.builder().bucket(bucket);
        String region = normalize(storageProperties.getMinioRegion());
        if (!region.isBlank()) {
          makeBucketArgs.region(region);
        }
        minioClient.makeBucket(makeBucketArgs.build());
      }

      bucketReady = true;
    }
  }

  private String resolveContentType(String format) {
    return switch (normalize(format).toUpperCase()) {
      case "GDF" -> "application/gdf";
      case "BDF" -> "application/bdf";
      default -> "application/edf";
    };
  }

  private String normalizedEndpoint() {
    String endpoint = requireValue(storageProperties.getMinioEndpoint(), "brainweb3.storage.minio-endpoint");
    return endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
  }

  private String normalizedBucket() {
    return requireValue(storageProperties.getMinioBucket(), "brainweb3.storage.minio-bucket");
  }

  private String requireValue(String value, String propertyName) {
    String normalized = normalize(value);
    if (normalized.isBlank()) {
      throw new IllegalStateException("Missing required storage property: " + propertyName);
    }
    return normalized;
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim();
  }
}
