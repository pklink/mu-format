package net.einself.mu.metadata.api;

import io.github.wasabithumb.jtoml.value.table.TomlTable;

import java.nio.file.Path;

/**
 * A parsed entity file: {@code meta/releases/<id>.mu} or
 * {@code meta/artists/<id>.mu}.
 *
 * @param id
 *            the filename stem, which is the entity's identity (SPEC.md section
 *            4.2)
 * @param path
 *            the entity file
 * @param data
 *            the parsed TOML
 */
public record EntityFile(String id, Path path, TomlTable data) {
}
