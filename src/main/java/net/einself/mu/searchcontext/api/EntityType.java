package net.einself.mu.searchcontext.api;

/**
 * The entity kinds a search can produce (SPEC.md section 4.2). Tracks are not
 * entity files but {@code [[track]]} tables inside a release (SPEC.md section
 * 4.6); a track result refers to the release file that contains it.
 */
public enum EntityType {

    RELEASE,
    ARTIST,
    TRACK

}
