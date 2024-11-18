package net.einself.mu.reader;

import org.apache.commons.io.file.PathUtils;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

public class HasMuExtension implements Predicate<Path> {

    @Override
    public boolean test(Path path) {
        return Optional.ofNullable(PathUtils.getExtension(path))
                .map(extension -> extension.toLowerCase(Locale.ROOT))
                .filter("mu"::equals)
                .isPresent();
    }

}
