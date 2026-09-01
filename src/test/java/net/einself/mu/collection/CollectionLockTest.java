package net.einself.mu.collection;

import net.einself.mu.collection.api.CollectionRoot;
import net.einself.mu.collection.internal.CollectionLock;
import net.einself.mu.shared.ExitCode;
import net.einself.mu.shared.MuException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;

class CollectionLockTest {

    @TempDir
    Path workspace;

    private CollectionRoot root;

    @BeforeEach
    void setUp() {
        root = new CollectionRoot(workspace);
    }

    @Test
    void acquire_createsTheLockFileAndItsDirectory() {
        CollectionLock.acquire(root).close();

        assertThat(root.lock()).isRegularFile();
    }

    @Test
    void acquire_succeedsWhenTheLockFileAlreadyExists() throws IOException {
        // arrange
        Files.createDirectories(root.meta());
        Files.createFile(root.lock());

        // act
        var lock = CollectionLock.acquire(root);

        // assert
        assertThat(root.lock()).isRegularFile();
        lock.close();
    }

    @Test
    void acquire_failsWhenTheLockIsAlreadyHeld() {
        // arrange
        var first = CollectionLock.acquire(root);

        // act / assert
        assertThatThrownBy(() -> CollectionLock.acquire(root))
                                        .isInstanceOf(MuException.class)
                                        .hasMessageContaining("Another mu process holds the lock")
                                        .hasMessageContaining(root.lock().toString())
                                        .extracting(e -> ((MuException) e).exitCode())
                                        .isEqualTo(ExitCode.LOCK_HELD);

        first.close();
    }

    @Test
    void acquire_succeedsAgainAfterRelease() {
        try (var ignored = CollectionLock.acquire(root)) {
            // lock held
        }
        // re-acquire succeeds — would throw if close didn't work
        CollectionLock.acquire(root).close();
    }

    @Test
    void close_isIdempotent() {
        var lock = CollectionLock.acquire(root);
        lock.close();
        lock.close();
    }

    @Test
    void acquire_failsWithIoErrorWhenMetaCannotBeCreated() throws IOException {
        Files.createFile(workspace.resolve("meta"));

        assertThatThrownBy(() -> CollectionLock.acquire(root))
                                        .isInstanceOf(MuException.class)
                                        .hasMessageContaining("Cannot open lock file")
                                        .extracting(e -> ((MuException) e).exitCode())
                                        .isEqualTo(ExitCode.IO_ERROR);
    }

    @Test
    void acquire_leavesTheLockFileInPlaceAfterRelease() throws IOException {
        try (var ignored = CollectionLock.acquire(root)) {
            // lock held
        }

        assertThat(root.lock()).isRegularFile();
        assertThat(Files.size(root.lock())).isZero();
    }

    @Test
    void acquire_failingSecondAcquireMustNotReleaseTheFirstLock() throws Exception {
        // arrange
        var first = CollectionLock.acquire(root);
        try {
            CollectionLock.acquire(root);
        } catch (MuException expected) {
            // second acquire fails — but closing its channel drops the OS lock (bug)
        }

        // act — a subprocess tries to acquire; should get LOCK_HELD, but gets SUCCESS
        // if the OS
        // lock was silently dropped
        int exitCode = runLockHelper();
        first.close();

        // assert
        assertThat(exitCode).isEqualTo(ExitCode.LOCK_HELD.value());
    }

    private int runLockHelper() throws Exception {
        var java = ProcessHandle.current().info().command().orElse("java");
        var classpath = System.getProperty("java.class.path");
        var pb = new ProcessBuilder(
                                        java, "-cp", classpath,
                                        LockTestHelper.class.getName(),
                                        root.path().toString());
        var process = pb.start();
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            fail("LockTestHelper timed out");
        }
        return process.exitValue();
    }
}