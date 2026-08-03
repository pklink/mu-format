package net.einself.mu.search;

import net.einself.mu.ExitCode;
import net.einself.mu.Main;
import net.einself.mu.MuException;
import net.einself.mu.collection.CollectionRoot;
import net.einself.mu.collection.CollectionRootFinder;
import net.einself.mu.collection.FormatVersionReader;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * {@code mu search} — searches releases, artists and tracks in the meta layer.
 * Read-only: takes no lock, writes nothing.
 */
@Command(name = "search",
        description = "Search releases, artists and tracks.")
public class SearchCommand implements Callable<Integer> {

    @ParentCommand
    private Main parent;

    @Parameters(index = "0", paramLabel = "<query>",
            description = "Case-insensitive substring to search for.")
    String query;

    @Option(names = {"-t", "--type"}, paramLabel = "<type>",
            description = "Restrict to one entity type: release, artist, track, all (default: all).")
    String type = "all";

    @Option(names = {"-f", "--field"}, paramLabel = "<field>",
            description = "Restrict matching to one TOML attribute.")
    String field;

    @Option(names = "--year", paramLabel = "<year>",
            description = "Keep only releases with this release-year-original.")
    String year;

    @Option(names = "--medium", paramLabel = "<medium>",
            description = "Keep only releases with this source-medium.")
    String medium;

    @Option(names = "--role", paramLabel = "<role>",
            description = "Credit matching counts only this role (e.g. main, feat).")
    String role;

    @Option(names = "--format", paramLabel = "<format>",
            description = "Output format: text, json, ids (default: text).")
    String format = "text";

    @Option(names = {"-n", "--limit"}, paramLabel = "<num>",
            description = "Maximum number of results (0 = unlimited).")
    int limit = 0;

    @Override
    public Integer call() {
        SearchOptions options = validateOptions();
        SearchResultFormatter.Format outputFormat = parseFormat();

        CollectionRoot root = resolveRoot();
        MetadataScanner scanner = new MetadataScanner(parent.toml(), parent.err());
        List<EntityFile> releases = scanner.scan(root.releases());
        List<EntityFile> artists = scanner.scan(root.artists());

        QueryMatcher matcher = new QueryMatcher(query);
        List<SearchResult> results = collect(releases, artists, matcher, options);

        if (options.limit() > 0 && results.size() > options.limit()) {
            results = new ArrayList<>(results.subList(0, options.limit()));
        }

        new SearchResultFormatter(root.path()).format(results, outputFormat, parent.out());
        return ExitCode.SUCCESS.value();
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

    /**
     * A release matched both by its own attributes and by a credit appears twice; the
     * attribute hit is kept, which is the stronger signal. Identity is (type, id) for
     * artists and releases; tracks of one release are distinct results, so they key on
     * (type, id, disc, number).
     */
    private static List<SearchResult> deduplicate(List<SearchResult> results) {
        List<SearchResult> deduplicated = new ArrayList<>();
        Set<String> seen = new java.util.HashSet<>();
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

    private SearchOptions validateOptions() {
        Set<EntityType> scope = switch (type.toLowerCase(Locale.ROOT)) {
            case "all" -> EnumSet.allOf(EntityType.class);
            case "release" -> EnumSet.of(EntityType.RELEASE);
            case "artist" -> EnumSet.of(EntityType.ARTIST);
            case "track" -> EnumSet.of(EntityType.TRACK);
            default -> throw new MuException(ExitCode.USAGE,
                    "Invalid --type: must be release, artist, track, or all");
        };
        if (limit < 0) {
            throw new MuException(ExitCode.USAGE, "--limit must be ≥ 0");
        }
        if (role != null && !scope.contains(EntityType.RELEASE)) {
            throw new MuException(ExitCode.USAGE,
                    "--role applies to release credits; --type must include release");
        }
        if ((year != null || medium != null) && !scope.contains(EntityType.RELEASE)
                && !scope.contains(EntityType.TRACK)) {
            throw new MuException(ExitCode.USAGE,
                    "--year/--medium apply to releases and tracks of releases");
        }
        return new SearchOptions(scope, field, year, medium, role, limit);
    }

    private SearchResultFormatter.Format parseFormat() {
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "text" -> SearchResultFormatter.Format.TEXT;
            case "json" -> SearchResultFormatter.Format.JSON;
            case "ids" -> SearchResultFormatter.Format.IDS;
            default -> throw new MuException(ExitCode.USAGE,
                    "Invalid --format: must be text, json, or ids");
        };
    }

    private CollectionRoot resolveRoot() {
        CollectionRoot root = new CollectionRootFinder()
                .find(parent.root, Path.of("").toAbsolutePath());
        new FormatVersionReader(parent.toml()).read(root);
        return root;
    }

}
