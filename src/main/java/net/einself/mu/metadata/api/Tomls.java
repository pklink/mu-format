package net.einself.mu.metadata.api;

import io.github.wasabithumb.jtoml.value.TomlValue;
import io.github.wasabithumb.jtoml.value.primitive.TomlPrimitive;
import io.github.wasabithumb.jtoml.value.table.TomlTable;

import java.util.Map;

/**
 * Null-safe accessors for parsed entity TOML. Entity files are edited by hand
 * (SPEC.md section 1), so a search must tolerate a missing or mistyped attribute instead
 * of failing on it.
 */
public final class Tomls {

    private Tomls() {
    }

    /**
     * The string primitive mapped to {@code key}, or null when absent or not a string.
     */
    public static String string(TomlTable table, String key) {
        TomlValue value = table.get(key);
        if (value == null || !value.isPrimitive()) {
            return null;
        }
        TomlPrimitive primitive = value.asPrimitive();
        return primitive.isString() ? primitive.asString() : null;
    }

    /**
     * Any primitive mapped to {@code key} rendered as a string — for the dual-typed
     * {@code disc} (integer or string, SPEC.md section 4.2) and similar cases.
     */
    public static String scalar(TomlTable table, String key) {
        TomlValue value = table.get(key);
        if (value == null || !value.isPrimitive()) {
            return null;
        }
        TomlPrimitive primitive = value.asPrimitive();
        if (primitive.isString()) {
            return primitive.asString();
        }
        if (primitive.isInteger()) {
            return Long.toString(primitive.asLong());
        }
        if (primitive.isBoolean()) {
            return Boolean.toString(primitive.asBoolean());
        }
        return null;
    }

    /**
     * Adds {@code key → value} to {@code fields} unless {@code value} is null, keeping
     * display maps free of absent attributes.
     */
    public static void put(Map<String, String> fields, String key, String value) {
        if (value != null) {
            fields.put(key, value);
        }
    }

}
