package net.einself.mu.searchcontext;

import io.github.wasabithumb.jtoml.JToml;
import net.einself.mu.metadata.api.EntityFile;
import net.einself.mu.searchcontext.api.EntityType;
import net.einself.mu.searchcontext.api.SearchOptions;
import net.einself.mu.searchcontext.api.SearchResult;
import net.einself.mu.searchcontext.internal.QueryMatcher;
import net.einself.mu.searchcontext.internal.TrackSearcher;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrackSearcherTest {

    private final JToml toml = JToml.jToml();

    @Test
    void search_findsTrackByTitle() {
        EntityFile release = release("good-lies", """
                title = "Good Lies"
                [[track]]
                number = 1
                title = "Feeling Plain"
                [[track]]
                number = 2
                title = "Arla Fearn"
                """);
        List<SearchResult> results = searcher("feeling").search(List.of(release));
        assertThat(results).hasSize(1);
        SearchResult track = results.get(0);
        assertThat(track.type()).isEqualTo(EntityType.TRACK);
        assertThat(track.id()).isEqualTo("good-lies");
        assertThat(track.fields())
                .containsEntry("release", "good-lies")
                .containsEntry("number", "1")
                .containsEntry("title", "Feeling Plain");
    }

    @Test
    void search_findsTrackByIsrc() {
        EntityFile release = release("a", """
                [[track]]
                number = 1
                title = "X"
                isrc = "GBABC1234567"
                """);
        assertThat(searcher("gbabc").search(List.of(release))).hasSize(1);
    }

    @Test
    void search_rendersStringDiscs() {
        EntityFile release = release("a", """
                [[track]]
                disc = "A"
                number = 1
                title = "X"
                """);
        List<SearchResult> results = searcher("x").search(List.of(release));
        assertThat(results.get(0).fields()).containsEntry("disc", "A");
    }

    @Test
    void search_appliesReleaseFilters() {
        EntityFile cd = release("cd-release", """
                title = "R"
                source-medium = "cd"
                [[track]]
                number = 1
                title = "X"
                """);
        EntityFile vinyl = release("vinyl-release", """
                title = "R"
                source-medium = "vinyl"
                [[track]]
                number = 1
                title = "X"
                """);
        SearchOptions options = new SearchOptions(
                EnumSet.allOf(EntityType.class), null, null, "vinyl", null, 0);
        List<SearchResult> results = new TrackSearcher(new QueryMatcher("x"), options)
                .search(List.of(cd, vinyl));
        assertThat(results).extracting(SearchResult::id).containsExactly("vinyl-release");
    }

    @Test
    void search_fieldRestrictsMatching() {
        EntityFile release = release("a", """
                [[track]]
                number = 1
                title = "Kink"
                isrc = "KINK12345678"
                """);
        SearchOptions titleOnly = new SearchOptions(
                EnumSet.allOf(EntityType.class), "title", null, null, null, 0);
        assertThat(new TrackSearcher(new QueryMatcher("kink1"), titleOnly)
                .search(List.of(release))).isEmpty();
        assertThat(new TrackSearcher(new QueryMatcher("kink"), titleOnly)
                .search(List.of(release))).hasSize(1);
    }

    @Test
    void search_skipsReleasesWithoutTracks() {
        EntityFile release = release("empty", "title = \"X\"\n");
        assertThat(searcher("x").search(List.of(release))).isEmpty();
    }

    private TrackSearcher searcher(String query) {
        SearchOptions options = new SearchOptions(
                EnumSet.allOf(EntityType.class), null, null, null, null, 0);
        return new TrackSearcher(new QueryMatcher(query), options);
    }

    private EntityFile release(String id, String tomlSource) {
        return new EntityFile(id, Path.of("meta/releases/" + id + ".mu"), toml.readFromString(tomlSource));
    }

}
