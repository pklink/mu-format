package net.einself.mu.importer;

import java.util.Set;
import java.util.function.Function;

/**
 * Classifies a source file by extension (IMPLEMENTATION.md section 2, step 1).
 */
public class FileClassifier implements Function<String, FileKind> {

    private static final Set<String> AUDIO = Set.of(
            "flac", "mp3", "m4a", "m4b", "aac", "ogg", "oga", "opus", "wav", "wave",
            "aiff", "aif", "aifc", "ape", "wv", "wma", "alac", "dsf", "dff", "mpc", "tta", "shn");

    private static final Set<String> IMAGE = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "tif", "tiff", "bmp", "heic", "avif");

    private final ExtensionDeriver extensionDeriver;

    public FileClassifier(ExtensionDeriver extensionDeriver) {
        this.extensionDeriver = extensionDeriver;
    }

    @Override
    public FileKind apply(String filename) {
        return extensionDeriver.derive(filename)
                .map(FileClassifier::byExtension)
                .orElse(FileKind.OTHER);
    }

    private static FileKind byExtension(String extension) {
        if (AUDIO.contains(extension)) {
            return FileKind.AUDIO;
        }
        if (IMAGE.contains(extension)) {
            return FileKind.IMAGE;
        }
        return FileKind.OTHER;
    }

}
