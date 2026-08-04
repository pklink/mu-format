package net.einself.mu.cli;

import net.einself.mu.shared.ExitCode;
import net.einself.mu.shared.MuException;
import net.einself.mu.collection.api.CollectionModule;
import net.einself.mu.collection.api.CollectionRoot;
import net.einself.mu.collection.api.CollectionService;
import net.einself.mu.importcontext.api.ImportData;
import net.einself.mu.importcontext.api.ImportModule;
import net.einself.mu.importcontext.api.ImportOptions;
import net.einself.mu.importcontext.api.ImportReport;
import net.einself.mu.importcontext.api.ImportService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Parameters;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * {@code mu import} — takes files into the store and creates a release skeleton.
 */
@Command(name = "import",
        description = "Take files into the store and create a meta skeleton.")
public class ImportCommand implements Callable<Integer> {

    @ParentCommand
    private Main parent;

    @Option(names = "--release", paramLabel = "<id>",
            description = "Import into an existing release. Not implemented yet.")
    String releaseId;

    @Option(names = "--artist", paramLabel = "<id>",
            description = "Artist identifier for the main credit.")
    String artistId;

    @Option(names = "--origin",
            description = "Record origin-dir and origin-path (SPEC.md section 4.9).")
    boolean origin;

    @Option(names = "--dry-run",
            description = "Report what would happen, write nothing.")
    boolean dryRun;

    @Parameters(arity = "1..*", paramLabel = "<path>",
            description = "Files or directories to import.")
    List<Path> paths;

    @Override
    public Integer call() {
        return run(parent.out(), parent.err());
    }

    int run(PrintStream out, PrintStream err) {
        rejectUnimplementedOptions();

        CollectionService collectionService = CollectionModule.createService(parent.toml());
        CollectionRoot root = collectionService.findRoot(parent.root, Path.of("").toAbsolutePath());
        collectionService.readFormatVersion(root);

        ImportOptions options = new ImportOptions(dryRun, origin, artistId, releaseId);
        ImportService importService = ImportModule.createImportService(parent.toml(), err);
        ImportReport report = importService.importPaths(root, paths, options);

        String relPath = root.path().relativize(
                root.releases().resolve(report.release().id() + ".mu")).toString();
        ImportData data = new ImportData(relPath, dryRun, report.result().files(),
                report.result().stored(), report.result().deduplicated(),
                report.result().warnings());

        OutputFormatter.write(out, parent.format, "import", data,
                printer -> {
                    report.result().warnings().forEach(w -> err.println("warning: " + w));
                    if (dryRun) {
                        printer.println("--- meta/releases/" + report.release().id() + ".mu (dry run) ---");
                        printer.print(importService.renderRelease(report.release()));
                        printer.println("--- end ---");
                    }
                    printer.println(summary(root, report.release()));
                    printer.println(report.result().files() + " file(s): "
                            + report.result().stored() + " stored, "
                            + report.result().deduplicated() + " deduplicated");
                });
        return ExitCode.SUCCESS.value();
    }

    private String summary(CollectionRoot root, net.einself.mu.metadata.api.Release release) {
        String path = root.path().relativize(root.releases().resolve(release.id() + ".mu")).toString();
        return (dryRun ? "would create " : "created ") + path;
    }

    private void rejectUnimplementedOptions() {
        if (releaseId != null) {
            throw new MuException(ExitCode.USAGE,
                    "--release is not implemented yet; import creates a new release");
        }
    }
}
