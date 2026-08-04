package net.einself.mu.storage.api;

import net.einself.mu.collection.api.CollectionRoot;
import net.einself.mu.storage.internal.FileSystemBlobStore;

public final class StorageModule {

    private StorageModule() {
    }

    public static BlobRepository createRepository(CollectionRoot root) {
        return new FileSystemBlobStore(root);
    }
}
