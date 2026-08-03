package net.einself.mu.metadata.internal;

import io.github.wasabithumb.jtoml.JToml;
import net.einself.mu.collection.api.CollectionRoot;
import net.einself.mu.metadata.api.Release;
import net.einself.mu.metadata.api.ReleaseRepository;

public class TomlReleaseRepository implements ReleaseRepository {

    private final ReleaseTomlWriter writer;

    public TomlReleaseRepository(JToml toml) {
        this.writer = new ReleaseTomlWriter(toml);
    }

    @Override
    public void save(Release release, CollectionRoot root) {
        writer.write(root, release);
    }

    @Override
    public String render(Release release) {
        return writer.render(release);
    }
}
