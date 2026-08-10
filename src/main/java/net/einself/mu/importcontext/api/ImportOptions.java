package net.einself.mu.importcontext.api;

import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

@ValueObject
public record ImportOptions(
                                boolean dryRun,
                                boolean origin,
                                @Nullable String artistId,
                                @Nullable String releaseId) {
    public static ImportOptions defaults() {
        return new ImportOptions(false, false, null, null);
    }

    public ImportOptions withDryRun(boolean dryRun) {
        return new ImportOptions(dryRun, origin, artistId, releaseId);
    }

    public ImportOptions withOrigin(boolean origin) {
        return new ImportOptions(dryRun, origin, artistId, releaseId);
    }

    public ImportOptions withArtistId(@Nullable String artistId) {
        return new ImportOptions(dryRun, origin, artistId, releaseId);
    }

    public ImportOptions withReleaseId(@Nullable String releaseId) {
        return new ImportOptions(dryRun, origin, artistId, releaseId);
    }
}
