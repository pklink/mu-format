package net.einself.mu.searchcontext;

import io.github.wasabithumb.jtoml.JToml;
import net.einself.mu.metadata.api.EntityFile;
import net.einself.mu.searchcontext.api.EntityType;
import net.einself.mu.searchcontext.api.SearchOptions;
import net.einself.mu.searchcontext.api.SearchResult;
import net.einself.mu.searchcontext.internal.CreditSearcher;
import net.einself.mu.searchcontext.internal.QueryMatcher;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CreditSearcherTest {

    private final JToml toml = JToml.jToml();

    @Test
    void search_findsReleaseCreditingAMatchingArtist() {
        EntityFile artist = artist("overmono", "name = \"Overmono\"\n");
        EntityFile release = release("good-lies", """
                title = "Good Lies"
                [[credit]]
                role = "main"
                artist = "overmono"
                """);
        List<SearchResult> results = searcher("over", null)
                .search(List.of(release), List.of(artist));
        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo("good-lies");
        assertThat(results.get(0).fields())
                .containsEntry("matched-credit", "overmono (main)");
    }

    @Test
    void search_findsReleaseThroughAnAlias() {
        EntityFile artist = artist("overmono", """
                name = "Overmono"
                alias = ["Tom & Ed"]
                """);
        EntityFile release = release("good-lies", """
                [[credit]]
                role = "main"
                artist = "overmono"
                """);
        assertThat(searcher("tom & ed", null).search(List.of(release), List.of(artist)))
                .hasSize(1);
    }

    @Test
    void search_findsTrackLevelCredits() {
        EntityFile artist = artist("anz", "name = " + quoted("Anz") + "\n");
        EntityFile release = release("good-lies", """
                [[credit]]
                role = "main"
                artist = "overmono"
                [[track]]
                number = 1
                title = "Kink"
                [[track.credit]]
                role = "feat"
                artist = "anz"
                """);
        List<SearchResult> results = searcher("anz", null)
                .search(List.of(release), List.of(artist));
        assertThat(results).hasSize(1);
        assertThat(results.get(0).fields())
                .containsEntry("matched-credit", "anz (feat)");
    }

    @Test
    void search_roleRestrictsMatchingCredits() {
        EntityFile artist = artist("anz", "name = \"Anz\"\n");
        EntityFile release = release("good-lies", """
                [[track]]
                number = 1
                title = "Kink"
                [[track.credit]]
                role = "feat"
                artist = "anz"
                """);
        assertThat(searcher("anz", "producer").search(List.of(release), List.of(artist)))
                .isEmpty();
        assertThat(searcher("anz", "feat").search(List.of(release), List.of(artist)))
                .hasSize(1);
    }

    @Test
    void search_noMatchingArtistYieldsNoResults() {
        EntityFile release = release("good-lies", """
                [[credit]]
                role = "main"
                artist = "overmono"
                """);
        assertThat(searcher("nobody", null).search(List.of(release), List.of())).isEmpty();
    }

    private static String quoted(String value) {
        return "\"" + value + "\"";
    }

    private CreditSearcher searcher(String query, String role) {
        SearchOptions options = new SearchOptions(
                EnumSet.allOf(EntityType.class), null, null, null, role, 0);
        return new CreditSearcher(new QueryMatcher(query), options);
    }

    private EntityFile artist(String id, String tomlSource) {
        return new EntityFile(id, Path.of("meta/artists/" + id + ".mu"), toml.readFromString(tomlSource));
    }

    private EntityFile release(String id, String tomlSource) {
        return new EntityFile(id, Path.of("meta/releases/" + id + ".mu"), toml.readFromString(tomlSource));
    }

}
