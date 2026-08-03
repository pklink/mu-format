package net.einself.mu.importcontext.internal;

import net.einself.mu.naming.internal.ExtensionDeriver;

/**
 * Guesses the {@code kind} of an asset from its extension (SPEC.md section 4.8).
 *
 * <p>The vocabulary is open, so a wrong guess is valid TOML and is corrected by editing the
 * entity file rather than by re-importing.
 */
public class AssetKindMapper {

    private final ExtensionDeriver extensionDeriver;

    private final FileClassifier fileClassifier;

    public AssetKindMapper(ExtensionDeriver extensionDeriver, FileClassifier fileClassifier) {
        this.extensionDeriver = extensionDeriver;
        this.fileClassifier = fileClassifier;
    }

    public String map(String filename) {
        if (fileClassifier.apply(filename) == FileKind.IMAGE) {
            return "scan";
        }
        return extensionDeriver.derive(filename)
                .map(AssetKindMapper::byExtension)
                .orElse("other");
    }

    private static String byExtension(String extension) {
        return switch (extension) {
            case "log" -> "log";
            case "cue" -> "cue";
            default -> "other";
        };
    }

}
