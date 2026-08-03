package net.einself.mu.searchcontext;

import io.github.wasabithumb.jtoml.JToml;
import io.github.wasabithumb.jtoml.value.table.TomlTable;
import net.einself.mu.searchcontext.internal.QueryMatcher;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QueryMatcherTest {

    private final JToml toml = JToml.jToml();

    @Test
    void matches_substringCaseInsensitively() {
        QueryMatcher matcher = new QueryMatcher("over");
        assertThat(matcher.matches("Overmono")).isTrue();
        assertThat(matcher.matches("OVER")).isTrue();
        assertThat(matcher.matches("a over b")).isTrue();
        assertThat(matcher.matches("Underworld")).isFalse();
    }

    @Test
    void matches_handlesNfcOnBothSides() {
        // query in decomposed form, value in composed form (and vice versa)
        QueryMatcher decomposed = new QueryMatcher("Café");
        assertThat(decomposed.matches("Café del Mar")).isTrue();

        QueryMatcher composed = new QueryMatcher("Café");
        assertThat(composed.matches("Café del Mar")).isTrue();
    }

    @Test
    void matches_nullIsNotAMatch() {
        assertThat(new QueryMatcher("x").matches(null)).isFalse();
    }

    @Test
    void matchesField_readsStringPrimitivesOnly() {
        TomlTable table = toml.readFromString("""
                title = "Good Lies"
                year = 2023
                """.stripIndent());
        QueryMatcher matcher = new QueryMatcher("good");
        assertThat(matcher.matchesField(table, "title")).isTrue();
        assertThat(matcher.matchesField(table, "year")).isFalse();
        assertThat(matcher.matchesField(table, "missing")).isFalse();
    }

    @Test
    void matchesAnyElement_searchesStringArrays() {
        TomlTable table = toml.readFromString("""
                alias = ["Tom Russell", "Ed Russell"]
                count = [1, 2]
                """.stripIndent());
        QueryMatcher matcher = new QueryMatcher("russell");
        assertThat(matcher.matchesAnyElement(table, "alias")).isTrue();
        assertThat(matcher.matchesAnyElement(table, "count")).isFalse();
        assertThat(matcher.matchesAnyElement(table, "missing")).isFalse();
    }

}
