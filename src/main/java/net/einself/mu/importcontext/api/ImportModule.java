package net.einself.mu.importcontext.api;

import io.github.wasabithumb.jtoml.JToml;
import net.einself.mu.importcontext.internal.ImportServiceImpl;
import net.einself.mu.metadata.api.MetadataModule;
import net.einself.mu.metadata.api.ReleaseRepository;

import java.io.PrintStream;

public final class ImportModule {

    private ImportModule() {
    }

    public static ReleaseRepository createReleaseRepository(JToml toml) {
        return MetadataModule.createReleaseRepository(toml);
    }

    public static ImportService createImportService(JToml toml, PrintStream err) {
        return new ImportServiceImpl(toml, err);
    }
}
