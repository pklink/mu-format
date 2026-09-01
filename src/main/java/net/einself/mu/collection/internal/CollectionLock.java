package net.einself.mu.collection.internal;

import net.einself.mu.collection.api.CollectionRoot;
import net.einself.mu.collection.api.LockHandle;
import net.einself.mu.shared.ExitCode;
import net.einself.mu.shared.MuException;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The advisory write lock on {@code meta/.lock}.
 *
 * <p>
 * A second {@code mu} process aborts immediately rather than waiting. The lock
 * file itself has no content anyone interprets and is never versioned (SPEC.md
 * section 6).
 */
public class CollectionLock implements LockHandle {

    private static final Set<Path> locks = ConcurrentHashMap.newKeySet();

    private final Path lockFile;
    private final FileChannel channel;

    private CollectionLock(Path lockFile, FileChannel channel) {
        this.lockFile = lockFile;
        this.channel = channel;
    }

    public static CollectionLock acquire(CollectionRoot root) {
        Path lockFile = root.lock();
        if (!locks.add(lockFile)) {
            throw new MuException(ExitCode.LOCK_HELD,
                                            "Another mu process holds the lock: " + lockFile);
        }
        FileChannel channel = null;
        boolean acquired = false;
        try {
            Files.createDirectories(root.meta());
            channel = FileChannel.open(lockFile,
                                            StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock lock = channel.tryLock();
            if (lock == null) {
                throw new MuException(ExitCode.LOCK_HELD,
                                                "Another mu process holds the lock: " + lockFile);
            }
            acquired = true;
            return new CollectionLock(lockFile, channel);
        } catch (OverlappingFileLockException e) {
            throw new MuException(ExitCode.LOCK_HELD,
                                            "Another mu process holds the lock: " + lockFile);
        } catch (IOException e) {
            throw new MuException(ExitCode.IO_ERROR,
                                            "Cannot open lock file " + lockFile + ": " + e.getMessage(), e);
        } finally {
            if (!acquired) {
                locks.remove(lockFile);
                closeQuietly(channel);
            }
        }
    }

    @Override
    public void close() {
        locks.remove(lockFile);
        closeQuietly(channel);
    }

    private static void closeQuietly(@Nullable FileChannel channel) {
        if (channel == null) {
            return;
        }
        try {
            channel.close();
        } catch (IOException ignored) {
            // nothing useful left to do
        }
    }

}