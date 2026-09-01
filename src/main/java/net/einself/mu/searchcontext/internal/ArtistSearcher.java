package net.einself.mu.searchcontext.internal;

import io.github.wasabithumb.jtoml.value.TomlValue;
import io.github.wasabithumb.jtoml.value.table.TomlTable;
import net.einself.mu.metadata.api.EntityFile;
import net.einself.mu.metadata.api.Tomls;
import net.einself.mu.searchcontext.api.EntityType;
import net.einself.mu.searchcontext.api.SearchOptions;
import net.einself.mu.searchcontext.api.SearchResult;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * Matches artists by {@code name}, {@code sort-name}, the {@code alias} array
 * and {@code notes} (SPEC.md section 4.7).
 */
public class ArtistSearcher {

    private static final List<String> DEFAULT_SCALAR_FIELDS = List.of("name", "sort-name", "notes", "discogs-artist-id");

    private static final List<String> DEFAULT_ARRAY_FIELDS = List.of("alias");

    private final QueryMatcher matcher;

    private final SearchOptions options;

    public ArtistSearcher(QueryMatcher matcher, SearchOptions options) {
        this.matcher = matcher;
        this.options = options;
    }

    public List<SearchResult> search(List<EntityFile> artists) {
        List<SearchResult> results = new ArrayList<>();
        for (EntityFile artist : artists) {
            if (matchesQuery(artist.data())) {
                results.add(toResult(artist));
            }
        }
        return results;
    }

    private boolean matchesQuery(TomlTable data) {
        if (options.field() != null) {
            return matcher.matchesField(data, options.field())
                                            || matcher.matchesAnyElement(data, options.field());
        }
        for (String key : DEFAULT_SCALAR_FIELDS) {
            if (matcher.matchesField(data, key)) {
                return true;
            }
        }
        for (String key : DEFAULT_ARRAY_FIELDS) {
            if (matcher.matchesAnyElement(data, key)) {
                return true;
            }
        }
        return false;
    }

    private static SearchResult toResult(EntityFile artist) {
        TomlTable data = artist.data();
        Map<String, String> fields = new LinkedHashMap<>();
        Tomls.put(fields, "name", Tomls.string(data, "name"));
        Tomls.put(fields, "sort-name", Tomls.string(data, "sort-name"));
        Tomls.put(fields, "is-group", Tomls.scalar(data, "is-group"));
        Tomls.put(fields, "alias", join(data, "alias"));
        Tomls.put(fields, "member", join(data, "member"));
        return new SearchResult(EntityType.ARTIST, artist.id(), artist.path(), fields);
    }

    private static @Nullable String join(TomlTable data, String key) {
        TomlValue value = data.get(key);
        if (value == null || !value.isArray()) {
            return null;
        }
        StringJoiner joiner = new StringJoiner(", ");
        for (TomlValue element : value.asArray()) {
            if (element.isPrimitive() && element.asPrimitive().isString()) {
                joiner.add(element.asPrimitive().asString());
            }
        }
        String joined = joiner.toString();
        return joined.isEmpty() ? null : joined;
    }

}
