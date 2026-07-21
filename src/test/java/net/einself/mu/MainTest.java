package net.einself.mu;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MainTest {

    @Test
    void run_printsReleaseWithAllAttributes() throws URISyntaxException {
        // arrange
        URL resource = getClass().getClassLoader().getResource("reader/main-cli");
        Path root = Path.of(resource.toURI());
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        // act
        Main.run(root, new PrintStream(buffer));

        // assert
        String output = buffer.toString();
        assertThat(output)
                .contains("artist: Test Artist")
                .contains("title: Test Title")
                .contains("release-year: 2020");
    }

}
