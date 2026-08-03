package net.einself.mu.naming;

import net.einself.mu.naming.api.NameSanitizer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class NameSanitizerTest {

    private final NameSanitizer underTest = new NameSanitizer();

    @Test
    void apply_normalizesToNfc() {
        // "é" as e + combining acute
        String decomposed = "Caf\u0065\u0301";

        String result = underTest.apply(decomposed);

        assertThat(result).isEqualTo("Caf\u00e9");
    }

    @Test
    void apply_replacesSeparatorsWithUnderscore() {
        assertThat(underTest.apply("AC/DC")).isEqualTo("AC_DC");
    }

    @Test
    void apply_stripsControlCharacters() {
        assertThat(underTest.apply("a\u0000b\u001fc")).isEqualTo("abc");
    }

    @Test
    void apply_trimsLeadingAndTrailingSpacesAndDots() {
        assertThat(underTest.apply("  .hidden.  ")).isEqualTo("hidden");
        assertThat(underTest.apply("..")).isEqualTo("_");
    }

    @Test
    void apply_truncatesTo200BytesWithoutSplittingACodepoint() {
        String result = underTest.apply("\u00e4".repeat(150));

        // "ä" is two bytes: 100 of them fit into 200 bytes
        assertThat(result).hasSize(100);
        assertThat(result.getBytes(StandardCharsets.UTF_8)).hasSize(200);
    }

    @Test
    void apply_truncatesOnACodepointBoundaryWhenTheLimitFallsMidCharacter() {
        // 199 ASCII bytes then a two-byte character: the last character must not be split
        String value = "a".repeat(199) + "\u00e4";

        byte[] result = underTest.apply(value).getBytes(StandardCharsets.UTF_8);

        assertThat(result).hasSize(199);
    }

    @Test
    void apply_replacesAnEmptyResultWithUnderscore() {
        assertThat(underTest.apply("")).isEqualTo("_");
        assertThat(underTest.apply("\u0001")).isEqualTo("_");
    }

    @Test
    void isUnchanged_holdsExactlyForValuesThatSurviveSanitization() {
        assertThat(underTest.isUnchanged("Overmono - Good Lies (2023) [FLAC]")).isTrue();
        assertThat(underTest.isUnchanged("01 Feeling Plain.flac")).isTrue();

        assertThat(underTest.isUnchanged("with/slash")).isFalse();
        assertThat(underTest.isUnchanged(" leading space")).isFalse();
        assertThat(underTest.isUnchanged("trailing dot.")).isFalse();
    }

}
