package net.einself.mu.importcontext.api;

import net.einself.mu.collection.api.CollectionRoot;
import org.jmolecules.ddd.annotation.Service;

import java.nio.file.Path;
import java.util.List;

@Service
public interface ImportService {

    ImportResult importFiles(CollectionRoot root, List<Path> paths, ImportOptions options);
}
