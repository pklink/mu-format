package net.einself.mu.storage.internal;

import net.einself.mu.collection.api.CollectionRoot;
import net.einself.mu.shared.ExitCode;
import net.einself.mu.shared.MuException;
import net.einself.mu.storage.api.Blob;
import net.einself.mu.storage.api.BlobRepository;
import org.apache.commons.io.file.PathUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Takes files into the store.
 *
 * <p>
 * SPEC.md section 3.4 fixes only the guarantee — a blob becomes visible at its
 * final path as a whole — not the mechanism. This implementation stages inside
 * {@code store/.tmp/} so that the publishing rename stays on one filesystem,
 * and clears that directory before it starts: nothing there is reachable by the
 * path formula, so an interrupted import cannot leave a resolvable but
 * incomplete blob.
 */
public class FileSystemBlobStore implements BlobRepository {

    private static final Set<PosixFilePermission> READ_ONLY = EnumSet.of(
                                    PosixFilePermission.OWNER_READ,
                                    PosixFilePermission.GROUP_READ,
                                    PosixFilePermission.OTHERS_READ);

    private static final int BUFFER_SIZE = 8192;

    private final CollectionRoot root;

    public FileSystemBlobStore(CollectionRoot root) {
        this.root = root;
    }

    /**
     * Removes whatever an interrupted run left behind. Must run before the first
     * {@link #take}.
     */
    public void clearStaging() {
        Path staging = root.staging();
        if (!Files.isDirectory(staging)) {
            return;
        }
        try {
            PathUtils.cleanDirectory(staging);
        } catch (IOException e) {
            throw new MuException(ExitCode.IO_ERROR,
                                            "Cannot clear staging directory " + staging + ": " + e.getMessage(), e);
        }
    }

    /**
     * Copies {@code source} into the store, hashing it in the same pass.
     */
    public Blob store(Path source) {
        Path staging = root.staging();
        Path temp;
        String hash;
        try {
            Files.createDirectories(staging);
            temp = staging.resolve(UUID.randomUUID().toString());
            hash = copyAndHash(source, temp);
        } catch (IOException e) {
            throw new MuException(ExitCode.IO_ERROR,
                                            "Cannot stage " + source + ": " + e.getMessage(), e);
        }

        try {
            return publish(temp, hash);
        } catch (IOException e) {
            deleteQuietly(temp);
            throw new MuException(ExitCode.IO_ERROR,
                                            "Cannot store " + source + ": " + e.getMessage(), e);
        }
    }

    /**
     * Hashes {@code source} without writing anything, for {@code --dry-run}.
     */
    public Blob inspect(Path source) {
        try {
            String hash = copyAndHash(source, null);
            return new Blob(hash, Files.exists(target(hash)));
        } catch (IOException e) {
            throw new MuException(ExitCode.IO_ERROR,
                                            "Cannot read " + source + ": " + e.getMessage(), e);
        }
    }

    private Blob publish(Path temp, String hash) throws IOException {
        Path target = target(hash);
        if (Files.exists(target)) {
            // Identical by definition (SPEC.md section 3.3): nothing is overwritten.
            Files.delete(temp);
            return new Blob(hash, true);
        }

        Files.createDirectories(target.getParent());
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (FileAlreadyExistsException e) {
            // Lost a race against another writer; the content is identical either way.
            Files.delete(temp);
            return new Blob(hash, true);
        }
        makeReadOnly(target);
        return new Blob(hash, false);
    }

    private Path target(String hash) {
        return root.store().resolve(hash.substring(0, 2)).resolve(hash);
    }

    /**
     * Streams {@code source} through a digest, writing to {@code target} when it is
     * not null.
     */
    private String copyAndHash(Path source, Path target) throws IOException {
        MessageDigest digest = sha256();
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream in = Files.newInputStream(source);
                                        OutputStream out = target == null ? OutputStream.nullOutputStream() : Files.newOutputStream(target)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
                out.write(buffer, 0, read);
            }
        }
        return toHex(digest.digest());
    }

    /**
     * Best-effort: where POSIX permissions are missing (exFAT, SMB) the call fails
     * and is ignored.
     */
    private static void makeReadOnly(Path target) {
        try {
            Files.setPosixFilePermissions(target, READ_ONLY);
        } catch (IOException | UnsupportedOperationException ignored) {
            // not every filesystem carries POSIX permissions
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(Character.forDigit((b >> 4) & 0xf, 16));
            hex.append(Character.forDigit(b & 0xf, 16));
        }
        return hex.toString();
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // the next run clears the staging directory anyway
        }
    }

}
