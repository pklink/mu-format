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

    private OutputFormatter() {}

    public static void validate(String format) {
        if (format == null
                || !(FORMAT_JSON.equalsIgnoreCase(format) || FORMAT_TEXT.equalsIgnoreCase(format))) {
            throw new MuException(ExitCode.USAGE,
                    "Invalid --format: " + format + " (must be text or json)");
        }
    }

    public static <T> void write(PrintStream out, String format, String command,
                                  T data, Consumer<PrintStream> textWriter) {
        if (FORMAT_JSON.equalsIgnoreCase(format)) {
            GSON.toJson(new Envelope<>(command, data), out);
            out.println();
        } else {
            textWriter.accept(out);
        }
    }

}
