package net.einself.mu.storage;

import net.einself.mu.collection.api.CollectionRoot;
import net.einself.mu.storage.api.Blob;
import net.einself.mu.storage.internal.FileSystemBlobStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

class BlobStoreTest {

    /** SHA-256 of "hello" */
    private static final String HELLO_HASH =
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";

    @TempDir
    Path root;

    private CollectionRoot collectionRoot;

    private FileSystemBlobStore underTest;

    @BeforeEach
    void setUp() {
        collectionRoot = new CollectionRoot(root);
        underTest = new FileSystemBlobStore(collectionRoot);
    }

    @Test
    void take_storesTheFileUnderTheHashOfItsContent() throws IOException {
        // arrange
        Path source = write("source.txt", "hello");

        // act
        Blob result = underTest.take(source);

        // assert
        assertThat(result.hash()).isEqualTo(HELLO_HASH);
        assertThat(result.deduplicated()).isFalse();
        assertThat(root.resolve("store/2c").resolve(HELLO_HASH))
                .exists()
                .hasContent("hello");
    }

    @Test
    void take_shardsByTheFirstTwoCharacters() throws IOException {
        Blob result = underTest.take(write("source.txt", "hello"));

        assertThat(result.relativePath()).isEqualTo("store/2c/" + HELLO_HASH);
    }

    @Test
    void take_deduplicatesIdenticalContentRegardlessOfName() throws IOException {
        // arrange
        underTest.take(write("first.txt", "hello"));

        // act
        Blob result = underTest.take(write("second-name.dat", "hello"));

        // assert
        assertThat(result.deduplicated()).isTrue();
        assertThat(result.hash()).isEqualTo(HELLO_HASH);
        try (var entries = Files.list(root.resolve("store/2c"))) {
            assertThat(entries).hasSize(1);
        }
    }

    @Test
    void take_leavesNothingInStaging() throws IOException {
        underTest.take(write("first.txt", "hello"));
        underTest.take(write("second.txt", "hello"));

        try (var entries = Files.list(collectionRoot.staging())) {
            assertThat(entries).isEmpty();
        }
    }

    @Test
    void take_makesTheBlobReadOnly() throws IOException {
        assumeThat(FileSystems.getDefault().supportedFileAttributeViews()).contains("posix");

        Blob result = underTest.take(write("source.txt", "hello"));

        Set<PosixFilePermission> permissions =
                Files.getPosixFilePermissions(root.resolve(result.relativePath()));
        assertThat(permissions).containsExactlyInAnyOrder(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.GROUP_READ,
                PosixFilePermission.OTHERS_READ);
    }

    @Test
    void clearStaging_removesWhatAnInterruptedRunLeftBehind() throws IOException {
        // arrange
        Files.createDirectories(collectionRoot.staging());
        Files.writeString(collectionRoot.staging().resolve("leftover"), "garbage");

        // act
        underTest.clearStaging();

        // assert
        try (var entries = Files.list(collectionRoot.staging())) {
            assertThat(entries).isEmpty();
        }
    }

    @Test
    void inspect_hashesWithoutWritingAnything() throws IOException {
        // arrange
        Path source = write("source.txt", "hello");

        // act
        Blob result = underTest.inspect(source);

        // assert
        assertThat(result.hash()).isEqualTo(HELLO_HASH);
        assertThat(result.deduplicated()).isFalse();
        assertThat(root.resolve("store")).doesNotExist();
    }

    @Test
    void inspect_reportsAnExistingBlobAsDeduplicated() throws IOException {
        underTest.take(write("first.txt", "hello"));

        assertThat(underTest.inspect(write("second.txt", "hello")).deduplicated()).isTrue();
    }

    private Path write(String name, String content) throws IOException {
        Path source = root.resolve(name);
        Files.writeString(source, content);
        return source;
    }

}
