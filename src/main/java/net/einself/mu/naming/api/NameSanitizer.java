package net.einself.mu.naming.api;

import java.nio.charset.StandardCharsets;
import java.util.function.UnaryOperator;

/**
 * Name construction (SPEC.md section 5.2): derives a filesystem name from an attribute value.
 *
 * <p>Also the test for origin values, which must pass this unchanged (SPEC.md section 4.9).
 */
public class NameSanitizer implements UnaryOperator<String> {

    public static final int MAX_BYTES = 200;

    private static final String EMPTY_REPLACEMENT = "_";

    @Override
    public String apply(String value) {
        String nfc = Nfc.normalize(value);
        String replaced = replaceSeparators(nfc);
        String stripped = stripControlCharacters(replaced);
        String trimmed = trimSpacesAndDots(stripped);
        String truncated = truncate(trimmed);
        return truncated.isEmpty() ? EMPTY_REPLACEMENT : truncated;
    }

    /**
     * Whether the value survives sanitization untouched, which is what makes it safe to write
     * to the filesystem verbatim (SPEC.md sections 4.1, 4.9).
     */
    public boolean isUnchanged(String value) {
        return apply(value).equals(value);
    }

    private static String replaceSeparators(String value) {
        return value.replace('/', '_');
    }

    private static String stripControlCharacters(String value) {
        StringBuilder result = new StringBuilder(value.length());
        value.codePoints()
                .filter(codePoint -> codePoint > 0x1f)
                .forEach(result::appendCodePoint);
        return result.toString();
    }

    private static String trimSpacesAndDots(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && isSpaceOrDot(value.charAt(start))) {
            start++;
        }
        while (end > start && isSpaceOrDot(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }

    private static boolean isSpaceOrDot(char c) {
        return c == ' ' || c == '.';
    }

    /**
     * Truncates to {@link #MAX_BYTES} UTF-8 bytes, never splitting a codepoint.
     */
    private static String truncate(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= MAX_BYTES) {
            return value;
        }

        int end = 0;
        int consumed = 0;
        while (end < value.length()) {
            int codePoint = value.codePointAt(end);
            int width = utf8Width(codePoint);
            if (consumed + width > MAX_BYTES) {
                break;
            }
            consumed += width;
            end += Character.charCount(codePoint);
        }
        return value.substring(0, end);
    }

    private static int utf8Width(int codePoint) {
        if (codePoint < 0x80) {
            return 1;
        }
        if (codePoint < 0x800) {
            return 2;
        }
        if (codePoint < 0x10000) {
            return 3;
        }
        return 4;
    }

}
