package net.einself.mu.importcontext.api;

import net.einself.mu.collection.api.CollectionRoot;
import net.einself.mu.metadata.api.MetadataModule;
import net.einself.mu.metadata.api.ReleaseRepository;
import net.einself.mu.storage.api.BlobRepository;
import net.einself.mu.storage.api.StorageModule;

public final class ImportModule {

    private ImportModule() {}

    public static BlobRepository createBlobRepository(CollectionRoot root) {
        return StorageModule.createRepository(root);
    }

    public static ReleaseRepository createReleaseRepository(io.github.wasabithumb.jtoml.JToml toml) {
        return MetadataModule.createReleaseRepository(toml);
    }
}
