package net.einself.mu.collection;

import io.github.wasabithumb.jtoml.JToml;
import net.einself.mu.collection.api.CollectionRoot;
import net.einself.mu.collection.internal.CollectionServiceImpl;
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

class CollectionServiceImplTest {

    @TempDir
    Path workspace;

    private Path root;

    private final CollectionServiceImpl underTest = new CollectionServiceImpl(JToml.jToml());

    @BeforeEach
    void setUp() throws IOException {
        root = workspace.resolve("collection");
        Files.createDirectories(root.resolve("meta"));
        Files.writeString(root.resolve("meta/.mu"), "format = 1\n");
    }

    @Test
    void findRoot_acceptsAnExplicitRootHoldingTheMarker() {
        CollectionRoot result = underTest.findRoot(root, workspace);

        assertThat(result.path()).isEqualTo(real(root));
    }

    @Test
    void findRoot_rejectsAnExplicitRootWithoutTheMarker() {
        assertThatThrownBy(() -> underTest.findRoot(workspace, workspace))
                                        .isInstanceOf(MuException.class)
                                        .hasMessageContaining("Not a mu collection")
                                        .extracting(e -> ((MuException) e).exitCode())
                                        .isEqualTo(ExitCode.USAGE);
    }

    @Test
    void findRoot_searchesUpwardsFromTheWorkingDirectory() throws IOException {
        // arrange
        Path deep = root.resolve("some/nested/directory");
        Files.createDirectories(deep);

        // act
        CollectionRoot result = underTest.findRoot(null, deep);

        // assert
        assertThat(result.path()).isEqualTo(real(root));
    }

    @Test
    void findRoot_singleArgOverloadAlsoSearchesUpwards() throws IOException {
        Path deep = root.resolve("some/nested/directory");
        Files.createDirectories(deep);

        CollectionRoot result = underTest.findRoot(deep);

        assertThat(result.path()).isEqualTo(real(root));
    }

    @Test
    void findRoot_failsWhenNoParentHoldsTheMarker() throws IOException {
        Path outside = workspace.resolve("outside");
        Files.createDirectories(outside);

        assertThatThrownBy(() -> underTest.findRoot(null, outside))
                                        .isInstanceOf(MuException.class)
                                        .hasMessageContaining("Not a mu collection");
    }

    @Test
    void collectionRoot_derivesTheLayoutPaths() {
        CollectionRoot result = underTest.findRoot(root, workspace);

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
