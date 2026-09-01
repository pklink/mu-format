package net.einself.mu.naming;

import net.einself.mu.naming.api.ExtensionDeriver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExtensionDeriverTest {

    private final ExtensionDeriver underTest = new ExtensionDeriver();

    @Test
    void derive_takesThePartAfterTheLastDot() {
        assertThat(underTest.derive("01 Feeling Plain.flac")).contains("flac");
        assertThat(underTest.derive("cover.front.jpg")).contains("jpg");
    }

    @Test
    void derive_lowerCasesWithAsciiRules() {
        assertThat(underTest.derive("COVER.JPG")).contains("jpg");
        assertThat(underTest.derive("scan.TIFF")).contains("tiff");
    }

    @Test
    void derive_rejectsWhatDoesNotMatchThePattern() {
        // longer than 8 characters, or not alphanumeric
        assertThat(underTest.derive("archive.extension")).isEmpty();
        assertThat(underTest.derive("weird.a-b")).isEmpty();
        assertThat(underTest.derive("umlaut.mü")).isEmpty();
    }

    @Test
    void derive_treatsALeadingDotAsNoExtension() {
        assertThat(underTest.derive(".gitignore")).isEmpty();
    }

    @Test
    void derive_ignoresAMissingOrTrailingDot() {
        assertThat(underTest.derive("README")).isEmpty();
        assertThat(underTest.derive("trailing.")).isEmpty();
    }

    @Test
    void reference_appendsOnlyAQualifyingExtension() {
        assertThat(underTest.reference("abcd", "track.flac")).isEqualTo("abcd.flac");
        assertThat(underTest.reference("abcd", "README")).isEqualTo("abcd.bin");
    }

}
