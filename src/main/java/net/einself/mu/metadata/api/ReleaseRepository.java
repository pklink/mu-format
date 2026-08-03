package net.einself.mu.metadata.api;

import net.einself.mu.collection.api.CollectionRoot;
import org.jmolecules.ddd.annotation.Repository;

import java.nio.file.Path;

@Repository
public interface ReleaseRepository {

    void save(Release release, CollectionRoot root);

    String render(Release release);
}
