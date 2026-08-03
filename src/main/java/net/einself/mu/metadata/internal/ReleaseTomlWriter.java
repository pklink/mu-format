package net.einself.mu.metadata.internal;

import io.github.wasabithumb.jtoml.JToml;
import io.github.wasabithumb.jtoml.value.array.TomlArray;
import io.github.wasabithumb.jtoml.value.primitive.TomlPrimitive;
import io.github.wasabithumb.jtoml.value.table.TomlTable;
import net.einself.mu.shared.ExitCode;
import net.einself.mu.shared.MuException;
import net.einself.mu.collection.api.CollectionRoot;
import net.einself.mu.naming.api.Nfc;
import net.einself.mu.metadata.api.Release;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

/**
 * Renders a {@link Release} to its entity file (SPEC.md section 4).
 *
 * <p>Written as UTF-8 without BOM with LF line endings, and published by rename so that a
 * reader never observes a half-written entity file. Key order inside each table is jtoml's:
 * primitives first, then arrays of tables, each group in lexicographical order — deterministic,
 * which is what keeps git diffs meaningful (SPEC.md section 6).
 */
public class ReleaseTomlWriter {

    private final JToml toml;

    public ReleaseTomlWriter(JToml toml) {
        this.toml = toml;
    }

    public String render(Release release) {
        return toml.writeToString(toTable(release));
    }

    /**
     * Writes {@code meta/releases/<id>.mu}, creating the directory if needed.
     */
    public Path write(CollectionRoot root, Release release) {
        Path target = root.releases().resolve(release.id() + ".mu");
        Path temp = root.meta().resolve(".tmp-" + UUID.randomUUID());
        try {
            Files.createDirectories(root.releases());
            try (OutputStream out = Files.newOutputStream(temp)) {
                toml.write(out, toTable(release));
            }
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException e) {
            deleteQuietly(temp);
            throw new MuException(ExitCode.IO_ERROR,
                    "Cannot write " + target + ": " + e.getMessage(), e);
        }
    }

    private static TomlTable toTable(Release release) {
        TomlTable table = TomlTable.create();
        putString(table, "title", release.title());
        putString(table, "origin-dir", release.originDir());
        putString(table, "cover-front", release.coverFront());
        putString(table, "cover-front-origin-path", release.coverFrontOriginPath());

        putTables(table, "credit", release.credits(), ReleaseTomlWriter::toTable);
        putTables(table, "asset", release.assets(), ReleaseTomlWriter::toTable);
        putTables(table, "track", release.tracks(), ReleaseTomlWriter::toTable);
        return table;
    }

    private static TomlTable toTable(Release.Credit credit) {
        TomlTable table = TomlTable.create();
        putString(table, "role", credit.role());
        putString(table, "artist", credit.artist());
        return table;
    }

    private static TomlTable toTable(Release.Asset asset) {
        TomlTable table = TomlTable.create();
        putString(table, "kind", asset.kind());
        putString(table, "blob", asset.blob());
        putString(table, "origin-path", asset.originPath());
        return table;
    }

    private static TomlTable toTable(Release.Track track) {
        TomlTable table = TomlTable.create();
        putDisc(table, track.disc());
        table.put("number", TomlPrimitive.of(track.number()));
        putString(table, "blob", track.blob());
        putString(table, "title", track.title());
        putString(table, "origin-path", track.originPath());
        return table;
    }

    /**
     * {@code disc} is the only dual-typed attribute (SPEC.md section 4.2).
     */
    private static void putDisc(TomlTable table, Object disc) {
        if (disc instanceof Integer number) {
            table.put("disc", TomlPrimitive.of(number.intValue()));
        } else if (disc instanceof String side) {
            putString(table, "disc", side);
        }
    }

    private static <T> void putTables(TomlTable table, String key, List<T> values,
                                      java.util.function.Function<T, TomlTable> mapper) {
        if (values.isEmpty()) {
            return;
        }
        TomlArray array = TomlArray.create();
        values.stream().map(mapper).forEach(array::add);
        table.put(key, array);
    }

    private static void putString(TomlTable table, String key, String value) {
        if (value != null) {
            table.put(key, TomlPrimitive.of(Nfc.normalize(value)));
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best effort
        }
    }

}
