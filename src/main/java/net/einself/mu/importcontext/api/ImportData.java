package net.einself.mu.importcontext.api;

import java.util.List;

public record ImportData(
        String path,
        boolean dryRun,
        int files,
        int stored,
        int deduplicated,
        List<String> warnings) {}
