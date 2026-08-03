package net.einself.mu.collection.internal;

import net.einself.mu.collection.api.CollectionRoot;
import net.einself.mu.shared.ExitCode;
import net.einself.mu.collection.api.CollectionRoot;
import net.einself.mu.shared.MuException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Locates the collection root: an explicit {@code --root}, or the nearest enclosing directory
 * containing {@code meta/.mu}, searching upwards the way git does (IMPLEMENTATION.md section 35).
 */
public class CollectionRootFinder {

    public CollectionRoot find(Path explicitRoot, Path workingDirectory) {
        return explicitRoot != null
                ? fromExplicit(explicitRoot)
                : searchUpwards(workingDirectory);
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
