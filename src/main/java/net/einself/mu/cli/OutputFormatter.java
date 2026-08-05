package net.einself.mu.cli;

import com.google.gson.Gson;
import net.einself.mu.shared.Envelope;
import net.einself.mu.shared.ExitCode;
import net.einself.mu.shared.MuException;

import java.io.PrintStream;
import java.util.function.Consumer;

public final class OutputFormatter {

    private static final String FORMAT_JSON = "json";
    private static final String FORMAT_TEXT = "text";

    private static final Gson GSON = new Gson();

    private final PrintStream out;

    public OutputFormatter(PrintStream out) {
        this.out = out;
    }

    public static void validate(String format) {
        if (!(FORMAT_JSON.equalsIgnoreCase(format) || FORMAT_TEXT.equalsIgnoreCase(format))) {
            String message = "Invalid --format: %s (must be text or json)".formatted(format);
            throw new MuException(ExitCode.USAGE, message);
        }
    }

    public <T> void write(String format, String command, T data, Consumer<PrintStream> textWriter) {
        if (FORMAT_JSON.equalsIgnoreCase(format)) {
            GSON.toJson(new Envelope<>(command, data), out);
            out.println();
        } else {
            textWriter.accept(out);
        }
    }

}
