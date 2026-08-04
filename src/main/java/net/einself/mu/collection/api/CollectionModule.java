package net.einself.mu.collection.api;

import io.github.wasabithumb.jtoml.JToml;
import net.einself.mu.collection.internal.CollectionLock;
import net.einself.mu.collection.internal.CollectionServiceImpl;

public final class CollectionModule {

    private CollectionModule() {
    }

    public static CollectionService createService(JToml toml) {
        return new CollectionServiceImpl(toml);
    }

    public static LockHandle acquireLock(CollectionRoot root) {
        return CollectionLock.acquire(root);
    }
}
