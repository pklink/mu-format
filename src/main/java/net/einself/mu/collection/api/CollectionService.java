package net.einself.mu.collection.api;

import org.jmolecules.ddd.annotation.Service;

import java.nio.file.Path;

@Service
public interface CollectionService {

    CollectionRoot findRoot(Path workingDirectory);

    CollectionRoot findRoot(Path explicitRoot, Path workingDirectory);

    long readFormatVersion(CollectionRoot root);
}
