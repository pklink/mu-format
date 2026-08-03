package net.einself.mu.searchcontext.api;

import io.github.wasabithumb.jtoml.JToml;
import net.einself.mu.metadata.api.MetadataModule;
import net.einself.mu.metadata.api.MetadataScanner;
import java.io.PrintStream;

public final class SearchModule {

    private SearchModule() {}

    public static MetadataScanner createScanner(JToml toml, PrintStream err) {
        return MetadataModule.createScanner(toml, err);
    }
}
