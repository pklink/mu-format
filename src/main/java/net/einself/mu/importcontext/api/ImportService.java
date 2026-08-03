package net.einself.mu.importcontext.api;

import net.einself.mu.collection.api.CollectionRoot;
import net.einself.mu.metadata.api.Release;
import org.jmolecules.ddd.annotation.Service;

import java.nio.file.Path;
import java.util.List;

@Service
public interface ImportService {

    ImportReport importPaths(CollectionRoot root, List<Path> paths, ImportOptions options);

    String renderRelease(Release release);
}
