package net.einself.mu.search;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One match.
 *
 * @param type   what was matched
 * @param id     the entity identifier; for a track, the identifier of the containing release
 * @param path   the entity file
 * @param fields display fields in output order
 */
public record SearchResult(EntityType type, String id, Path path, Map<String, String> fields) {

    public SearchResult {
        fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

}
