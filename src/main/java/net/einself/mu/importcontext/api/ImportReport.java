package net.einself.mu.importcontext.api;

import net.einself.mu.metadata.api.Release;

public record ImportReport(ImportResult result, Release release) {
}
