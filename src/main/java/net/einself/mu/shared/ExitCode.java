package net.einself.mu.shared;

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
