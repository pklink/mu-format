package net.einself.mu.importcontext.internal;

import net.einself.mu.shared.ExitCode;
import net.einself.mu.shared.MuException;
import net.einself.mu.naming.api.Nfc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Collects the files to import, recursively (IMPLEMENTATION.md section 2, step 1).
 *
 * <p>Order is by NFC-normalized relative path in code point order, so that track numbering and
 * the resulting entity file do not depend on directory listing order.
 */
public class SourceFileCollector {

    private final FileClassifier fileClassifier;

    public SourceFileCollector(FileClassifier fileClassifier) {
        this.fileClassifier = fileClassifier;
    }

    public List<SourceFile> collect(List<Path> arguments) {
        List<SourceFile> collected = new ArrayList<>();
        for (Path argument : arguments) {
            collectInto(argument, collected);
        }
        collected.sort(Comparator.comparing(file -> Nfc.normalize(file.relativePath())));
        return List.copyOf(collected);
    }

    private void collectInto(Path argument, List<SourceFile> collected) {
        if (Files.isDirectory(argument)) {
            collectDirectory(argument, collected);
            return;
        }
        if (Files.isRegularFile(argument)) {
            collected.add(toSourceFile(argument, argument.getFileName().toString()));
            return;
        }
        throw new MuException(ExitCode.USAGE, "Not a file or directory: " + argument);
    }

    private void collectDirectory(Path directory, List<SourceFile> collected) {
        try (Stream<Path> walk = Files.walk(directory)) {
            walk.filter(Files::isRegularFile)
                    .map(file -> toSourceFile(file, relativize(directory, file)))
                    .forEach(collected::add);
        } catch (IOException e) {
            throw new MuException(ExitCode.IO_ERROR,
                    "Cannot read " + directory + ": " + e.getMessage(), e);
        }
    }

    private SourceFile toSourceFile(Path path, String relativePath) {
        return new SourceFile(path, relativePath, fileClassifier.apply(path.getFileName().toString()));
    }

    /**
     * Always {@code /}-separated, independent of the platform separator (SPEC.md section 4.9).
     */
    private static String relativize(Path directory, Path file) {
        Path relative = directory.relativize(file);
        List<String> segments = new ArrayList<>();
        for (Path segment : relative) {
            segments.add(segment.toString());
        }
        return String.join("/", segments);
    }

}
