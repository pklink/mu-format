package net.einself.mu;

import java.util.Arrays;
import java.util.Optional;

public record MuInfo(String key, String value) {
    public enum Attribute {
        TYPE("type", "Type"),
        SAMPLE_RATE("sample-rate", "Sample Rate"),
        BIT_DEPTH("bit-depth", "Bit Depth"),
        DISCOGS_RELEASE_ID("discogs-release-id", "Discogs Release ID"),
        RELEASE_YEAR("release-year", "Release Year"),
        RELEASE_YEAR_MEDIUM("release-year-medium", "Release Year of Medium"),
        RIP_RESULT("rip-result", "Rip Result"),
        SOURCE_MEDIUM("source-medium", "Source Medium");

        private final String key;
        private final String label;

        Attribute(String key, String label) {
            this.key = key;
            this.label = label;
        }

        public String getKey() {
            return key;
        }

        public String getLabel() {
            return label;
        }

        public static Optional<Attribute> fromKey(String key) {
            return Arrays.stream(Attribute.values())
                    .filter(value -> value.key.equals(key))
                    .findFirst();
        }
    }
}
