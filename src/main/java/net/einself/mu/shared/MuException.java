package net.einself.mu.shared;

import java.util.List;

/**
 * An aborting condition that carries the exit code it must produce.
 */
public class MuException extends RuntimeException {

    private final ExitCode exitCode;

    private final List<String> details;

    public MuException(ExitCode exitCode, String message) {
        this(exitCode, message, List.of());
    }

    public MuException(ExitCode exitCode, String message, List<String> details) {
        super(message);
        this.exitCode = exitCode;
        this.details = List.copyOf(details);
    }

    public MuException(ExitCode exitCode, String message, Throwable cause) {
        super(message, cause);
        this.exitCode = exitCode;
        this.details = List.of();
    }

    public ExitCode exitCode() {
        return exitCode;
    }

    /**
     * Additional lines printed below the message, one per offending item.
     */
    public List<String> details() {
        return details;
    }

}
