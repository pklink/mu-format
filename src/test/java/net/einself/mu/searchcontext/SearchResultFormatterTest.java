package net.einself.mu.searchcontext;

import net.einself.mu.searchcontext.api.EntityType;
import net.einself.mu.searchcontext.api.SearchResult;
import net.einself.mu.searchcontext.api.SearchResultFormatter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SearchResultFormatterTest {

    private final Path root = Path.of("/collection");

    private final SearchResultFormatter formatter = new SearchResultFormatter(root);

    @Test
    void text_groupsResultsByTypeWithCounts() {
        String output = format(List.of(release(), artist()), SearchResultFormatter.Format.TEXT);
        assertThat(output)
                .contains("Releases (1 found):")
                .contains("  good-lies")
                .contains("    title: Good Lies")
                .contains("    path: meta/releases/good-lies.mu")
                .contains("Artists (1 found):")
                .contains("  overmono");
    }

    @Test
    void text_reportsNoMatches() {
        assertThat(format(List.of(), SearchResultFormatter.Format.TEXT))
                .isEqualTo("No matches.\n");
    }

    @Test
    void json_emitsAllGroupsAlways() {
        String output = format(List.of(release()), SearchResultFormatter.Format.JSON);
        assertThat(output)
                .contains("\"total\": 1")
                .contains("\"releases\": [\n    {")
                .contains("\"id\": \"good-lies\"")
                .contains("\"title\": \"Good Lies\"")
                .contains("\"path\": \"meta/releases/good-lies.mu\"")
                .contains("\"artists\": []")
                .contains("\"tracks\": []");
    }

    @Test
    void json_escapesStrings() {
        SearchResult tricky = new SearchResult(EntityType.RELEASE, "tricky",
                root.resolve("meta/releases/tricky.mu"),
                Map.of("title", "Say \"Hi\"\nLine2\\end"));
        String output = format(List.of(tricky), SearchResultFormatter.Format.JSON);
        assertThat(output).contains("\"title\": \"Say \\\"Hi\\\"\\nLine2\\\\end\"");
        assertThat(output).doesNotContain("Line2\\end\n");
    }

    @Test
    void ids_printsEachIdOnce() {
        SearchResult track1 = new SearchResult(EntityType.TRACK, "good-lies",
                root.resolve("meta/releases/good-lies.mu"),
                Map.of("number", "1", "title", "Feeling Plain"));
        SearchResult track2 = new SearchResult(EntityType.TRACK, "good-lies",
                root.resolve("meta/releases/good-lies.mu"),
                Map.of("number", "2", "title", "Arla Fearn"));
        String output = format(List.of(track1, track2, artist()),
                SearchResultFormatter.Format.IDS);
        assertThat(output).isEqualTo("good-lies\novermono\n");
    }

    private String format(List<SearchResult> results, SearchResultFormatter.Format format) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        formatter.format(results, format, new PrintStream(out));
        return out.toString();
    }

    private SearchResult release() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("title", "Good Lies");
        fields.put("release-year-original", "2023");
        return new SearchResult(EntityType.RELEASE, "good-lies",
                root.resolve("meta/releases/good-lies.mu"), fields);
    }

    private SearchResult artist() {
        return new SearchResult(EntityType.ARTIST, "overmono",
                root.resolve("meta/artists/overmono.mu"), Map.of("name", "Overmono"));
    }

}
