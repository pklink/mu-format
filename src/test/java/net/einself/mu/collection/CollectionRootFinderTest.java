package net.einself.mu.collection;

import net.einself.mu.collection.api.CollectionRoot;
import net.einself.mu.collection.internal.CollectionRootFinder;
import net.einself.mu.shared.ExitCode;
import net.einself.mu.shared.MuException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CollectionRootFinderTest {

    @TempDir
    Path workspace;

    private Path root;

    private final CollectionRootFinder underTest = new CollectionRootFinder();

    @BeforeEach
    void setUp() throws IOException {
        root = workspace.resolve("collection");
        Files.createDirectories(root.resolve("meta"));
        Files.writeString(root.resolve("meta/.mu"), "format = 1\n");
    }

    @Test
    void find_acceptsAnExplicitRootHoldingTheMarker() {
        CollectionRoot result = underTest.find(root, workspace);

        assertThat(result.path()).isEqualTo(real(root));
    }

    @Test
    void find_rejectsAnExplicitRootWithoutTheMarker() {
        assertThatThrownBy(() -> underTest.find(workspace, workspace))
                                        .isInstanceOf(MuException.class)
                                        .hasMessageContaining("Not a mu collection")
                                        .extracting(e -> ((MuException) e).exitCode())
                                        .isEqualTo(ExitCode.USAGE);
    }

    @Test
    void find_searchesUpwardsFromTheWorkingDirectory() throws IOException {
        // arrange
        Path deep = root.resolve("some/nested/directory");
        Files.createDirectories(deep);

        // act
        CollectionRoot result = underTest.find(null, deep);

        // assert
        assertThat(result.path()).isEqualTo(real(root));
    }

    @Test
    void find_failsWhenNoParentHoldsTheMarker() throws IOException {
        Path outside = workspace.resolve("outside");
        Files.createDirectories(outside);

        assertThatThrownBy(() -> underTest.find(null, outside))
                                        .isInstanceOf(MuException.class)
                                        .hasMessageContaining("Not a mu collection");
    }

    @Test
    void collectionRoot_derivesTheLayoutPaths() {
        CollectionRoot result = underTest.find(root, workspace);

        assertThat(result.marker()).isEqualTo(result.meta().resolve(".mu"));
        assertThat(result.releases()).isEqualTo(result.meta().resolve("releases"));
        assertThat(result.staging()).isEqualTo(result.store().resolve(".tmp"));
        assertThat(result.lock()).isEqualTo(result.meta().resolve(".lock"));
    }

    private static Path real(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            return path.toAbsolutePath().normalize();
        }
    }

}
