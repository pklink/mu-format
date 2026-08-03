package net.einself.mu.collection.internal;

import net.einself.mu.collection.api.CollectionRoot;
import net.einself.mu.collection.api.LockHandle;
import net.einself.mu.shared.ExitCode;
import net.einself.mu.collection.api.CollectionRoot;
import net.einself.mu.shared.MuException;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * The advisory write lock on {@code meta/.lock}.
 *
 * <p>A second {@code mu} process aborts immediately rather than waiting. The lock file itself
 * has no content anyone interprets and is never versioned (SPEC.md section 6).
 */
public class CollectionLock implements LockHandle {

    private final FileChannel channel;

    private final FileLock lock;

    private CollectionLock(FileChannel channel, FileLock lock) {
        this.channel = channel;
        this.lock = lock;
    }

    public static CollectionLock acquire(CollectionRoot root) {
        Path lockFile = root.lock();
        FileChannel channel = null;
        try {
            Files.createDirectories(root.meta());
            channel = FileChannel.open(lockFile,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock lock = channel.tryLock();
            if (lock == null) {
                throw locked(lockFile, channel);
            }
            return new CollectionLock(channel, lock);
        } catch (OverlappingFileLockException e) {
            throw locked(lockFile, channel);
        } catch (IOException e) {
            closeQuietly(channel);
            throw new MuException(ExitCode.IO_ERROR,
                    "Cannot open lock file " + lockFile + ": " + e.getMessage(), e);
        }
    }

    private static MuException locked(Path lockFile, FileChannel channel) {
        closeQuietly(channel);
        return new MuException(ExitCode.LOCK_HELD,
                "Another mu process holds the lock: " + lockFile);
    }

    @Override
    public void close() {
        try {
            lock.release();
        } catch (IOException ignored) {
            // releasing a lock we hold cannot meaningfully fail for the caller
        }
        closeQuietly(channel);
    }

    private static void closeQuietly(FileChannel channel) {
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
