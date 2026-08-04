package net.einself.mu.searchcontext;

import io.github.wasabithumb.jtoml.JToml;
import net.einself.mu.metadata.api.EntityFile;
import net.einself.mu.searchcontext.api.EntityType;
import net.einself.mu.searchcontext.api.SearchOptions;
import net.einself.mu.searchcontext.api.SearchResult;
import net.einself.mu.searchcontext.internal.QueryMatcher;
import net.einself.mu.searchcontext.internal.ReleaseSearcher;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReleaseSearcherTest {

    private final JToml toml = JToml.jToml();

    @Test
    void search_findsReleaseByTitleSubstring() {
        EntityFile release = release("good-lies", """
                                        title = "Good Lies"
                                        release-year-original = "2023"
                                        """);
        List<SearchResult> results = searcher("lies", options()).search(List.of(release));
        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo("good-lies");
        assertThat(results.get(0).type()).isEqualTo(EntityType.RELEASE);
        assertThat(results.get(0).fields()).containsEntry("title", "Good Lies");
    }

    @Test
    void search_skipsNonMatchingReleases() {
        EntityFile release = release("good-lies", "title = \"Good Lies\"\n");
        assertThat(searcher("underworld", options()).search(List.of(release))).isEmpty();
    }

    @Test
    void search_filtersbyYear() {
        EntityFile y2023 = release("a", "title = \"X\"\nrelease-year-original = \"2023\"\n");
        EntityFile y2024 = release("b", "title = \"X\"\nrelease-year-original = \"2024\"\n");
        SearchOptions options = options("2023", null);
        List<SearchResult> results = searcher("x", options).search(List.of(y2023, y2024));
        assertThat(results).extracting(SearchResult::id).containsExactly("a");
    }

    @Test
    void search_filtersbyMediumCaseInsensitively() {
        EntityFile cd = release("a", "title = \"X\"\nsource-medium = \"cd\"\n");
        EntityFile vinyl = release("b", "title = \"X\"\nsource-medium = \"vinyl\"\n");
        SearchOptions options = options(null, "CD");
        List<SearchResult> results = searcher("x", options).search(List.of(cd, vinyl));
        assertThat(results).extracting(SearchResult::id).containsExactly("a");
    }

    @Test
    void search_fieldRestrictsMatching() {
        EntityFile release = release("good-lies", """
                                        title = "Good Lies"
                                        notes = "Ripped from the web"
                                        """);
        SearchOptions titleOnly = options(null, null, "title");
        assertThat(searcher("web", titleOnly).search(List.of(release))).isEmpty();
        SearchOptions notesOnly = options(null, null, "notes");
        assertThat(searcher("web", notesOnly).search(List.of(release))).hasSize(1);
    }

    @Test
    void search_searchesNotesByDefault() {
        EntityFile release = release("a", "title = \"X\"\nnotes = \"AccurateRip ok\"\n");
        assertThat(searcher("accuraterip", options()).search(List.of(release))).hasSize(1);
    }

    @Test
    void search_toleratesMissingAttributes() {
        EntityFile release = release("bare", "title = \"X\"\n");
        List<SearchResult> results = searcher("x", options()).search(List.of(release));
        assertThat(results).hasSize(1);
        assertThat(results.get(0).fields()).doesNotContainKeys("source-medium", "type");
    }

    private ReleaseSearcher searcher(String query, SearchOptions options) {
        return new ReleaseSearcher(new QueryMatcher(query), options);
    }

    private EntityFile release(String id, String tomlSource) {
        return new EntityFile(id, Path.of("meta/releases/" + id + ".mu"), toml.readFromString(tomlSource));
    }

    private static SearchOptions options() {
        return options(null, null);
    }

    private static SearchOptions options(String year, String medium) {
        return options(year, medium, null);
    }

    private static SearchOptions options(String year, String medium, String field) {
        return new SearchOptions(EnumSet.allOf(EntityType.class), field, year, medium, null, 0);
    }

}
