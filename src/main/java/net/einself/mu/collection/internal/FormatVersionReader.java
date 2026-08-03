package net.einself.mu.collection.internal;

import io.github.wasabithumb.jtoml.JToml;
import io.github.wasabithumb.jtoml.value.TomlValue;
import io.github.wasabithumb.jtoml.value.table.TomlTable;
import net.einself.mu.collection.api.CollectionRoot;
import net.einself.mu.shared.ExitCode;
import net.einself.mu.collection.api.CollectionRoot;
import net.einself.mu.shared.MuException;

/**
 * Reads and checks the {@code format} value of {@code meta/.mu} (SPEC.md section 4.0).
 *
 * <p>A value higher than {@link #IMPLEMENTED_VERSION} makes the tool refuse to write, rather
 * than silently degrade.
 */
public class FormatVersionReader {

    public static final long IMPLEMENTED_VERSION = 1L;

    private final JToml toml;

    public FormatVersionReader(JToml toml) {
        this.toml = toml;
    }

    public long read(CollectionRoot root) {
        long format = parse(root);
        if (format > IMPLEMENTED_VERSION) {
            throw new MuException(ExitCode.USAGE,
                    "Collection uses format " + format + ", this tool implements "
                            + IMPLEMENTED_VERSION + ": " + root.marker());
        }
        return format;
    }

    private long parse(CollectionRoot root) {
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

}
