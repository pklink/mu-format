package net.einself.mu.reader;

import net.einself.mu.dto.Attribute;
import net.einself.mu.dto.Attributes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AttributeReaderTest {

    private AttributeReader underTest;

    @BeforeEach
    void setUp() {
        AttributeKeyExtractor attributeKeyExtractor = new AttributeKeyExtractor();
        underTest = new AttributeReader(attributeKeyExtractor);
    }

    @Test
    void simple() throws URISyntaxException {
        Path muPath = Path.of(getClass().getClassLoader().getResource("reader/attribute-reader/simple/=mu").toURI());
        Attributes result = underTest.read(muPath);

        assertNotNull(result);
        assertThat(result.getArtists()).map(Attribute::value).containsExactlyInAnyOrder("Boo Artist", "Foo Artist");
        assertThat(result.getReleaseYear()).isPresent().map(Attribute::value).hasValue("2006");
        assertThat(result.getAttribute("foo")).isPresent().map(Attribute::value).hasValue("bar");
    }

    @Test
    void single() throws URISyntaxException {
        Path muPath = Path.of(getClass().getClassLoader().getResource("reader/attribute-reader/single/=mu").toURI());
        Attributes result = underTest.read(muPath);

        assertNotNull(result);
        assertThat(result.getAttribute("foo"))
                .map(Attribute::value)
                .contains("bar");
    }

    @Test
    void multipleUnordered() throws URISyntaxException {

        Path muPath = Path.of(getClass().getClassLoader().getResource("reader/attribute-reader/multiple-unordered/=mu").toURI());
        Attributes result = underTest.read(muPath);

        assertNotNull(result);
        assertThat(result.getAttributes("artist"))
                .map(Attribute::value)
                .containsExactlyInAnyOrder("Foo Artist", "Bar Artist");
    }

    @Test
    void multipleOrdered() throws URISyntaxException {
        Path muPath = Path.of(getClass().getClassLoader().getResource("reader/attribute-reader/multiple-ordered/=mu").toURI());
        Attributes result = underTest.read(muPath);

        assertNotNull(result);
        assertThat(result.getAttributes("foo"))
                .map(Attribute::value)
                .containsExactly("Foo Foo", "Bar Foo", "B3r Foo");
    }

}