package net.einself.mu.cli;

import net.einself.mu.shared.ExitCode;
import net.einself.mu.cli.Main;
import net.einself.mu.shared.MuException;
import net.einself.mu.collection.api.CollectionModule;
import net.einself.mu.collection.api.CollectionRoot;
import net.einself.mu.collection.api.CollectionService;
import net.einself.mu.searchcontext.api.EntityType;
import net.einself.mu.searchcontext.api.SearchModule;
import net.einself.mu.searchcontext.api.SearchOptions;
import net.einself.mu.searchcontext.api.SearchResult;
import net.einself.mu.searchcontext.api.SearchResultFormatter;
import net.einself.mu.searchcontext.api.SearchService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
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

        CollectionService collectionService = CollectionModule.createService(parent.toml());
        CollectionRoot root = collectionService.findRoot(parent.root, Path.of("").toAbsolutePath());
        collectionService.readFormatVersion(root);

        SearchService searchService = SearchModule.createSearchService(parent.toml(), parent.err());
        List<SearchResult> results = searchService.search(query, options, root);

        new SearchResultFormatter(root.path()).format(results, outputFormat, parent.out());
        return ExitCode.SUCCESS.value();
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
}
