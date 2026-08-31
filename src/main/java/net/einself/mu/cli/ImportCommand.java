package net.einself.mu.cli;

import net.einself.mu.collection.api.CollectionModule;
import net.einself.mu.collection.api.CollectionRoot;
import net.einself.mu.collection.api.CollectionService;
import net.einself.mu.importcontext.api.*;
import net.einself.mu.shared.ExitCode;
import net.einself.mu.shared.MuException;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * {@code mu import} — takes files into the store and creates a release
 * skeleton.
 */
@Command(name = "import", description = "Take files into the store and create a meta skeleton.")
public class ImportCommand implements Callable<Integer> {

    // Populated by Picocli via reflection before call(); never null when used.
    @SuppressWarnings("NullAway.Init")
    @ParentCommand
    private Main parent;

    @Option(names = "--release", paramLabel = "<id>", description = "Import into an existing release. Not implemented yet.")
    @Nullable
    String releaseId;

    @Option(names = "--artist", paramLabel = "<id>", description = "Artist identifier for the main credit.")
    @Nullable
    String artistId;

    @Option(names = "--origin", description = "Record origin-dir and origin-path (SPEC.md section 4.9).")
    boolean origin;

    @Option(names = "--dry-run", description = "Report what would happen, write nothing.")
    boolean dryRun;

    // Populated by Picocli via reflection before call(); never null when used.
    @SuppressWarnings("NullAway.Init")
    @Parameters(arity = "1..*", paramLabel = "<path>", description = "Files or directories to import.")
    List<Path> paths;

    private final OutputFormatter outputFormatter;

    public ImportCommand(OutputFormatter outputFormatter) {
        this.outputFormatter = outputFormatter;
    }

    @Override
    public Integer call() {
        PrintStream err = parent.err();
        OutputFormatter.validate(parent.format);
        rejectUnimplementedOptions();

        CollectionService collectionService = CollectionModule.createService(parent.toml());
        CollectionRoot collectionRoot = collectionService.findRoot(parent.root, Path.of("").toAbsolutePath());
        collectionService.checkFormatVersion(collectionRoot);

        ImportOptions importOptions = new ImportOptions(dryRun, origin, artistId, releaseId);
        ImportService importService = ImportModule.createImportService(parent.toml(), err);
        ImportReport report = importService.importPaths(collectionRoot, paths, importOptions);

        String relPath = releaseMetaPath(collectionRoot, report.release().id());
        ImportData data = new ImportData(relPath, dryRun, report.result().files(),
                                        report.result().stored(), report.result().deduplicated(),
                                        report.result().warnings());

        Consumer<PrintStream> printStreamConsumer = printer -> print(printer, report, err, importService, collectionRoot);
        outputFormatter.write(parent.format, "import", data, printStreamConsumer);
        return ExitCode.SUCCESS.value();
    }

    private void print(PrintStream printer, ImportReport report, PrintStream err, ImportService importService, CollectionRoot collectionRoot) {
        report.result().warnings().forEach(w -> err.println("warning: " + w));

        if (dryRun) {
            printer.printf("--- meta/releases/%s.mu (dry run) ---%n", report.release().id());
            printer.print(importService.renderRelease(report.release()));
            printer.println("--- end ---");
        }

        printer.println(summary(collectionRoot, report.release()));
        printer.println(report.result().files() + " file(s): "
                                        + report.result().stored() + " stored, "
                                        + report.result().deduplicated() + " deduplicated");
    }

    private String summary(CollectionRoot collectionRoot, net.einself.mu.metadata.api.Release release) {
        String path = releaseMetaPath(collectionRoot, release.id());
        return (dryRun ? "would create " : "created ") + path;
    }

    private String releaseMetaPath(CollectionRoot root, String releaseId) {
        Path muPath = root.releases().resolve(releaseId + ".mu");
        return root.path().relativize(muPath).toString();
    }

    private void rejectUnimplementedOptions() {
        if (releaseId != null) {
            throw new MuException(ExitCode.USAGE, "--release is not implemented yet; import creates a new release");
        }
    }
}
