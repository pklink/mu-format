package net.einself.mu.reader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HasMuFolderTest {

    private final HasMuFolder underTest = new HasMuFolder();

    @ParameterizedTest
    @ValueSource(strings = { "reader/has-mu-folder", "reader/has-mu-folder/" })
    void exists(String resourcePath) throws URISyntaxException {
        URL resource = getClass().getClassLoader().getResource(resourcePath);
        Path path = Path.of(resource.toURI());
        assertTrue(underTest.test(path));
    }

    @Test
    void doesNoExists() throws URISyntaxException {
        URL resource = getClass().getClassLoader().getResource("reader/has-mu-folder/=mu");
        Path path = Path.of(resource.toURI());
        assertFalse(underTest.test(path));
    }

}