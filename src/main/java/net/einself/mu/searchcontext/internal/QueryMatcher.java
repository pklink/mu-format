package net.einself.mu.searchcontext.internal;

import io.github.wasabithumb.jtoml.value.TomlValue;
import io.github.wasabithumb.jtoml.value.table.TomlTable;
import net.einself.mu.metadata.api.Tomls;
import net.einself.mu.naming.api.Nfc;

import java.util.Locale;

/**
 * Case-insensitive substring matching. Both the query and every candidate value
 * are NFC-normalized before comparison (SPEC.md section 4.3), so a query
 * matches a value regardless of the normalization form either was typed or
 * stored in.
 */
public class QueryMatcher {

    private final String query;

    public QueryMatcher(String query) {
        this.query = Nfc.normalize(query).toLowerCase(Locale.ROOT);
    }

    /**
     * True when {@code value} contains the query.
     */
    public boolean matches(String value) {
        if (value == null) {
            return false;
        }
        return Nfc.normalize(value).toLowerCase(Locale.ROOT).contains(query);
    }

    /**
     * True when the string primitive mapped to {@code key} in {@code table}
     * matches. Non-string values (integers, booleans) never match.
     */
    public boolean matchesField(TomlTable table, String key) {
        String value = Tomls.string(table, key);
        return value != null && matches(value);
    }

    /**
     * True when any string element of the array mapped to {@code key} in
     * {@code table} matches — for {@code alias} or {@code member} (SPEC.md section
     * 4.2).
     */
    public boolean matchesAnyElement(TomlTable table, String key) {
        TomlValue value = table.get(key);
        if (value == null || !value.isArray()) {
            return false;
        }
        for (TomlValue element : value.asArray()) {
            if (element.isPrimitive() && element.asPrimitive().isString()
                                            && matches(element.asPrimitive().asString())) {
                return true;
            }
        }
        return false;
    }

}
