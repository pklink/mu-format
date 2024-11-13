package net.einself.reader;

import java.io.File;
import java.nio.file.Path;
import java.util.function.Predicate;

public class HasMuFolder implements Predicate<Path> {

    @Override
    public boolean test(Path path) {
        File muMetaFolder = Path.of(path.toString(), "=mu").toFile();
        return muMetaFolder.exists() && muMetaFolder.isDirectory();
    }

}
