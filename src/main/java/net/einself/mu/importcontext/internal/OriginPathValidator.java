package net.einself.mu.importcontext.internal;

import net.einself.mu.naming.api.NameSanitizer;
import net.einself.mu.naming.api.Nfc;
import net.einself.mu.shared.ExitCode;
import net.einself.mu.shared.MuException;

import java.util.*;

/**
 * Checks the values {@code --origin} would record (SPEC.md section 4.9).
 *
 * <p>
 * Every segment must pass name construction unchanged, and origin paths must be
 * unique within a release. Violations are collected and reported together:
 * importing the rest would produce exactly the half-tree the option exists to
 * prevent.
 */
public class OriginPathValidator {

    private final NameSanitizer nameSanitizer;

    public OriginPathValidator(NameSanitizer nameSanitizer) {
        this.nameSanitizer = nameSanitizer;
    }

    public void validate(String originDir, List<SourceFile> files) {
        List<String> problems = new ArrayList<>();
        problems.addAll(directoryProblems(originDir));
        problems.addAll(pathProblems(files));
        problems.addAll(duplicateProblems(files));

        if (!problems.isEmpty()) {
            throw new MuException(ExitCode.PROBLEMS,
                                            "Cannot record origin paths, " + problems.size() + " value(s) are invalid "
                                                                            + "(SPEC.md section 4.9); nothing was written",
                                            problems);
        }
    }

    private List<String> directoryProblems(String originDir) {
        return isValidSegment(originDir)
                                        ? List.of()
                                        : List.of("origin-dir: " + originDir);
    }

    private List<String> pathProblems(List<SourceFile> files) {
        List<String> problems = new ArrayList<>();
        for (SourceFile file : files) {
            for (String segment : file.relativePath().split("/", -1)) {
                if (!isValidSegment(segment)) {
                    problems.add("origin-path: " + file.relativePath() + " (segment: '" + segment + "')");
                    break;
                }
            }
        }
        return problems;
    }

    /**
     * Compared after NFC normalization and case folding (SPEC.md sections 4.9, 4.1
     * rule 5).
     */
    private List<String> duplicateProblems(List<SourceFile> files) {
        Map<String, String> seen = new HashMap<>();
        List<String> problems = new ArrayList<>();
        for (SourceFile file : files) {
            String key = Nfc.normalize(file.relativePath()).toLowerCase(Locale.ROOT);
            String previous = seen.putIfAbsent(key, file.relativePath());
            if (previous != null) {
                problems.add("origin-path: '" + file.relativePath()
                                                + "' collides with '" + previous + "'");
            }
        }
        return problems;
    }

    private boolean isValidSegment(String segment) {
        if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
            return false;
        }
        return nameSanitizer.isUnchanged(segment);
    }

}
