package net.einself.mu.importcontext.internal;

import io.github.wasabithumb.jtoml.JToml;
import net.einself.mu.collection.api.CollectionModule;
import net.einself.mu.collection.api.CollectionRoot;
import net.einself.mu.importcontext.api.ImportOptions;
import net.einself.mu.importcontext.api.ImportReport;
import net.einself.mu.importcontext.api.ImportResult;
import net.einself.mu.importcontext.api.ImportService;
import net.einself.mu.metadata.api.MetadataModule;
import net.einself.mu.metadata.api.Release;
import net.einself.mu.metadata.api.ReleaseRepository;
import net.einself.mu.naming.api.ExtensionDeriver;
import net.einself.mu.naming.api.NameSanitizer;
import net.einself.mu.naming.api.Nfc;
import net.einself.mu.shared.ExitCode;
import net.einself.mu.shared.MuException;
import net.einself.mu.storage.api.Blob;
import net.einself.mu.storage.api.BlobRepository;
import net.einself.mu.storage.api.StorageModule;
import org.jspecify.annotations.Nullable;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ImportServiceImpl implements ImportService {

    private final PrintStream err;
    private final ExtensionDeriver extensionDeriver = new ExtensionDeriver();
    private final FileClassifier fileClassifier = new FileClassifier(extensionDeriver);
    private final SourceFileCollector sourceFileCollector = new SourceFileCollector(fileClassifier);
    private final OriginPathValidator originPathValidator = new OriginPathValidator(new NameSanitizer());
    private final ReleaseAssembler releaseAssembler = new ReleaseAssembler(
                                    extensionDeriver, new TrackPrefixParser(), new CoverFrontSelector(),
                                    new AssetKindMapper(extensionDeriver, fileClassifier));
    private final ReleaseRepository releaseRepository;

    public ImportServiceImpl(JToml toml, PrintStream err) {
        this.err = err;
        this.releaseRepository = MetadataModule.createReleaseRepository(toml);
    }

    @Override
    public ImportReport importPaths(CollectionRoot root, List<Path> paths, ImportOptions options) {
        List<SourceFile> files = collect(paths);
        String originDir = options.origin() ? requireSingleDirectoryName(paths, files) : null;

        String releaseTitle = deriveTitle(paths);

        ImportResult report = new ImportResult();
        Release release = options.dryRun()
                                        ? importDryRun(root, files, originDir, report, options, releaseTitle)
                                        : importForReal(root, files, originDir, report, options, releaseTitle);

        return new ImportReport(report, release);
    }

    private String deriveTitle(List<Path> paths) {
        Path first = paths.get(0);
        Path source = paths.size() == 1 && Files.isDirectory(first) ? first : first.toAbsolutePath().getParent();
        if (source == null) {
            throw new MuException(ExitCode.USAGE, "Cannot import files from the filesystem root");
        }
        return directoryName(source);
    }

    @Override
    public String renderRelease(Release release) {
        return releaseRepository.render(release);
    }

    private List<SourceFile> collect(List<Path> paths) {
        List<SourceFile> files = sourceFileCollector.collect(paths);
        if (files.isEmpty()) {
            throw new MuException(ExitCode.USAGE, "No files to import");
        }
        return files;
    }

    private String requireSingleDirectoryName(List<Path> paths, List<SourceFile> files) {
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
                                    @Nullable String originDir,
                                    ImportResult report,
                                    ImportOptions options,
                                    String releaseTitle) {
        try (var ignored = CollectionModule.acquireLock(root)) {
            BlobRepository store = StorageModule.createRepository(root);
            store.clearStaging();

            Map<SourceFile, Blob> blobs = new LinkedHashMap<>();
            for (SourceFile file : files) {
                Blob blob = store.store(file.path());
                blobs.put(file, blob);
                count(report, blob);
            }

            Release release = buildRelease(files, blobs, originDir, report, options, releaseTitle);
            releaseRepository.save(release, root);
            return release;
        }
    }

    private Release importDryRun(CollectionRoot root,
                                    List<SourceFile> files,
                                    @Nullable String originDir,
                                    ImportResult report,
                                    ImportOptions options,
                                    String releaseTitle) {
        BlobRepository store = StorageModule.createRepository(root);
        Map<SourceFile, Blob> blobs = new LinkedHashMap<>();
        for (SourceFile file : files) {
            Blob blob = store.inspect(file.path());
            blobs.put(file, blob);
            count(report, blob);
        }
        return buildRelease(files, blobs, originDir, report, options, releaseTitle);
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
                                    @Nullable String originDir,
                                    ImportResult report,
                                    ImportOptions options,
                                    String releaseTitle) {
        warnAboutCredit(report, options);
        return releaseAssembler.assemble(
                                        UUID.randomUUID().toString(),
                                        releaseTitle,
                                        options.artistId(),
                                        files,
                                        blobs,
                                        originDir);
    }

    private void warnAboutCredit(ImportResult report, ImportOptions options) {
        if (options.artistId() == null) {
            report.warn("no --artist: the main credit has no artist, "
                                            + "the release is incomplete (SPEC.md section 4.6)");
        }
    }

    private static String directoryName(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        Path name = absolute.getFileName();
        return name == null ? absolute.toString() : name.toString();
    }
}
