package net.einself.mu.reader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
class HasMuExtensionTest {

    private final HasMuExtension hasMuExtension = new HasMuExtension();

    @ParameterizedTest
    @CsvSource({
            "true,reader/has-mu-extension/foo.mu",
            "false,reader/has-mu-extension/foo.txt",
            "false,reader/has-mu-extension/foo.mu.txt",
            "true,reader/has-mu-extension/foo.MU",
            "true,reader/has-mu-extension/foo.mU",
            "false,",
    })
    void test(Boolean expected, Path path) {
        assertEquals(expected, hasMuExtension.test(path));
    }

    @Test
    void test_null() {
        assertFalse(hasMuExtension.test(null));
    }
}