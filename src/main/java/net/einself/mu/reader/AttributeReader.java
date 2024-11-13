package net.einself.mu.reader;

import net.einself.mu.dto.Attribute;
import net.einself.mu.dto.Attributes;
import net.einself.mu.dto.Key;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class AttributeReader {

    private final static String MU_EXTENSION = ".mu";

    private final AttributeKeyExtractor attributeKeyExtractor;

    public AttributeReader(AttributeKeyExtractor attributeKeyExtractor) {
        this.attributeKeyExtractor = attributeKeyExtractor;
    }

    public Attributes read(Path muPath) {
        try (Stream<Path> files = Files.walk(muPath)) {
            List<Attribute> attributeList = files
                    .filter(path -> path.toFile().isFile())
                    .filter(AttributeReader::hasMuExtension)
                    .map(AttributeReader::getBaseName)
                    .map(this::createAttribute)
                    .sorted(Comparator.comparing(attribute -> attribute.key().order()))
                    .toList();
            return new Attributes(attributeList);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Attribute createAttribute(String attributePair) {
        Key key = attributeKeyExtractor.extract(attributePair);
        String value = attributePair.substring(attributePair.indexOf("=") + 1);
        return new Attribute(key, value);
    }

    private static String getBaseName(Path path) {
        return Optional.ofNullable(path)
                .map(Path::getFileName)
                .map(Path::toString)
                .map(fileName -> fileName.substring(0, fileName.length() - MU_EXTENSION.length()))
                .orElse(null);
    }

    private static boolean hasMuExtension(Path path) {
        return Optional.ofNullable(path)
                .map(Path::toString)
                .map(pathAsString -> pathAsString.endsWith(".mu"))
                .orElse(false);
    }

}
