package net.einself.reader;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AttributeKeyExtractorTest {

    @Test
    void name() {
        AttributeKeyExtractor underTest = new AttributeKeyExtractor();

        assertThat(underTest.extract("foo=bar").name()).isEqualTo("foo");
        assertThat(underTest.extract("foo=bar").order()).isEqualTo(-1);

        assertThat(underTest.extract("foo=").name()).isEqualTo("foo");
        assertThat(underTest.extract("foo=").order()).isEqualTo(-1);

        assertThat(underTest.extract("foo").name()).isEqualTo("foo");
        assertThat(underTest.extract("foo").order()).isEqualTo(-1);

        assertThat(underTest.extract("foo-1").name()).isEqualTo("foo");
        assertThat(underTest.extract("foo-1").order()).isEqualTo(1);

        assertThat(underTest.extract("foo-10").name()).isEqualTo("foo");
        assertThat(underTest.extract("foo-10").order()).isEqualTo(10);

        assertThat(underTest.extract("foo-01").name()).isEqualTo("foo");
        assertThat(underTest.extract("foo-01").order()).isEqualTo(1);

        assertThat(underTest.extract("foo-100").name()).isEqualTo("foo");
        assertThat(underTest.extract("foo-100").order()).isEqualTo(100);

        assertThat(underTest.extract("foo-001").name()).isEqualTo("foo");
        assertThat(underTest.extract("foo-001").order()).isEqualTo(1);

        assertThat(underTest.extract("foo-bar").name()).isEqualTo("foo-bar");
        assertThat(underTest.extract("foo-bar").order()).isEqualTo(-1);

        assertThat(underTest.extract("foo-bar-1").name()).isEqualTo("foo-bar");
        assertThat(underTest.extract("foo-bar-1").order()).isEqualTo(1);

        assertThat(underTest.extract("foo-bar-1a").name()).isEqualTo("foo-bar-1a");
        assertThat(underTest.extract("foo-bar-1a").order()).isEqualTo(-1);
    }

}