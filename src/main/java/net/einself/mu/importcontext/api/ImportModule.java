package net.einself.mu.importcontext.api;

import io.github.wasabithumb.jtoml.JToml;
import net.einself.mu.collection.api.CollectionRoot;
import net.einself.mu.importcontext.internal.ImportServiceImpl;
import net.einself.mu.metadata.api.MetadataModule;
import net.einself.mu.metadata.api.ReleaseRepository;
import net.einself.mu.storage.api.BlobRepository;
import net.einself.mu.storage.api.StorageModule;

import java.io.PrintStream;

public final class ImportModule {

    private ImportModule() {}

    public static BlobRepository createBlobRepository(CollectionRoot root) {
        return StorageModule.createRepository(root);
    }

    public static ReleaseRepository createReleaseRepository(JToml toml) {
        return MetadataModule.createReleaseRepository(toml);
    }

    public static ImportService createImportService(JToml toml, PrintStream err) {
        return new ImportServiceImpl(toml, err);
    }
}
