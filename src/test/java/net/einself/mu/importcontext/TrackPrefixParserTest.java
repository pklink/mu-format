package net.einself.mu.importcontext;

import net.einself.mu.importcontext.internal.TrackPosition;
import net.einself.mu.importcontext.internal.TrackPrefixParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrackPrefixParserTest {

    private final TrackPrefixParser underTest = new TrackPrefixParser();

    @Test
    void parse_readsAPlainNumberPrefix() {
        TrackPosition result = underTest.parse("01 Feeling Plain").orElseThrow();

        assertThat(result.disc()).isNull();
        assertThat(result.number()).isEqualTo(1);
        assertThat(result.title()).isEqualTo("Feeling Plain");
    }

    @Test
    void parse_readsADiscAndNumberPrefix() {
        TrackPosition result = underTest.parse("1-05 Kink").orElseThrow();

        assertThat(result.disc()).isEqualTo(1);
        assertThat(result.number()).isEqualTo(5);
        assertThat(result.title()).isEqualTo("Kink");
    }

    @Test
    void parse_readsAMediumSideAsAStringDisc() {
        TrackPosition result = underTest.parse("A1 Feeling Plain").orElseThrow();

        assertThat(result.disc()).isEqualTo("A");
        assertThat(result.number()).isEqualTo(1);
        assertThat(result.title()).isEqualTo("Feeling Plain");
    }

    @Test
    void parse_readsAHyphenatedMediumSide() {
        TrackPosition result = underTest.parse("A-01 Feeling Plain").orElseThrow();

        assertThat(result.disc()).isEqualTo("A");
        assertThat(result.number()).isEqualTo(1);
    }

    @Test
    void parse_acceptsUnderscoreAndDotSeparators() {
        assertThat(underTest.parse("03_Waffle House").orElseThrow().title()).isEqualTo("Waffle House");
        assertThat(underTest.parse("03.Waffle House").orElseThrow().title()).isEqualTo("Waffle House");
        assertThat(underTest.parse("03 - Waffle House").orElseThrow().title()).isEqualTo("Waffle House");
    }

    @Test
    void parse_doesNotReadAYearAsATrackNumber() {
        assertThat(underTest.parse("1984 Song")).isEmpty();
    }

    @Test
    void parse_rejectsANumberBelowOne() {
        // SPEC.md section 4.7 requires number >= 1
        assertThat(underTest.parse("00 Intro")).isEmpty();
    }

    @Test
    void parse_returnsEmptyWithoutAPrefix() {
        assertThat(underTest.parse("Feeling Plain")).isEmpty();
        assertThat(underTest.parse("")).isEmpty();
    }

    @Test
    void parse_normalizesTheTitleToNfc() {
        TrackPosition result = underTest.parse("01 Fu\u0308\u00dfballprofi").orElseThrow();

        assertThat(result.title()).isEqualTo("F\u00fc\u00dfballprofi");
    }

}
