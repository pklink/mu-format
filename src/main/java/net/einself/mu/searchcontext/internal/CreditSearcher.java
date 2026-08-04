package net.einself.mu.searchcontext.internal;

import io.github.wasabithumb.jtoml.value.TomlValue;
import io.github.wasabithumb.jtoml.value.table.TomlTable;
import net.einself.mu.metadata.api.EntityFile;
import net.einself.mu.metadata.api.Tomls;
import net.einself.mu.searchcontext.api.SearchOptions;
import net.einself.mu.searchcontext.api.SearchResult;

import java.util.*;

/**
 * Matches releases through their credits (SPEC.md section 4.6): when the query
 * matches an artist's {@code name} or {@code alias}, every release crediting
 * that artist is a result — a query for a band finds their records, not just
 * the artist file. {@code --role} restricts which credit roles count, both at
 * release and at track level.
 */
public class CreditSearcher {

    private final QueryMatcher matcher;

    private final SearchOptions options;

    public CreditSearcher(QueryMatcher matcher, SearchOptions options) {
        this.matcher = matcher;
        this.options = options;
    }

    public List<SearchResult> search(List<EntityFile> releases, List<EntityFile> artists) {
        Set<String> artistIds = matchingArtistIds(artists);
        if (artistIds.isEmpty()) {
            return List.of();
        }
        List<SearchResult> results = new ArrayList<>();
        for (EntityFile release : releases) {
            SequencedSet<String> matches = new LinkedHashSet<>();
            collectMatches(release.data().get("credit"), artistIds, matches);
            collectTrackMatches(release.data().get("track"), artistIds, matches);
            if (!matches.isEmpty()) {
                results.add(ReleaseSearcher.toResult(release,
                                                Map.of("matched-credit", String.join(", ", matches))));
            }
        }
        return results;
    }

    /**
     * The identifiers of every artist whose name or alias matches the query.
     * Matching {@code member} references is deliberate: a query for a band member
     * finds the band's releases as well.
     */
    private Set<String> matchingArtistIds(List<EntityFile> artists) {
        Set<String> ids = new LinkedHashSet<>();
        for (EntityFile artist : artists) {
            TomlTable data = artist.data();
            if (matcher.matchesField(data, "name")
                                            || matcher.matchesAnyElement(data, "alias")
                                            || matcher.matchesAnyElement(data, "member")) {
                ids.add(artist.id());
            }
        }
        return ids;
    }

    private void collectTrackMatches(TomlValue tracks, Set<String> artistIds,
                                    SequencedSet<String> matches) {
        if (tracks == null || !tracks.isArray()) {
            return;
        }
        for (TomlValue entry : tracks.asArray()) {
            if (entry.isTable()) {
                collectMatches(entry.asTable().get("credit"), artistIds, matches);
            }
        }
    }

    private void collectMatches(TomlValue credits, Set<String> artistIds,
                                    SequencedSet<String> matches) {
        if (credits == null || !credits.isArray()) {
            return;
        }
        for (TomlValue entry : credits.asArray()) {
            if (!entry.isTable()) {
                continue;
            }
            TomlTable credit = entry.asTable();
            String artist = Tomls.string(credit, "artist");
            String role = Tomls.string(credit, "role");
            if (artist != null && artistIds.contains(artist) && roleAccepted(role)) {
                matches.add(role == null ? artist : artist + " (" + role + ")");
            }
        }
    }

    private boolean roleAccepted(String role) {
        return options.role() == null || options.role().equals(role);
    }

}
