package net.einself.mu.reader;

import net.einself.mu.dto.Attribute;
import net.einself.mu.dto.Attributes;
import net.einself.mu.dto.Key;
import org.apache.commons.io.file.PathUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

public class AttributeReader {

    private final HasMuExtension hasMuExtension = new HasMuExtension();

    private final AttributeKeyExtractor attributeKeyExtractor;

    public AttributeReader(AttributeKeyExtractor attributeKeyExtractor) {
        this.attributeKeyExtractor = attributeKeyExtractor;
    }

    public Attributes read(Path muPath) {
        try (Stream<Path> files = Files.walk(muPath)) {
            return collectAttributes(files);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Attributes collectAttributes(Stream<Path> files) {
        return files.filter(PathUtils::isRegularFile)
                .filter(hasMuExtension)
                .map(this::createAttribute)
                .sorted(byKeyOrder())
                .collect(collectingAndThen(toList(), Attributes::new));
    }

    private Attribute createAttribute(Path path) {
        String baseName = PathUtils.getBaseName(path);
        Key key = attributeKeyExtractor.extract(baseName);
        String value = baseName.substring(baseName.indexOf("=") + 1);
        return new Attribute(key, value);
    }

    private static Comparator<Attribute> byKeyOrder() {
        return Comparator.comparing(attribute -> attribute.key().order());
    }

}
