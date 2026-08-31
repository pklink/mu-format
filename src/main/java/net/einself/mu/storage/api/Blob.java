package net.einself.mu.storage.api;

/**
 * A file taken into the store.
 *
 * @param hash
 *            lowercase hex SHA-256 of the content, 64 characters (SPEC.md
 *            section 3.1)
 * @param deduplicated
 *            whether the blob was already present (SPEC.md section 3.3)
 */
public record Blob(String hash, boolean deduplicated) {
}
