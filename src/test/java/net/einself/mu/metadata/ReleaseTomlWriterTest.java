package net.einself.mu.metadata;

import io.github.wasabithumb.jtoml.JToml;
import io.github.wasabithumb.jtoml.option.JTomlOption;
import io.github.wasabithumb.jtoml.option.JTomlOptions;
import io.github.wasabithumb.jtoml.option.prop.LineSeparator;
import io.github.wasabithumb.jtoml.option.prop.OrderMarkPolicy;
import io.github.wasabithumb.jtoml.value.table.TomlTable;
import net.einself.mu.metadata.api.Release;
import net.einself.mu.metadata.internal.ReleaseTomlWriter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReleaseTomlWriterTest {

    private static final JToml TOML = JToml.jToml(JTomlOptions.builder()
                                    .set(JTomlOption.LINE_SEPARATOR, LineSeparator.LF)
                                    .set(JTomlOption.WRITE_BOM, OrderMarkPolicy.NEVER)
                                    .build());

    private final ReleaseTomlWriter underTest = new ReleaseTomlWriter(TOML);

    @Test
    void render_writesCreditsAndTracksAsBlockTables() {
        String result = underTest.render(release());

        // SPEC.md section 4.5 rule 8: the canonical write form is the block table
        assertThat(result).contains("[[credit]]");
        assertThat(result).contains("[[track]]");
        assertThat(result).doesNotContain("credit = [");
        assertThat(result).doesNotContain("track = [");
    }

    @Test
    void render_putsScalarsBeforeTheFirstTableHeader() {
        String result = underTest.render(release());

        assertThat(result.indexOf("title = ")).isLessThan(result.indexOf("[["));
    }

    @Test
    void render_usesLfLineEndings() {
        String result = underTest.render(release());

        assertThat(result).doesNotContain("\r");
    }

    @Test
    void render_escapesStringValues() {
        Release release = new Release("id", "Quote \" and \\ and \n", List.of(), List.of(), List.of(), null);

        String result = underTest.render(release);

        assertThat(result).contains("title = \"Quote \\\" and \\\\ and \\n\"");
    }

    @Test
    void render_normalizesStringValuesToNfc() {
        Release release = new Release("id", "Caf\u0065\u0301", List.of(), List.of(), List.of(), null);

        String result = underTest.render(release);

        assertThat(result).contains("Caf\u00e9");
    }

    @Test
    void render_omitsAbsentOptionalAttributes() {
        Release release = new Release("id", "Title",
                                        List.of(new Release.Credit("main", null)), List.of(), List.of(), null);

        String result = underTest.render(release);

        assertThat(result).doesNotContain("artist");
        assertThat(result).doesNotContain("origin-dir");
    }

    @Test
    void render_writesAnIntegerDiscAsAnIntegerAndASideAsAString() {
        Release release = new Release("id", "Title", List.of(),
                                        List.of(
                                                                        new Release.Track(2, 5, "beef01.flac", "Kink", null),
                                                                        new Release.Track("A", 1, "abcd3f.flac", "Feeling Plain", null)),
                                        List.of(), null);

        String result = underTest.render(release);

        assertThat(result).contains("disc = 2");
        assertThat(result).contains("disc = \"A\"");
    }

    @Test
    void render_producesADocumentThatParsesBackToTheSameValues() {
        String rendered = underTest.render(release());

        TomlTable parsed = TOML.readFromString(rendered);

        assertThat(parsed.get("title").asPrimitive().asString()).isEqualTo("Good Lies");
        assertThat(parsed.get("asset").asArray().get(0).asTable().get("kind").asPrimitive().asString())
                                        .isEqualTo("cover-front");
        assertThat(parsed.get("asset").asArray().get(0).asTable().get("blob").asPrimitive().asString())
                                        .isEqualTo("3f0a91.jpg");
        assertThat(parsed.get("credit").asArray().size()).isEqualTo(1);

        var tracks = parsed.get("track").asArray();
        assertThat(tracks.size()).isEqualTo(2);
        assertThat(tracks.get(0).asTable().get("number").asPrimitive().asLong()).isEqualTo(1);
        assertThat(tracks.get(1).asTable().get("title").asPrimitive().asString()).isEqualTo("Arla Fearn");
    }

    @Test
    void render_keepsTheGivenTrackOrder() {
        String result = underTest.render(release());

        assertThat(result.indexOf("Feeling Plain")).isLessThan(result.indexOf("Arla Fearn"));
    }

    private static Release release() {
        return new Release(
                                        "b27e3c80",
                                        "Good Lies",
                                        List.of(new Release.Credit("main", "overmono")),
                                        List.of(
                                                                        new Release.Track(null, 1, "abcd3f.flac", "Feeling Plain", null),
                                                                        new Release.Track(null, 2, "ab77e1.flac", "Arla Fearn", null)),
                                        List.of(new Release.Asset("cover-front", "3f0a91.jpg", null),
                                                                        new Release.Asset("log", "1a2b3c.txt", null)),
                                        null);
    }

}
