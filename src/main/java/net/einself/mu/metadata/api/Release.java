package net.einself.mu.metadata.api;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A release entity, as {@code import} produces it (SPEC.md section 4.7).
 *
 * @param id
 *            the filename stem of {@code meta/releases/<id>.mu}
 * @param title
 *            required (SPEC.md section 4.7)
 * @param credits
 *            at least one with {@code role = "main"} (SPEC.md section 4.5)
 * @param tracks
 *            sorted by (disc, number)
 * @param assets
 *            in file order, which is authoritative (SPEC.md section 4.7)
 * @param originDir
 *            the directory the release arrived as, or null
 */
public record Release(
                                String id,
                                String title,
                                List<Credit> credits,
                                List<Track> tracks,
                                List<Asset> assets,
                                @Nullable String originDir) {

    /**
     * @param role
     *            required
     * @param artist
     *            reference to {@code meta/artists/<id>.mu}; null yields an
     *            incomplete release, which is what {@code import} writes without
     *            {@code --artist}
     */
    public record Credit(String role, @Nullable String artist) {
    }

    /**
     * @param disc
     *            null, an {@link Integer}, or a {@link String} side (SPEC.md
     *            section 4.6)
     * @param number
     *            at least 1
     * @param blob
     *            reference to the audio file
     * @param title
     *            as printed
     * @param originPath
     *            original path, or null
     */
    public record Track(@Nullable Object disc, int number, String blob, String title, @Nullable String originPath) {
    }

    /**
     * @param kind
     *            asset category (SPEC.md section 4.7)
     * @param blob
     *            reference to the file in the store
     * @param originPath
     *            original path, or null
     */
    public record Asset(String kind, String blob, @Nullable String originPath) {
    }

}
