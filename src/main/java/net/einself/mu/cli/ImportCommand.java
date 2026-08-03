package net.einself.mu.cli;

import net.einself.mu.shared.ExitCode;
import net.einself.mu.cli.Main;
import net.einself.mu.shared.MuException;
import net.einself.mu.collection.internal.CollectionLock;
import net.einself.mu.collection.api.CollectionRoot;
import net.einself.mu.collection.internal.CollectionRootFinder;
import net.einself.mu.collection.internal.FormatVersionReader;
import net.einself.mu.importcontext.api.ImportResult;
import net.einself.mu.naming.internal.ExtensionDeriver;
import net.einself.mu.importcontext.internal.TrackPrefixParser;
import net.einself.mu.importcontext.internal.SourceFileCollector;
import net.einself.mu.importcontext.internal.SourceFile;
import net.einself.mu.importcontext.internal.ReleaseAssembler;
import net.einself.mu.importcontext.internal.OriginPathValidator;
import net.einself.mu.importcontext.internal.FileClassifier;
import net.einself.mu.importcontext.internal.CoverFrontSelector;
import net.einself.mu.importcontext.internal.AssetKindMapper;
import net.einself.mu.naming.api.NameSanitizer;
import net.einself.mu.naming.api.Nfc;
import net.einself.mu.storage.api.Blob;
import net.einself.mu.storage.api.BlobRepository;
import net.einself.mu.storage.api.StorageModule;
import net.einself.mu.storage.internal.FileSystemBlobStore;
import net.einself.mu.metadata.api.Release;
import net.einself.mu.metadata.internal.ReleaseTomlWriter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Parameters;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    private final ExtensionDeriver extensionDeriver = new ExtensionDeriver();

    private final FileClassifier fileClassifier = new FileClassifier(extensionDeriver);

    private final SourceFileCollector sourceFileCollector = new SourceFileCollector(fileClassifier);

    private final OriginPathValidator originPathValidator = new OriginPathValidator(new NameSanitizer());

    private final ReleaseAssembler releaseAssembler = new ReleaseAssembler(
            extensionDeriver,
            new TrackPrefixParser(),
            new CoverFrontSelector(),
            new AssetKindMapper(extensionDeriver, fileClassifier));

    @Override
    public Integer call() {
        return run(parent.out(), parent.err());
    }

    int run(PrintStream out, PrintStream err) {
        rejectUnimplementedOptions();

        CollectionRoot root = resolveRoot();
        List<SourceFile> files = collectFiles();
        String originDir = origin ? requireSingleDirectoryName(files) : null;

        ImportResult report = new ImportResult();
        Release release = dryRun
                ? importDryRun(root, files, originDir, report)
                : importForReal(root, files, originDir, report);

        ReleaseTomlWriter writer = new ReleaseTomlWriter(parent.toml());
        if (dryRun) {
            out.println("--- meta/releases/" + release.id() + ".mu (dry run) ---");
            out.print(writer.render(release));
            out.println("--- end ---");
        }
        report.print(out, err, summary(root, release));
        return ExitCode.SUCCESS.value();
    }

    private String summary(CollectionRoot root, Release release) {
        String path = root.path().relativize(root.releases().resolve(release.id() + ".mu")).toString();
        return (dryRun ? "would create " : "created ") + path;
    }

    /**
     * {@code --release} needs a reader and a merge strategy for an existing entity file, which
     * this tool does not have yet. Failing is better than silently creating a second release.
     */
    private void rejectUnimplementedOptions() {
        if (releaseId != null) {
            throw new MuException(ExitCode.USAGE,
                    "--release is not implemented yet; import creates a new release");
        }
    }

    private CollectionRoot resolveRoot() {
        CollectionRoot root = new CollectionRootFinder()
                .find(parent.root, Path.of("").toAbsolutePath());
        new FormatVersionReader(parent.toml()).read(root);
        return root;
    }

    private List<SourceFile> collectFiles() {
        List<SourceFile> files = sourceFileCollector.collect(paths);
        if (files.isEmpty()) {
            throw new MuException(ExitCode.USAGE, "No files to import");
        }
        return files;
    }

    /**
     * {@code --origin} needs exactly one directory argument: several paths, or a file, leave no
     * name for {@code origin-dir}.
     */
    private String requireSingleDirectoryName(List<SourceFile> files) {
        if (paths.size() != 1 || !Files.isDirectory(paths.get(0))) {
            throw new MuException(ExitCode.USAGE,
                    "--origin requires exactly one directory argument");
        }
        String originDir = Nfc.normalize(directoryName(paths.get(0)));
        originPathValidator.validate(originDir, files);
        return originDir;
    }

    private Release importForReal(CollectionRoot root,
                                  List<SourceFile> files,
                                  String originDir,
                                  ImportResult report) {
        try (CollectionLock ignored = CollectionLock.acquire(root)) {
            BlobRepository store = StorageModule.createRepository(root);
            store.clearStaging();

            Map<SourceFile, Blob> blobs = new LinkedHashMap<>();
            for (SourceFile file : files) {
                Blob blob = store.store(file.path());
                blobs.put(file, blob);
                count(report, blob);
            }

            Release release = buildRelease(files, blobs, originDir, report);
            new ReleaseTomlWriter(parent.toml()).write(root, release);
            return release;
        }
    }

    /**
     * Hashes without copying and takes no lock: a dry run writes nothing, so there is nothing
     * to exclude another process from.
     */
    private Release importDryRun(CollectionRoot root,
                                 List<SourceFile> files,
                                 String originDir,
                                 ImportResult report) {
        BlobRepository store = StorageModule.createRepository(root);
        Map<SourceFile, Blob> blobs = new LinkedHashMap<>();
        for (SourceFile file : files) {
            Blob blob = store.inspect(file.path());
            blobs.put(file, blob);
            count(report, blob);
        }
        return buildRelease(files, blobs, originDir, report);
    }

    private static void count(ImportResult report, Blob blob) {
        if (blob.deduplicated()) {
            report.countDeduplicated();
        } else {
            report.countStored();
        }
    }

    private Release buildRelease(List<SourceFile> files,
                                 Map<SourceFile, Blob> blobs,
                                 String originDir,
                                 ImportResult report) {
        warnAboutCredit(report);
        return releaseAssembler.assemble(
                UUID.randomUUID().toString(),
                title(),
                artistId,
                files,
                blobs,
                originDir);
    }

    /**
     * SPEC.md section 4.6 requires a credit with {@code role = "main"} whose {@code artist}
     * resolves. Without {@code --artist} there is none, so the release is written incomplete
     * and the user is told.
     */
    private void warnAboutCredit(ImportResult report) {
        if (artistId == null) {
            report.warn("no --artist: the main credit has no artist, "
                    + "the release is incomplete (SPEC.md section 4.6)");
        }
    }

    /**
     * The base name of the imported directory: the only information import has, since it reads
     * no tags.
     */
    private String title() {
        Path first = paths.get(0);
        Path source = paths.size() == 1 && Files.isDirectory(first) ? first : first.toAbsolutePath().getParent();
        return directoryName(source);
    }

    private static String directoryName(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        Path name = absolute.getFileName();
        return name == null ? absolute.toString() : name.toString();
    }

}
