package net.einself.mu;

/**
 * Exit codes of the {@code mu} tool (IMPLEMENTATION.md section 7).
 */
public enum ExitCode {

    SUCCESS(0),
    PROBLEMS(1),
    USAGE(2),
    LOCK_HELD(3),
    IO_ERROR(4);

    private final int value;

    ExitCode(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

}
