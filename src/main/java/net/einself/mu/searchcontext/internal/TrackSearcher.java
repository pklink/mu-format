package net.einself.mu.searchcontext.internal;

import io.github.wasabithumb.jtoml.value.TomlValue;
import io.github.wasabithumb.jtoml.value.table.TomlTable;
import net.einself.mu.searchcontext.api.SearchResult;
import net.einself.mu.searchcontext.api.SearchOptions;
import net.einself.mu.searchcontext.api.EntityType;
import net.einself.mu.metadata.api.EntityFile;
import net.einself.mu.metadata.api.Tomls;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Matches the {@code [[track]]} tables inside release files by {@code title} and
 * {@code isrc} (SPEC.md section 4.7). A track result points at the release file that
 * contains it; the {@code --year} and {@code --medium} release filters apply to the
 * containing release.
 */
public class TrackSearcher {

    private static final List<String> DEFAULT_FIELDS = List.of("title", "isrc");

    private final QueryMatcher matcher;

    private final SearchOptions options;

    public TrackSearcher(QueryMatcher matcher, SearchOptions options) {
        this.matcher = matcher;
        this.options = options;
    }

    public List<SearchResult> search(List<EntityFile> releases) {
        List<SearchResult> results = new ArrayList<>();
        for (EntityFile release : releases) {
            if (ReleaseSearcher.passesFilters(release.data(), options)) {
                searchTracks(release, results);
            }
        }
        return results;
    }

    private void searchTracks(EntityFile release, List<SearchResult> results) {
        TomlValue tracks = release.data().get("track");
        if (tracks == null || !tracks.isArray()) {
            return;
        }
        for (TomlValue entry : tracks.asArray()) {
            if (entry.isTable() && matchesQuery(entry.asTable())) {
                results.add(toResult(release, entry.asTable()));
            }
        }
    }

    private boolean matchesQuery(TomlTable track) {
        if (options.field() != null) {
            return matcher.matchesField(track, options.field());
        }
        for (String key : DEFAULT_FIELDS) {
            if (matcher.matchesField(track, key)) {
                return true;
            }
        }
        return false;
    }

    private static SearchResult toResult(EntityFile release, TomlTable track) {
        Map<String, String> fields = new LinkedHashMap<>();
        Tomls.put(fields, "release", release.id());
        Tomls.put(fields, "disc", Tomls.scalar(track, "disc"));
        Tomls.put(fields, "number", Tomls.scalar(track, "number"));
        Tomls.put(fields, "title", Tomls.string(track, "title"));
        Tomls.put(fields, "isrc", Tomls.string(track, "isrc"));
        return new SearchResult(EntityType.TRACK, release.id(), release.path(), fields);
    }

}
