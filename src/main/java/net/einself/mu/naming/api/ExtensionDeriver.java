package net.einself.mu.naming.api;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Derives the extension of a blob reference from the source filename.
 *
 * <p>
 * The extension is not part of the blob's identity and has no effect on
 * resolution; it is a rendering hint (SPEC.md section 4.4). This heuristic
 * trusts the filename. When no qualifying extension can be derived, the
 * reference falls back to {@value #FALLBACK}.
 */
public class ExtensionDeriver {

    private static final Pattern VALID = Pattern.compile("[a-z0-9]{1,8}");

    static final String FALLBACK = "bin";

    public Optional<String> derive(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot <= 0 || dot == filename.length() - 1) {
            return Optional.empty();
        }

        String candidate = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return VALID.matcher(candidate).matches() ? Optional.of(candidate) : Optional.empty();
    }

    public String reference(String hash, String filename) {
        return derive(filename)
                                        .map(extension -> hash + "." + extension)
                                        .orElse(hash + "." + FALLBACK);
    }

}
