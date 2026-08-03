package net.einself.mu.searchcontext;

import io.github.wasabithumb.jtoml.JToml;
import net.einself.mu.metadata.api.EntityFile;
import net.einself.mu.searchcontext.api.EntityType;
import net.einself.mu.searchcontext.api.SearchOptions;
import net.einself.mu.searchcontext.api.SearchResult;
import net.einself.mu.searchcontext.internal.ArtistSearcher;
import net.einself.mu.searchcontext.internal.QueryMatcher;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArtistSearcherTest {

    private final JToml toml = JToml.jToml();

    @Test
    void search_findsArtistByName() {
        EntityFile artist = artist("overmono", "name = \"Overmono\"\n");
        List<SearchResult> results = searcher("over").search(List.of(artist));
        assertThat(results).hasSize(1);
        assertThat(results.get(0).type()).isEqualTo(EntityType.ARTIST);
        assertThat(results.get(0).fields()).containsEntry("name", "Overmono");
    }

    @Test
    void search_findsArtistByAlias() {
        EntityFile artist = artist("overmono", """
                name = "Overmono"
                alias = ["Tom & Ed"]
                """);
        assertThat(searcher("tom & ed").search(List.of(artist))).hasSize(1);
    }

    @Test
    void search_findsArtistBySortName() {
        EntityFile artist = artist("overmono", """
                name = "Overmono"
                sort-name = "Russell Brothers"
                """);
        assertThat(searcher("russell").search(List.of(artist))).hasSize(1);
    }

    @Test
    void search_exposesGroupMembership() {
        EntityFile artist = artist("overmono", """
                name = "Overmono"
                is-group = true
                member = ["tom-russell", "ed-russell"]
                """);
        List<SearchResult> results = searcher("over").search(List.of(artist));
        assertThat(results.get(0).fields())
                .containsEntry("is-group", "true")
                .containsEntry("member", "tom-russell, ed-russell");
    }

    @Test
    void search_fieldRestrictsMatchingToScalarsAndArrays() {
        EntityFile artist = artist("overmono", """
                name = "Overmono"
                alias = ["Doversole"]
                """);
        SearchOptions nameOnly = options("name");
        assertThat(new ArtistSearcher(new QueryMatcher("dover"), nameOnly)
                .search(List.of(artist))).isEmpty();
        SearchOptions aliasOnly = options("alias");
        assertThat(new ArtistSearcher(new QueryMatcher("dover"), aliasOnly)
                .search(List.of(artist))).hasSize(1);
    }

    private ArtistSearcher searcher(String query) {
        return new ArtistSearcher(new QueryMatcher(query), options(null));
    }

    private EntityFile artist(String id, String tomlSource) {
        return new EntityFile(id, Path.of("meta/artists/" + id + ".mu"), toml.readFromString(tomlSource));
    }

    private static SearchOptions options(String field) {
        return new SearchOptions(EnumSet.allOf(EntityType.class), field, null, null, null, 0);
    }

}
