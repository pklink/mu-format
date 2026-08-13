package net.einself.mu.collection.internal;

import io.github.wasabithumb.jtoml.JToml;
import net.einself.mu.collection.api.CollectionRoot;
import net.einself.mu.collection.api.CollectionService;
import net.einself.mu.shared.ExitCode;
import net.einself.mu.shared.MuException;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class CollectionServiceImpl implements CollectionService {

    private static final Path MARKER = Path.of("meta", ".mu");

    private final JToml toml;

    public CollectionServiceImpl(JToml toml) {
        this.toml = toml;
    }

    @Override
    public CollectionRoot findRoot(Path workingDirectory) {
        return findRoot(null, workingDirectory);
    }

    @Override
    public CollectionRoot findRoot(@Nullable Path explicitRoot, Path workingDirectory) {
        return explicitRoot != null
                                        ? fromExplicit(explicitRoot)
                                        : searchUpwards(workingDirectory);
    }

    @Override
    public long readFormatVersion(CollectionRoot root) {
        FormatVersionReader reader = new FormatVersionReader(toml);
        return reader.read(root);
    }

    private CollectionRoot fromExplicit(Path explicitRoot) {
        CollectionRoot root = new CollectionRoot(absolute(explicitRoot));
        if (!Files.isRegularFile(root.marker())) {
            throw new MuException(ExitCode.USAGE,
                                            "Not a mu collection (no meta/.mu): " + root.path());
        }
        return root;
    }

    private CollectionRoot searchUpwards(Path workingDirectory) {
        for (Path candidate = absolute(workingDirectory); candidate != null; candidate = candidate.getParent()) {
            CollectionRoot root = new CollectionRoot(candidate);
            if (Files.isRegularFile(root.marker())) {
                return root;
            }
        }
        throw new MuException(ExitCode.USAGE,
                                        "Not a mu collection (no meta/.mu found in " + absolute(workingDirectory)
                                                                        + " or any parent directory)");
    }

    private static Path absolute(Path path) {
        try {
            return path.toAbsolutePath().normalize().toRealPath();
        } catch (IOException e) {
            return path.toAbsolutePath().normalize();
        }
    }
}
