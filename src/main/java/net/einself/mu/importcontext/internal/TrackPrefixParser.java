package net.einself.mu.importcontext.internal;

import net.einself.mu.naming.api.Nfc;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a leading track prefix such as {@code 01 }, {@code A1 } or
 * {@code 1-05 } from a filename. A letter prefix gives a string {@code disc}
 * (SPEC.md section 4.7).
 *
 * <p>
 * The number is capped at three digits so that a filename like
 * {@code 1984 Song.flac} is not read as track 1984.
 */
public class TrackPrefixParser {

    private static final String SEPARATOR = "[ _.\\-]+";

    /** {@code 1-05 Title}, {@code 2.01 Title} — numbered disc and track. */
    private static final Pattern DISC_AND_NUMBER = Pattern.compile("^(\\d{1,2})[-.](\\d{1,3})" + SEPARATOR + "(.*)$");

    /** {@code A1 Title}, {@code A-01 Title} — medium side and track. */
    private static final Pattern SIDE_AND_NUMBER = Pattern.compile("^([A-Za-z])-?(\\d{1,3})" + SEPARATOR + "(.*)$");

    /** {@code 01 Title} — track only. */
    private static final Pattern NUMBER_ONLY = Pattern.compile("^(\\d{1,3})" + SEPARATOR + "(.*)$");

    public Optional<TrackPosition> parse(String stem) {
        Matcher discAndNumber = DISC_AND_NUMBER.matcher(stem);
        if (discAndNumber.matches()) {
            return position(
                                            Integer.valueOf(discAndNumber.group(1)),
                                            discAndNumber.group(2),
                                            discAndNumber.group(3),
                                            stem);
        }

        Matcher sideAndNumber = SIDE_AND_NUMBER.matcher(stem);
        if (sideAndNumber.matches()) {
            return position(
                                            sideAndNumber.group(1).toUpperCase(Locale.ROOT),
                                            sideAndNumber.group(2),
                                            sideAndNumber.group(3),
                                            stem);
        }

        Matcher numberOnly = NUMBER_ONLY.matcher(stem);
        if (numberOnly.matches()) {
            return position(null, numberOnly.group(1), numberOnly.group(2), stem);
        }

        return Optional.empty();
    }

    private static Optional<TrackPosition> position(Object disc, String number, String title, String stem) {
        int parsed = Integer.parseInt(number);
        if (parsed < 1) {
            // SPEC.md section 4.7 requires number >= 1; "00 Intro" carries no usable
            // position.
            return Optional.empty();
        }
        if (disc instanceof Integer discNumber && discNumber < 1) {
            return Optional.empty();
        }
        return Optional.of(new TrackPosition(disc, parsed, title(title, stem)));
    }

    private static String title(String remainder, String stem) {
        String trimmed = remainder.trim();
        return Nfc.normalize(trimmed.isEmpty() ? stem : trimmed);
    }

}
