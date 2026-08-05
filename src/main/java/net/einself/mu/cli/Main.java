package net.einself.mu.cli;

import io.github.wasabithumb.jtoml.JToml;
import io.github.wasabithumb.jtoml.option.JTomlOption;
import io.github.wasabithumb.jtoml.option.JTomlOptions;
import io.github.wasabithumb.jtoml.option.prop.LineSeparator;
import io.github.wasabithumb.jtoml.option.prop.OrderMarkPolicy;
import net.einself.mu.shared.ExitCode;
import net.einself.mu.shared.MuException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ScopeType;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "mu", mixinStandardHelpOptions = true, version = "mu 1.0", synopsisSubcommandLabel = "COMMAND")
public class Main implements Callable<Integer> {

    /**
     * Entity files are UTF-8 without BOM with LF line endings (SPEC.md section 4).
     */
    private static final JToml TOML = JToml.jToml(JTomlOptions.builder()
                                    .set(JTomlOption.LINE_SEPARATOR, LineSeparator.LF)
                                    .set(JTomlOption.WRITE_BOM, OrderMarkPolicy.NEVER)
                                    .build());

    @Option(names = "--root", scope = ScopeType.INHERIT, paramLabel = "<path>", description = "Collection root. Default: search upwards for a directory "
                                    + "containing meta/.mu.")
    public Path root;

    @Option(names = "--format", scope = ScopeType.INHERIT, paramLabel = "<format>", description = "Output format: text, json (default: text).")
    public String format = "text";

    private PrintStream out = System.out;

    private PrintStream err = System.err;

    private CommandLine commandLine;

    static void main(String[] args) {
        System.exit(execute(args, System.out, System.err));
    }

    public static int execute(String[] args, PrintStream out, PrintStream err) {
        Main main = new Main();
        main.out = out;
        main.err = err;
        CommandLine commandLine = new CommandLine(main);
        main.commandLine = commandLine;

        OutputFormatter outputFormatter = new OutputFormatter(out);

        return commandLine
                                        .addSubcommand(new ImportCommand(outputFormatter))
                                        .addSubcommand(new SearchCommand(outputFormatter))
                                        .setOut(new PrintWriter(out, true))
                                        .setErr(new PrintWriter(err, true))
                                        .setExecutionExceptionHandler(new ExceptionHandler(err))
                                        .execute(args);
    }

    /**
     * Reached only when no subcommand was given, which is a usage error.
     */
    @Override
    public Integer call() {
        commandLine.usage(err);
        return ExitCode.USAGE.value();
    }

    public PrintStream out() {
        return out;
    }

    public PrintStream err() {
        return err;
    }

    public JToml toml() {
        return TOML;
    }

    /**
     * Turns a {@link MuException} into its exit code instead of a stack trace.
     * Anything else is an unexpected failure and is reported as an I/O error.
     */
    private record ExceptionHandler(PrintStream err) implements CommandLine.IExecutionExceptionHandler {

        @Override
        public int handleExecutionException(Exception exception,
                                        CommandLine commandLine,
                                        CommandLine.ParseResult parseResult) {
            if (exception instanceof MuException muException) {
                err.println("mu: " + muException.getMessage());
                muException.details().forEach(detail -> err.println("  " + detail));
                return muException.exitCode().value();
            }
            err.println("mu: " + exception);
            return ExitCode.IO_ERROR.value();
        }

    }

}
