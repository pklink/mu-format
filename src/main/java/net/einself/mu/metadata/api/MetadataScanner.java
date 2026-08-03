package net.einself.mu.metadata.api;

import io.github.wasabithumb.jtoml.JToml;
import net.einself.mu.shared.ExitCode;
import net.einself.mu.shared.MuException;
import net.einself.mu.searchcontext.api.EntityFile;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reads every {@code *.mu} entity file of one meta directory. A file that fails to parse
 * is reported on stderr and skipped: one broken entity must not hide the rest of the
 * collection. Read-only — no lock is taken.
 */
public class MetadataScanner {

    private static final String SUFFIX = ".mu";

    private final JToml toml;

    private final PrintStream err;

    public MetadataScanner(JToml toml, PrintStream err) {
        this.toml = toml;
        this.err = err;
    }

    /**
     * The parsed entity files of {@code directory}, sorted by file name for a stable
     * result order; empty when the directory does not exist.
     */
    public List<EntityFile> scan(Path directory) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        List<Path> files = list(directory);
        List<EntityFile> entities = new ArrayList<>(files.size());
        for (Path file : files) {
            try {
                entities.add(new EntityFile(stem(file), file, toml.read(file)));
            } catch (RuntimeException e) {
                err.println("mu search: skipping " + file + ": " + e.getMessage());
            }
        }
        return entities;
    }

    private static List<Path> list(Path directory) {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*" + SUFFIX)) {
            stream.forEach(files::add);
        } catch (IOException e) {
            throw new MuException(ExitCode.IO_ERROR,
                    "Cannot list " + directory + ": " + e.getMessage(), e);
        }
        files.sort(Comparator.comparing(path -> path.getFileName().toString()));
        return files;
    }

    private static String stem(Path file) {
        String name = file.getFileName().toString();
        return name.substring(0, name.length() - SUFFIX.length());
    }

}
