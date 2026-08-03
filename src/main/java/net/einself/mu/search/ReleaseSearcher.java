package net.einself.mu.search;

import io.github.wasabithumb.jtoml.value.table.TomlTable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Matches releases by their scalar attributes (SPEC.md section 4.8) and applies the
 * {@code --year} and {@code --medium} filters. Credits are matched separately
 * ({@link CreditSearcher}).
 */
public class ReleaseSearcher {

    /**
     * Searched when no {@code --field} is given. The descriptive, hand-edited attributes
     * a user is likely to remember — not blob references or technical rip data.
     */
    private static final List<String> DEFAULT_FIELDS = List.of(
            "title", "type", "release-year-original", "release-year-medium",
            "source-medium", "source-store", "rip-result", "notes", "origin-dir");

    private final QueryMatcher matcher;

    private final SearchOptions options;

    public ReleaseSearcher(QueryMatcher matcher, SearchOptions options) {
        this.matcher = matcher;
        this.options = options;
    }

    public List<SearchResult> search(List<EntityFile> releases) {
        List<SearchResult> results = new ArrayList<>();
        for (EntityFile release : releases) {
            if (passesFilters(release.data()) && matchesQuery(release.data())) {
                results.add(toResult(release));
            }
        }
        return results;
    }

    /**
     * The filters are shared with {@link TrackSearcher}: a year or medium constraint
     * keeps only tracks of matching releases.
     */
    static boolean passesFilters(TomlTable data, SearchOptions options) {
        return equalsIgnoreCase(data, "release-year-original", options.year())
                && equalsIgnoreCase(data, "source-medium", options.medium());
    }

    private boolean passesFilters(TomlTable data) {
        return passesFilters(data, options);
    }

    private static boolean equalsIgnoreCase(TomlTable data, String key, String expected) {
        if (expected == null) {
            return true;
        }
        String actual = Tomls.string(data, key);
        return actual != null && actual.equalsIgnoreCase(expected);
    }

    private boolean matchesQuery(TomlTable data) {
        if (options.field() != null) {
            return matcher.matchesField(data, options.field());
        }
        for (String key : DEFAULT_FIELDS) {
            if (matcher.matchesField(data, key)) {
                return true;
            }
        }
        return false;
    }

    static SearchResult toResult(EntityFile release) {
        return toResult(release, Map.of());
    }

    /**
     * @param extra additional fields, e.g. the credits that made a release match
     */
    static SearchResult toResult(EntityFile release, Map<String, String> extra) {
        TomlTable data = release.data();
        Map<String, String> fields = new LinkedHashMap<>();
        Tomls.put(fields, "title", Tomls.string(data, "title"));
        Tomls.put(fields, "type", Tomls.string(data, "type"));
        Tomls.put(fields, "release-year-original", Tomls.string(data, "release-year-original"));
        Tomls.put(fields, "release-year-medium", Tomls.string(data, "release-year-medium"));
        Tomls.put(fields, "source-medium", Tomls.string(data, "source-medium"));
        fields.putAll(extra);
        return new SearchResult(EntityType.RELEASE, release.id(), release.path(), fields);
    }

}
