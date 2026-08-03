package net.einself.mu.metadata.api;

import io.github.wasabithumb.jtoml.JToml;
import net.einself.mu.metadata.internal.TomlReleaseRepository;

public final class MetadataModule {

    private MetadataModule() {}

    public static ReleaseRepository createReleaseRepository(JToml toml) {
        return new TomlReleaseRepository(toml);
    }

    public static MetadataScanner createScanner(JToml toml, java.io.PrintStream err) {
        return new MetadataScanner(toml, err);
    }
}
