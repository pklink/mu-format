package net.einself.mu.importcontext.api;

import java.util.ArrayList;
import java.util.List;

/**
 * What an import did, and what the user should know about it.
 */
public class ImportResult {

    private final List<String> warnings = new ArrayList<>();

    private int stored;

    private int deduplicated;

    public void countStored() {
        stored++;
    }

    public void countDeduplicated() {
        deduplicated++;
    }

    public void warn(String warning) {
        warnings.add(warning);
    }

    public int files() {
        return stored + deduplicated;
    }

    public int stored() {
        return stored;
    }

    public int deduplicated() {
        return deduplicated;
    }

    public List<String> warnings() {
        return List.copyOf(warnings);
    }

}
