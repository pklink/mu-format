package net.einself.mu.searchcontext.internal;

import io.github.wasabithumb.jtoml.JToml;
import net.einself.mu.collection.api.CollectionRoot;
import net.einself.mu.metadata.api.EntityFile;
import net.einself.mu.metadata.api.MetadataScanner;
import net.einself.mu.searchcontext.api.EntityType;
import net.einself.mu.searchcontext.api.SearchOptions;
import net.einself.mu.searchcontext.api.SearchResult;
import net.einself.mu.searchcontext.api.SearchService;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SearchServiceImpl implements SearchService {

    private final JToml toml;
    private final PrintStream err;

    public SearchServiceImpl(JToml toml, PrintStream err) {
        this.toml = toml;
        this.err = err;
    }

    @Override
    public List<SearchResult> search(String query, SearchOptions options, CollectionRoot root) {
        MetadataScanner scanner = new MetadataScanner(toml, err);
        List<EntityFile> releases = scanner.scan(root.releases());
        List<EntityFile> artists = scanner.scan(root.artists());

        QueryMatcher matcher = new QueryMatcher(query);
        List<SearchResult> results = collect(releases, artists, matcher, options);

        if (options.limit() > 0 && results.size() > options.limit()) {
            results = new ArrayList<>(results.subList(0, options.limit()));
        }

        return results;
    }

    private List<SearchResult> collect(List<EntityFile> releases,
                                       List<EntityFile> artists,
                                       QueryMatcher matcher,
                                       SearchOptions options) {
        List<SearchResult> results = new ArrayList<>();
        if (options.searches(EntityType.RELEASE)) {
            results.addAll(new ReleaseSearcher(matcher, options).search(releases));
            if (options.field() == null) {
                results.addAll(new CreditSearcher(matcher, options).search(releases, artists));
            }
        }
        if (options.searches(EntityType.ARTIST)) {
            results.addAll(new ArtistSearcher(matcher, options).search(artists));
        }
        if (options.searches(EntityType.TRACK)) {
            results.addAll(new TrackSearcher(matcher, options).search(releases));
        }
        return deduplicate(results);
    }

    private static List<SearchResult> deduplicate(List<SearchResult> results) {
        List<SearchResult> deduplicated = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (SearchResult result : results) {
            String key = switch (result.type()) {
                case RELEASE, ARTIST -> result.type() + ":" + result.id();
                case TRACK -> result.type() + ":" + result.id() + ":"
                        + result.fields().get("disc") + ":" + result.fields().get("number");
            };
            if (seen.add(key)) {
                deduplicated.add(result);
            }
        }
        return deduplicated;
    }
}
