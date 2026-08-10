package net.einself.mu.importcontext.internal;

import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * A track position parsed from a filename prefix (SPEC.md section 4.7).
 *
 * @param disc
 *            {@code null} when the filename carried no disc, an {@link Integer}
 *            for numbered discs, a {@link String} for medium sides
 * @param number
 *            track number, always at least 1
 * @param title
 *            the remainder of the filename
 */
public record TrackPosition(@Nullable Object disc, int number, String title) {

    public Optional<Object> discValue() {
        return Optional.ofNullable(disc);
    }

}
