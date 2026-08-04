package net.einself.mu.importcontext.api;

import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record ImportOptions(
                                boolean dryRun,
                                boolean origin,
                                String artistId,
                                String releaseId) {
    public static ImportOptions defaults() {
        return new ImportOptions(false, false, null, null);
    }

    public ImportOptions withDryRun(boolean dryRun) {
        return new ImportOptions(dryRun, origin, artistId, releaseId);
    }

    public ImportOptions withOrigin(boolean origin) {
        return new ImportOptions(dryRun, origin, artistId, releaseId);
    }

    public ImportOptions withArtistId(String artistId) {
        return new ImportOptions(dryRun, origin, artistId, releaseId);
    }

    public ImportOptions withReleaseId(String releaseId) {
        return new ImportOptions(dryRun, origin, artistId, releaseId);
    }
}
