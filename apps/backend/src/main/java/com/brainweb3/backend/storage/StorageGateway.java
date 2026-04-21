package com.brainweb3.backend.storage;

public interface StorageGateway {

  StoragePersistReceipt persist(StoragePersistCommand command);

  void delete(StorageDeleteCommand command);
}
