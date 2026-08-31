package net.einself.mu.collection.internal;

import io.github.wasabithumb.jtoml.JToml;
import io.github.wasabithumb.jtoml.value.TomlValue;
import io.github.wasabithumb.jtoml.value.table.TomlTable;
import net.einself.mu.collection.api.CollectionRoot;
import net.einself.mu.collection.api.CollectionService;
import net.einself.mu.shared.ExitCode;
import net.einself.mu.shared.MuException;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class CollectionServiceImpl implements CollectionService {

    private static final long IMPLEMENTED_VERSION = 1L;

    private final JToml toml;

    public CollectionServiceImpl(JToml toml) {
        this.toml = toml;
    }

    @Override
    public CollectionRoot findRoot(@Nullable Path explicitRoot, Path workingDirectory) {
        return explicitRoot != null
                                        ? fromExplicit(explicitRoot)
                                        : searchUpwards(workingDirectory);
    }

    @Override
    public void checkFormatVersion(CollectionRoot root) {
        long format = parseFormat(root);
        if (format > IMPLEMENTED_VERSION) {
            throw new MuException(ExitCode.USAGE,
                                            "Collection uses format " + format + ", this tool implements "
                                                                            + IMPLEMENTED_VERSION + ": " + root.marker());
        }
    }

    private long parseFormat(CollectionRoot root) {
        TomlTable marker;
        try {
            marker = toml.read(root.marker());
        } catch (RuntimeException e) {
            throw new MuException(ExitCode.USAGE, "Cannot read " + root.marker() + ": " + e.getMessage(), e);
        }

        TomlValue value = marker.get("format");
        if (value == null || !value.isPrimitive() || !value.asPrimitive().isInteger()) {
            throw new MuException(ExitCode.USAGE,
                                            "Missing or non-integer 'format' in " + root.marker());
        }
        return value.asPrimitive().asLong();
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