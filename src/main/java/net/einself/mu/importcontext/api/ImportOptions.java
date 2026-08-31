package net.einself.mu.importcontext.api;

import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

@ValueObject
public record ImportOptions(
                                boolean dryRun,
                                boolean origin,
                                @Nullable String artistId,
                                @Nullable String releaseId) {
}
