package net.einself.mu.importcontext.internal;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Selects the image that becomes {@code cover-front} (IMPLEMENTATION.md section 2, step 5).
 *
 * <p>The first image in filename order whose stem is {@code cover}, {@code front} or
 * {@code folder}. Every other image becomes an asset.
 */
public class CoverFrontSelector {

    private static final Set<String> COVER_NAMES = Set.of("cover", "front", "folder");

    public Optional<SourceFile> select(List<SourceFile> files) {
        return files.stream()
                .filter(file -> file.kind() == FileKind.IMAGE)
                .filter(CoverFrontSelector::hasCoverName)
                .findFirst();
    }

    private static boolean hasCoverName(SourceFile file) {
        return COVER_NAMES.contains(stem(file.filename()).toLowerCase(Locale.ROOT));
    }

    static String stem(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot <= 0 ? filename : filename.substring(0, dot);
    }

}
