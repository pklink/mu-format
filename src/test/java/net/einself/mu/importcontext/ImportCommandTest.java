package net.einself.mu.importcontext;

import io.github.wasabithumb.jtoml.JToml;
import io.github.wasabithumb.jtoml.value.array.TomlArray;
import io.github.wasabithumb.jtoml.value.table.TomlTable;
import net.einself.mu.cli.Main;
import net.einself.mu.shared.ExitCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ImportCommandTest {

    @TempDir
    Path workspace;

    private Path root;

    private Path source;

    private ByteArrayOutputStream out;

    private ByteArrayOutputStream err;

    @BeforeEach
    void setUp() throws IOException {
        root = workspace.resolve("collection");
        Files.createDirectories(root.resolve("meta"));
        Files.writeString(root.resolve("meta/.mu"), "format = 1\n");

        source = workspace.resolve("Overmono - Good Lies (2023) [FLAC]");
        Files.createDirectories(source);

        out = new ByteArrayOutputStream();
        err = new ByteArrayOutputStream();
    }

    @Test
    void import_storesEveryFileAndCreatesTheRelease() throws IOException {
        // arrange
        file("01 Feeling Plain.flac", "first");
        file("02 Arla Fearn.flac", "second");
        file("cover.jpg", "image");
        file("rip.log", "log contents");

        // act
        int exitCode = run("import", "--artist", "overmono", source.toString());

        // assert
        assertThat(exitCode).isZero();
        assertThat(blobCount()).isEqualTo(4);

        TomlTable release = readRelease();
        assertThat(release.get("title").asPrimitive().asString())
                                        .isEqualTo("Overmono - Good Lies (2023) [FLAC]");
        assertThat(release.get("track").asArray().size()).isEqualTo(2);
        assertThat(release.get("asset").asArray().size()).isEqualTo(2);
        TomlTable coverAsset = release.get("asset").asArray().get(0).asTable();
        assertThat(coverAsset.get("kind").asPrimitive().asString()).isEqualTo("cover-front");
        assertThat(coverAsset.get("blob").asPrimitive().asString()).endsWith(".jpg");
    }

    @Test
    void import_writesTheMainCreditFromTheArtistOption() throws IOException {
        file("01 Track.flac", "audio");

        run("import", "--artist", "overmono", source.toString());

        TomlArray credits = readRelease().get("credit").asArray();
        assertThat(credits.size()).isEqualTo(1);
        assertThat(credits.get(0).asTable().get("role").asPrimitive().asString()).isEqualTo("main");
        assertThat(credits.get(0).asTable().get("artist").asPrimitive().asString()).isEqualTo("overmono");
    }

    @Test
    void import_warnsAndWritesAnIncompleteReleaseWithoutTheArtistOption() throws IOException {
        file("01 Track.flac", "audio");

        int exitCode = run("import", source.toString());

        assertThat(exitCode).isZero();
        assertThat(err.toString()).contains("no --artist");
        TomlTable credit = readRelease().get("credit").asArray().get(0).asTable();
        assertThat(credit.get("role").asPrimitive().asString()).isEqualTo("main");
        assertThat(credit.get("artist")).isNull();
    }

    @Test
    void import_derivesTrackNumbersAndTitlesFromFilenamePrefixes() throws IOException {
        file("1-05 Kink.flac", "a");
        file("2-01 Feeling Plain.flac", "b");

        run("import", source.toString());

        TomlArray tracks = readRelease().get("track").asArray();
        assertThat(tracks.get(0).asTable().get("disc").asPrimitive().asLong()).isEqualTo(1);
        assertThat(tracks.get(0).asTable().get("number").asPrimitive().asLong()).isEqualTo(5);
        assertThat(tracks.get(0).asTable().get("title").asPrimitive().asString()).isEqualTo("Kink");
        assertThat(tracks.get(1).asTable().get("disc").asPrimitive().asLong()).isEqualTo(2);
    }

    @Test
    void import_numbersSequentiallyWhenAFilenameCarriesNoPrefix() throws IOException {
        // arrange: one prefixed, one not
        file("01 Feeling Plain.flac", "a");
        file("Arla Fearn.flac", "b");

        // act
        run("import", source.toString());

        // assert: numbers come from the position, titles from the whole stem
        TomlArray tracks = readRelease().get("track").asArray();
        assertThat(tracks.get(0).asTable().get("number").asPrimitive().asLong()).isEqualTo(1);
        assertThat(tracks.get(0).asTable().get("title").asPrimitive().asString())
                                        .isEqualTo("01 Feeling Plain");
        assertThat(tracks.get(1).asTable().get("number").asPrimitive().asLong()).isEqualTo(2);
        assertThat(tracks.get(1).asTable().get("title").asPrimitive().asString()).isEqualTo("Arla Fearn");
    }

    @Test
    void import_deduplicatesIdenticalContent() throws IOException {
        file("01 Track.flac", "same");
        file("02 Copy.flac", "same");

        run("import", source.toString());

        assertThat(blobCount()).isEqualTo(1);
        assertThat(out.toString()).contains("1 stored, 1 deduplicated");
    }

    @Test
    void import_recordsOriginPathsWithTheOriginOption() throws IOException {
        // arrange
        file("01 Feeling Plain.flac", "audio");
        Files.createDirectories(source.resolve("artwork"));
        Files.writeString(source.resolve("artwork/cover.jpg"), "image");

        // act
        int exitCode = run("import", "--origin", source.toString());

        // assert
        assertThat(exitCode).isZero();
        TomlTable release = readRelease();
        assertThat(release.get("origin-dir").asPrimitive().asString())
                                        .isEqualTo("Overmono - Good Lies (2023) [FLAC]");
        TomlTable coverAsset = release.get("asset").asArray().get(0).asTable();
        assertThat(coverAsset.get("kind").asPrimitive().asString()).isEqualTo("cover-front");
        assertThat(coverAsset.get("origin-path").asPrimitive().asString())
                                        .isEqualTo("artwork/cover.jpg");
        assertThat(release.get("track").asArray().get(0).asTable()
                                        .get("origin-path").asPrimitive().asString())
                                        .isEqualTo("01 Feeling Plain.flac");
    }

    @Test
    void import_omitsOriginAttributesWithoutTheOption() throws IOException {
        file("01 Track.flac", "audio");

        run("import", source.toString());

        TomlTable release = readRelease();
        assertThat(release.get("origin-dir")).isNull();
        assertThat(release.get("track").asArray().get(0).asTable().get("origin-path")).isNull();
    }

    @Test
    void import_rejectsOriginWithMoreThanOneArgument() throws IOException {
        file("01 Track.flac", "audio");

        int exitCode = run("import", "--origin", source.toString(), source.toString());

        assertThat(exitCode).isEqualTo(2);
        assertThat(err.toString()).contains("exactly one directory");
    }

    @Test
    void import_abortsBeforeWritingWhenAnOriginSegmentIsInvalid() throws IOException {
        // arrange: a trailing space is not a portable name
        file("01 Track.flac", "audio");
        Files.createDirectories(source.resolve("bad name "));
        Files.writeString(source.resolve("bad name /note.txt"), "x");

        // act
        int exitCode = run("import", "--origin", source.toString());

        // assert: exit 1 and nothing written at all
        assertThat(exitCode).isEqualTo(1);
        assertThat(err.toString()).contains("bad name ");
        assertThat(root.resolve("store")).doesNotExist();
        assertThat(root.resolve("meta/releases")).doesNotExist();
    }

    @Test
    void import_writesNothingOnADryRun() throws IOException {
        file("01 Feeling Plain.flac", "audio");

        int exitCode = run("import", "--dry-run", source.toString());

        assertThat(exitCode).isZero();
        assertThat(root.resolve("store")).doesNotExist();
        assertThat(root.resolve("meta/releases")).doesNotExist();
        assertThat(out.toString()).contains("[[track]]").contains("would create");
    }

    @Test
    void import_rejectsTheNotImplementedReleaseOption() throws IOException {
        file("01 Track.flac", "audio");

        int exitCode = run("import", "--release", "some-id", source.toString());

        assertThat(exitCode).isEqualTo(2);
        assertThat(err.toString()).contains("--release is not implemented");
    }

    @Test
    void import_failsWithoutACollectionRoot() throws IOException {
        file("01 Track.flac", "audio");
        Files.delete(root.resolve("meta/.mu"));

        int exitCode = run("import", source.toString());

        assertThat(exitCode).isEqualTo(2);
        assertThat(err.toString()).contains("Not a mu collection");
    }

    @Test
    void import_refusesAFormatItDoesNotImplement() throws IOException {
        file("01 Track.flac", "audio");
        Files.writeString(root.resolve("meta/.mu"), "format = 2\n");

        int exitCode = run("import", source.toString());

        assertThat(exitCode).isEqualTo(2);
        assertThat(err.toString()).contains("format 2");
    }

    @Test
    void import_abortsWhenTheLockIsHeld() throws IOException {
        file("01 Track.flac", "audio");
        Path lockFile = root.resolve("meta/.lock");

        try (FileChannel channel = FileChannel.open(lockFile,
                                        StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                                        FileLock ignored = channel.lock()) {

            int exitCode = run("import", source.toString());

            assertThat(exitCode).isEqualTo(3);
            assertThat(err.toString()).contains("holds the lock");
        }
    }

    @Test
    void import_producesAnEntityFileWithLfEndingsAndNoBom() throws IOException {
        file("01 Track.flac", "audio");

        run("import", source.toString());

        byte[] bytes = Files.readAllBytes(releaseFile());
        assertThat(bytes).doesNotContain((byte) '\r');
        assertThat(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)).doesNotStartWith("\uFEFF");
    }

    @Test
    void import_jsonFlagOutputsJsonStructure() throws IOException {
        file("01 Track.flac", "audio");

        int exitCode = run("--format", "json", "import", "--artist", "overmono", source.toString());

        assertThat(exitCode).isZero();
        assertThat(out.toString())
                                        .contains("\"command\":\"import\"")
                                        .contains("\"path\"")
                                        .contains("\"dryRun\":false")
                                        .contains("\"files\":1")
                                        .contains("\"stored\":1")
                                        .contains("\"deduplicated\":0")
                                        .contains("\"warnings\":[]");
    }

    @Test
    void import_jsonFlagIncludesWarnings() throws IOException {
        file("01 Track.flac", "audio");

        int exitCode = run("--format", "json", "import", source.toString());

        assertThat(exitCode).isZero();
        assertThat(out.toString())
                                        .contains("\"warnings\":[")
                                        .contains("no --artist");
    }

    @Test
    void import_rejectsAnInvalidFormat() throws IOException {
        file("01 Track.flac", "audio");

        int exitCode = run("--format", "yaml", "import", source.toString());

        assertThat(exitCode).isEqualTo(ExitCode.USAGE.value());
        assertThat(err.toString()).contains("--format");
        assertThat(root.resolve("store")).doesNotExist();
        assertThat(root.resolve("meta/releases")).doesNotExist();
    }

    @Test
    void import_acceptsJsonFormatCaseInsensitively() throws IOException {
        file("01 Track.flac", "audio");

        int exitCode = run("--format", "JSON", "import", "--artist", "overmono", source.toString());

        assertThat(exitCode).isZero();
        assertThat(out.toString()).contains("\"command\":\"import\"");
    }

    /**
     * Every invocation passes {@code --root} explicitly; the upward search is
     * covered by {@link net.einself.mu.collection.CollectionServiceImplTest}.
     */
    private int run(String... args) {
        String[] withRoot = Stream.concat(
                                        Stream.of("--root", root.toString()),
                                        Stream.of(args))
                                        .toArray(String[]::new);
        return Main.execute(withRoot, new PrintStream(out), new PrintStream(err));
    }

    private void file(String name, String content) throws IOException {
        Files.writeString(source.resolve(name), content);
    }

    private long blobCount() throws IOException {
        Path store = root.resolve("store");
        try (Stream<Path> walk = Files.walk(store)) {
            return walk.filter(Files::isRegularFile)
                                            .filter(path -> !path.getParent().getFileName().toString().equals(".tmp"))
                                            .count();
        }
    }

    private Path releaseFile() throws IOException {
        try (Stream<Path> entries = Files.list(root.resolve("meta/releases"))) {
            List<Path> files = entries.toList();
            assertThat(files).hasSize(1);
            return files.get(0);
        }
    }

    private TomlTable readRelease() throws IOException {
        return JToml.jToml().read(releaseFile());
    }

}
