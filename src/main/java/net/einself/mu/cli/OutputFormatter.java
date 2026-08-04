package net.einself.mu.cli;

import com.google.gson.Gson;
import net.einself.mu.shared.Envelope;

import java.io.PrintStream;
import java.util.function.Consumer;

public final class OutputFormatter {

    private static final Gson GSON = new Gson();

    private OutputFormatter() {}

    public static <T> void write(PrintStream out, String format, String command,
                                  T data, Consumer<PrintStream> textWriter) {
        if ("json".equals(format)) {
            GSON.toJson(new Envelope<>(command, data), out);
            out.println();
        } else {
            textWriter.accept(out);
        }
    }

}
