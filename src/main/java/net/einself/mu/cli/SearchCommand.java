package net.einself.mu.cli;

import net.einself.mu.collection.api.CollectionModule;
import net.einself.mu.collection.api.CollectionRoot;
import net.einself.mu.collection.api.CollectionService;
import net.einself.mu.searchcontext.api.*;
import net.einself.mu.shared.ExitCode;
import net.einself.mu.shared.MuException;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * {@code mu search} — searches releases, artists and tracks in the meta layer.
 * Read-only: takes no lock, writes nothing.
 */
@Command(name = "search", description = "Search releases, artists and tracks.")
public class SearchCommand implements Callable<Integer> {

    private static final Map<EntityType, String> GROUP_LABELS = Map.of(
                                    EntityType.RELEASE, "Releases",
                                    EntityType.ARTIST, "Artists",
                                    EntityType.TRACK, "Tracks");

    // Populated by Picocli via reflection before call(); never null when used.
    @SuppressWarnings("NullAway.Init")
    @ParentCommand
    private Main parent;

    // Populated by Picocli via reflection before call(); never null when used.
    @SuppressWarnings("NullAway.Init")
    @Parameters(index = "0", paramLabel = "<query>", description = "Case-insensitive substring to search for.")
    String query;

    @Option(names = {"-t", "--type"}, paramLabel = "<type>", description = "Restrict to one entity type: release, artist, track, all (default: all).")
    String type = "all";

    @Option(names = {"-f", "--field"}, paramLabel = "<field>", description = "Restrict matching to one TOML attribute.")
    @Nullable
    String field;

    @Option(names = "--year", paramLabel = "<year>", description = "Keep only releases with this release-year-original.")
    @Nullable
    String year;

    @Option(names = "--medium", paramLabel = "<medium>", description = "Keep only releases with this source-medium.")
    @Nullable
    String medium;

    @Option(names = "--role", paramLabel = "<role>", description = "Credit matching counts only this role (e.g. main, feat).")
    @Nullable
    String role;

    @Option(names = {"-n", "--limit"}, paramLabel = "<num>", description = "Maximum number of results (0 = unlimited).")
    int limit = 0;

    private final OutputFormatter outputFormatter;

    public SearchCommand(OutputFormatter outputFormatter) {
        this.outputFormatter = outputFormatter;
    }

    @Override
    public Integer call() {
        OutputFormatter.validate(parent.format);
        SearchOptions options = validateOptions();

        CollectionService collectionService = CollectionModule.createService(parent.toml());
        CollectionRoot root = collectionService.findRoot(parent.root, Path.of("").toAbsolutePath());
        collectionService.readFormatVersion(root);

        SearchService searchService = SearchModule.createSearchService(parent.toml(), parent.err());
        List<SearchResult> results = searchService.search(query, options, root);

        Path rootPath = root.path();
        Map<EntityType, List<SearchResult>> grouped = group(results);
        SearchData data = new SearchData(
                                        results.size(),
                                        toItems(grouped, EntityType.RELEASE, rootPath),
                                        toItems(grouped, EntityType.ARTIST, rootPath),
                                        toItems(grouped, EntityType.TRACK, rootPath));

        outputFormatter.write(parent.format, "search", data,
                                        out -> formatText(grouped, rootPath, out));
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

    private void formatText(Map<EntityType, List<SearchResult>> grouped,
                                    Path root, PrintStream out) {
        if (grouped.values().stream().allMatch(List::isEmpty)) {
            out.println("No matches.");
            return;
        }
        for (EntityType type : EntityType.values()) {
            List<SearchResult> group = grouped.get(type);
            if (group == null || group.isEmpty()) {
                continue;
            }
            out.printf("%s (%d found):%n", GROUP_LABELS.get(type), group.size());
            for (SearchResult result : group) {
                out.println("  " + result.id());
                result.fields().forEach((key, value) -> out.println("    " + key + ": " + value));
                out.println("    path: " + relative(result.path(), root));
            }
            out.println();
        }
    }

    private Map<EntityType, List<SearchResult>> group(List<SearchResult> results) {
        Map<EntityType, List<SearchResult>> grouped = new EnumMap<>(EntityType.class);
        for (SearchResult result : results) {
            grouped.computeIfAbsent(result.type(), ignored -> new ArrayList<>())
                                            .add(result);
        }
        return grouped;
    }

    private List<SearchResultItem> toItems(Map<EntityType, List<SearchResult>> grouped,
                                    EntityType type, Path root) {
        return grouped.getOrDefault(type, List.of()).stream()
                                        .map(result -> new SearchResultItem(result.id(), result.fields(),
                                                                        relative(result.path(), root)))
                                        .toList();
    }

    private String relative(Path path, Path root) {
        return root.relativize(path).toString();
    }
}
