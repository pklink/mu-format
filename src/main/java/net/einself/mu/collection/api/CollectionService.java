package net.einself.mu.collection.api;

import org.jmolecules.ddd.annotation.Service;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

@Service
public interface CollectionService {

    CollectionRoot findRoot(Path workingDirectory);

    CollectionRoot findRoot(@Nullable Path explicitRoot, Path workingDirectory);

    long readFormatVersion(CollectionRoot root);
}
