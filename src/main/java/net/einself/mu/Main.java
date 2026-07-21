package net.einself.mu;

import net.einself.mu.dto.Attribute;
import net.einself.mu.dto.MuFolder;
import net.einself.mu.dto.Release;
import net.einself.mu.reader.AttributeKeyExtractor;
import net.einself.mu.reader.AttributeReader;
import net.einself.mu.reader.MuFolderFinder;
import net.einself.mu.reader.MuReleaseParser;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "mu", mixinStandardHelpOptions = true, version = "mu 1.0")
public class Main implements Callable<Integer> {

    @Parameters(index = "0", defaultValue = ".", description = "Root directory to scan")
    Path root;

    public static void main(String[] args) {
        System.exit(new CommandLine(new Main()).execute(args));
    }

    @Override
    public Integer call() {
        if (!Files.isDirectory(root)) {
            System.err.println("Not a directory: " + root);
            return 1;
        }
        run(root, System.out);
        return 0;
    }

    static void run(Path root, PrintStream out) {
        MuReleaseParser parser = new MuReleaseParser(new AttributeReader(new AttributeKeyExtractor()));
        List<MuFolder> folders = new MuFolderFinder().findAll(root);

        for (MuFolder folder : folders) {
            Release release = parser.parse(folder);
            out.println(release.path());
            out.println("  artist: " + String.join(", ", release.artists()));
            out.println("  title: " + release.title());
            for (Attribute attribute : release.attributes().getAll()) {
                out.println("  " + attribute.key().name() + ": " + attribute.value());
            }
        }
    }
}
