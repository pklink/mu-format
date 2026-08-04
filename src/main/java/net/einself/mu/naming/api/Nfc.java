package net.einself.mu.naming.api;

import java.text.Normalizer;

/**
 * Unicode normalization (SPEC.md section 4.3): string values are normalized to
 * NFC on write.
 */
public final class Nfc {

    private Nfc() {
    }

    public static String normalize(String value) {
        return Normalizer.isNormalized(value, Normalizer.Form.NFC)
                                        ? value
                                        : Normalizer.normalize(value, Normalizer.Form.NFC);
    }

}
