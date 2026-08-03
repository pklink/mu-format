package net.einself.mu.storage.api;

import org.jmolecules.ddd.annotation.Repository;

import java.nio.file.Path;

@Repository
public interface BlobRepository {

    void clearStaging();

    Blob store(Path source);

    Blob inspect(Path source);
}
