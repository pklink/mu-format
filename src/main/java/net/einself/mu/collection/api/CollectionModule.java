package net.einself.mu.collection.api;

import io.github.wasabithumb.jtoml.JToml;
import net.einself.mu.collection.internal.CollectionServiceImpl;

public final class CollectionModule {

    private CollectionModule() {}

    public static CollectionService createService(JToml toml) {
        return new CollectionServiceImpl(toml);
    }
}
