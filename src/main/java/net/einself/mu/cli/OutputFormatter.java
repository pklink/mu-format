package net.einself.mu.cli;

import com.google.gson.Gson;
import net.einself.mu.shared.Envelope;
import net.einself.mu.shared.ExitCode;
import net.einself.mu.shared.MuException;

import java.io.PrintStream;
import java.util.function.Consumer;

public final class OutputFormatter {

    private static final Gson GSON = new Gson();

    private OutputFormatter() {}

    public static <T> void write(PrintStream out, String format, String command,
                                  T data, Consumer<PrintStream> textWriter) {
        if (format == null) {
            throw new MuException(ExitCode.USAGE,
                    "Invalid --format: null (must be text or json)");
        }
        if ("json".equalsIgnoreCase(format)) {
            GSON.toJson(new Envelope<>(command, data), out);
            out.println();
        } else if ("text".equalsIgnoreCase(format)) {
            textWriter.accept(out);
        } else {
            throw new MuException(ExitCode.USAGE,
                    "Invalid --format: " + format + " (must be text or json)");
        }
    }

}
