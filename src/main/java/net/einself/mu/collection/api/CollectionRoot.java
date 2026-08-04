package net.einself.mu.collection.api;

import java.nio.file.Path;

/**
 * The root of a collection: the directory containing {@code meta/.mu} (SPEC.md
 * section 2).
 *
 * <p>
 * {@link #staging()} and {@link #lock()} are not part of the format. SPEC.md
 * sections 3.2 and 4.0 leave unknown entries under {@code store/} and
 * {@code meta/} without meaning, which is what allows this tool to place them
 * there.
 */
public record CollectionRoot(Path path) {

    public static final String MARKER = ".mu";

    public Path store() {
        return path.resolve("store");
    }

    /**
     * Staging directory for blobs, inside the store so that the publishing rename
     * stays on one filesystem.
     */
    public Path staging() {
        return store().resolve(".tmp");
    }

    public Path meta() {
        return path.resolve("meta");
    }

    public Path marker() {
        return meta().resolve(MARKER);
    }

    public Path releases() {
        return meta().resolve("releases");
    }

    public Path artists() {
        return meta().resolve("artists");
    }

    public Path lock() {
        return meta().resolve(".lock");
    }

}
