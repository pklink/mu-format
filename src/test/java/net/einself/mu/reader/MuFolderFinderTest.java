package net.einself.mu.reader;

import net.einself.mu.dto.MuFolder;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MuFolderFinderTest {

    private final MuFolderFinder underTest = new MuFolderFinder();

    @Test
    void findAll() throws URISyntaxException {
        // arrange
        URL resource = getClass().getClassLoader().getResource("reader/mu-folder-finder");
        Path path = Path.of(resource.toURI());

        // act
        List<MuFolder> result = underTest.findAll(path);

        // assert
        assertThat(result)
                .hasSize(2)
                .anySatisfy(muFolder -> {
                    assertThat(muFolder.root()).endsWith(Path.of("reader/mu-folder-finder/foo-1"));
                    assertThat(muFolder.mu()).endsWith(Path.of("reader/mu-folder-finder/foo-1/=mu"));
                })
                .anySatisfy(muFolder -> {
                    assertThat(muFolder.root()).endsWith(Path.of("reader/mu-folder-finder/foo-3/bar-1"));
                    assertThat(muFolder.mu()).endsWith(Path.of("reader/mu-folder-finder/foo-3/bar-1/=mu"));
                });
    }

}