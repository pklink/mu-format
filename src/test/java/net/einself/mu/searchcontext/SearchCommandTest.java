package net.einself.mu.searchcontext;

import net.einself.mu.shared.ExitCode;
import net.einself.mu.cli.Main;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SearchCommandTest {

    @TempDir
    Path workspace;

    private Path root;

    private ByteArrayOutputStream out;

    private ByteArrayOutputStream err;

    @BeforeEach
    void setUp() throws IOException {
        root = workspace.resolve("collection");
        Files.createDirectories(root.resolve("meta/releases"));
        Files.createDirectories(root.resolve("meta/artists"));
        Files.writeString(root.resolve("meta/.mu"), "format = 1\n");

        Files.writeString(root.resolve("meta/releases/good-lies.mu"), """
                title = "Good Lies"
                type = "album"
                release-year-original = "2023"
                source-medium = "cd"
                [[credit]]
                role = "main"
                artist = "overmono"
                [[track]]
                number = 1
                title = "Feeling Plain"
                [[track]]
                number = 2
                title = "Kink"
                [[track.credit]]
                role = "feat"
                artist = "anz"
                """.stripIndent());
        Files.writeString(root.resolve("meta/releases/dub.mu"), """
                title = "Dub"
                release-year-original = "2024"
                source-medium = "vinyl"
                [[credit]]
                role = "main"
                artist = "underworld"
                [[track]]
                number = 1
                title = "Two Months Off"
                """.stripIndent());
        Files.writeString(root.resolve("meta/artists/overmono.mu"), """
                name = "Overmono"
                is-group = true
                member = ["tom-russell", "ed-russell"]
                """.stripIndent());
        Files.writeString(root.resolve("meta/artists/anz.mu"), "name = \"Anz\"\n");
        Files.writeString(root.resolve("meta/artists/underworld.mu"), "name = \"Underworld\"\n");

        out = new ByteArrayOutputStream();
        err = new ByteArrayOutputStream();
    }

    @Test
    void search_findsReleasesArtistsAndTracksWithOneQuery() {
        int exitCode = run("search", "over");

        assertThat(exitCode).isZero();
        assertThat(out.toString())
                .contains("Artists (1 found):")
                .contains("overmono");
    }

    @Test
    void search_findsAReleaseThroughItsCredit() {
        int exitCode = run("search", "overmono");

        assertThat(exitCode).isZero();
        assertThat(out.toString())
                .contains("Releases (1 found):")
                .contains("good-lies")
                .contains("matched-credit: overmono (main)");
    }

    @Test
    void search_findsTrackByTitle() {
        int exitCode = run("search", "--type", "track", "feeling");

        assertThat(exitCode).isZero();
        assertThat(out.toString())
                .contains("Tracks (1 found):")
                .contains("release: good-lies")
                .contains("title: Feeling Plain");
    }

    @Test
    void search_typeRestrictsResults() {
        int exitCode = run("search", "--type", "release", "over");

        assertThat(exitCode).isZero();
        assertThat(out.toString())
                .contains("Releases")
                .doesNotContain("Artists (");
    }

    @Test
    void search_filtersByYearAndMedium() {
        int exitCode = run("search", "--type", "release", "--year", "2023", "--medium", "cd", "good");

        assertThat(exitCode).isZero();
        assertThat(out.toString()).contains("good-lies");

        exitCode = run("search", "--type", "release", "--year", "2024", "good");
        assertThat(exitCode).isZero();
        assertThat(out.toString()).contains("No matches.");
    }

    @Test
    void search_roleRestrictsCreditMatching() {
        int exitCode = run("search", "--role", "feat", "anz");

        assertThat(exitCode).isZero();
        assertThat(out.toString()).contains("good-lies")
                .contains("matched-credit: anz (feat)");

        exitCode = run("search", "--type", "release", "--role", "producer", "anz");
        assertThat(exitCode).isZero();
        assertThat(out.toString()).contains("No matches.");
    }

    @Test
    void search_fieldRestrictsMatching() {
        int exitCode = run("search", "--field", "notes", "overmono");

        assertThat(exitCode).isZero();
        assertThat(out.toString()).contains("No matches.");
    }

    @Test
    void search_limitCapsTheResults() {
        int exitCode = run("search", "--type", "track", "--limit", "1", "o");

        assertThat(exitCode).isZero();
        assertThat(out.toString()).contains("Tracks (1 found):");
    }

    @Test
    void search_jsonFormatEmitsAllGroups() {
        int exitCode = run("--format", "json", "search", "--type", "release", "good");

        assertThat(exitCode).isZero();
        assertThat(out.toString())
                .contains("\"command\":\"search\"")
                .contains("\"total\":1")
                .contains("\"releases\":[")
                .contains("\"id\":\"good-lies\"")
                .contains("\"artists\":[]")
                .contains("\"tracks\":[]");
    }

    @Test
    void search_reportsZeroMatchesAsSuccess() {
        int exitCode = run("search", "no-such-thing");

        assertThat(exitCode).isZero();
        assertThat(out.toString()).contains("No matches.");
    }

    @Test
    void search_rejectsAnInvalidFormat() {
        int exitCode = run("--format", "yaml", "search", "x");

        assertThat(exitCode).isEqualTo(ExitCode.USAGE.value());
        assertThat(err.toString()).contains("--format");
    }

    @Test
    void search_acceptsJsonFormatCaseInsensitively() {
        int exitCode = run("--format", "JSON", "search", "--type", "release", "good");

        assertThat(exitCode).isZero();
        assertThat(out.toString()).contains("\"command\":\"search\"");
    }

    @Test
    void search_rejectsAnInvalidType() {
        int exitCode = run("search", "--type", "album", "x");

        assertThat(exitCode).isEqualTo(ExitCode.USAGE.value());
        assertThat(err.toString()).contains("--type");
    }

    @Test
    void search_rejectsRoleWithoutReleasesInScope() {
        int exitCode = run("search", "--type", "artist", "--role", "main", "x");

        assertThat(exitCode).isEqualTo(ExitCode.USAGE.value());
        assertThat(err.toString()).contains("--role");
    }

    @Test
    void search_skipsBrokenEntityFilesWithAWarning() throws IOException {
        Files.writeString(root.resolve("meta/releases/broken.mu"), "title = [unclosed\n");

        int exitCode = run("search", "--type", "release", "good");

        assertThat(exitCode).isZero();
        assertThat(out.toString()).contains("good-lies");
        assertThat(err.toString()).contains("skipping").contains("broken.mu");
    }

    @Test
    void search_worksWithEmptyMetaDirectories() throws IOException {
        Path empty = workspace.resolve("empty");
        Files.createDirectories(empty.resolve("meta/releases"));
        Files.createDirectories(empty.resolve("meta/artists"));
        Files.writeString(empty.resolve("meta/.mu"), "format = 1\n");

        String[] args = {"--root", empty.toString(), "search", "anything"};
        int exitCode = Main.execute(args, new PrintStream(out), new PrintStream(err));

        assertThat(exitCode).isZero();
        assertThat(out.toString()).contains("No matches.");
    }

    /**
     * Every invocation passes {@code --root} explicitly, like {@code ImportCommandTest}.
     */
    private int run(String... args) {
        String[] withRoot = Stream.concat(
                        Stream.of("--root", root.toString()),
                        Stream.of(args))
                .toArray(String[]::new);
        return Main.execute(withRoot, new PrintStream(out), new PrintStream(err));
    }

}
