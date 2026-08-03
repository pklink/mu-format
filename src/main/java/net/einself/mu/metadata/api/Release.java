package net.einself.mu.metadata.api;

import java.util.List;

/**
 * A release entity, as {@code import} produces it (SPEC.md section 4.8).
 *
 * @param id                   the filename stem of {@code meta/releases/<id>.mu}
 * @param title                required (SPEC.md section 4.8)
 * @param credits              at least one with {@code role = "main"} (SPEC.md section 4.6)
 * @param tracks               sorted by (disc, number)
 * @param assets              in file order, which is authoritative (SPEC.md section 4.8)
 * @param coverFront           blob reference, or null
 * @param coverFrontOriginPath origin path of the cover, or null (SPEC.md section 4.9)
 * @param originDir            the directory the release arrived as, or null
 */
public record Release(
        String id,
        String title,
        List<Credit> credits,
        List<Track> tracks,
        List<Asset> assets,
        String coverFront,
        String coverFrontOriginPath,
        String originDir) {

    /**
     * @param role   required
     * @param artist reference to {@code meta/artists/<id>.mu}; null yields an incomplete
     *               release, which is what {@code import} writes without {@code --artist}
     */
    public record Credit(String role, String artist) {
    }

    /**
     * @param disc       null, an {@link Integer}, or a {@link String} side (SPEC.md section 4.7)
     * @param number     at least 1
     * @param blob       reference to the audio file
     * @param title      as printed
     * @param originPath original path, or null
     */
    public record Track(Object disc, int number, String blob, String title, String originPath) {
    }

    /**
     * @param kind       asset category (SPEC.md section 4.8)
     * @param blob       reference to the file in the store
     * @param originPath original path, or null
     */
    public record Asset(String kind, String blob, String originPath) {
    }

}
