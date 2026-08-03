package net.einself.mu.search;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Renders search results in three formats: {@code text} for humans, {@code json} for
 * further processing and {@code ids} for piping into other tools. Paths are printed
 * relative to the collection root, matching the layout of SPEC.md section 2.
 */
public class SearchResultFormatter {

    public enum Format {

        TEXT,
        JSON,
        IDS

    }

    private static final Map<EntityType, String> GROUP_LABELS = Map.of(
            EntityType.RELEASE, "Releases",
            EntityType.ARTIST, "Artists",
            EntityType.TRACK, "Tracks");

    private static final Map<EntityType, String> JSON_KEYS = Map.of(
            EntityType.RELEASE, "releases",
            EntityType.ARTIST, "artists",
            EntityType.TRACK, "tracks");

    private final Path root;

    public SearchResultFormatter(Path root) {
        this.root = root;
    }

    public void format(List<SearchResult> results, Format format, PrintStream out) {
        switch (format) {
            case TEXT -> formatText(results, out);
            case JSON -> formatJson(results, out);
            case IDS -> formatIds(results, out);
        }
    }

    private void formatText(List<SearchResult> results, PrintStream out) {
        if (results.isEmpty()) {
            out.println("No matches.");
            return;
        }
        Map<EntityType, List<SearchResult>> grouped = group(results);
        for (EntityType type : EntityType.values()) {
            List<SearchResult> group = grouped.get(type);
            if (group == null || group.isEmpty()) {
                continue;
            }
            out.printf("%s (%d found):%n", GROUP_LABELS.get(type), group.size());
            for (SearchResult result : group) {
                out.println("  " + result.id());
                result.fields().forEach((key, value) ->
                        out.println("    " + key + ": " + value));
                out.println("    path: " + relative(result.path()));
            }
            out.println();
        }
    }

    private void formatJson(List<SearchResult> results, PrintStream out) {
        Map<EntityType, List<SearchResult>> grouped = group(results);
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"total\": ").append(results.size()).append(",\n");
        for (EntityType type : EntityType.values()) {
            json.append("  \"").append(JSON_KEYS.get(type)).append("\": [");
            List<SearchResult> group = grouped.getOrDefault(type, List.of());
            if (group.isEmpty()) {
                json.append("]");
            } else {
                for (SearchResult result : group) {
                    json.append("\n    {");
                    appendJsonField(json, "id", result.id(), true);
                    result.fields().forEach((key, value) -> appendJsonField(json, key, value, false));
                    appendJsonField(json, "path", relative(result.path()), false);
                    json.append("\n    },");
                }
                json.setLength(json.length() - 1);
                json.append("\n  ]");
            }
            json.append(",\n");
        }
        json.setLength(json.length() - 2);
        json.append("\n}\n");
        out.print(json);
    }

    private static void appendJsonField(StringBuilder json, String key, String value, boolean first) {
        if (!first) {
            json.append(",");
        }
        json.append("\n      \"").append(escape(key)).append("\": \"").append(escape(value)).append("\"");
    }

    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.toString();
    }

    /**
     * One identifier per line, deduplicated: several matching tracks of one release
     * print its id once, so the output can be piped into other commands directly.
     */
    private void formatIds(List<SearchResult> results, PrintStream out) {
        Set<String> ids = new LinkedHashSet<>();
        results.forEach(result -> ids.add(result.id()));
        ids.forEach(out::println);
    }

    private Map<EntityType, List<SearchResult>> group(List<SearchResult> results) {
        Map<EntityType, List<SearchResult>> grouped = new EnumMap<>(EntityType.class);
        for (SearchResult result : results) {
            grouped.computeIfAbsent(result.type(), ignored -> new ArrayList<>())
                    .add(result);
        }
        return grouped;
    }

    private String relative(Path path) {
        return root.relativize(path).toString();
    }

}
