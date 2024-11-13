package net.einself.reader;

import net.einself.dto.Attribute;
import net.einself.dto.Attributes;
import net.einself.dto.MuFolder;
import net.einself.dto.Release;

import java.nio.file.Path;
import java.util.List;

public class MuReleaseParser implements MuParser<Release> {

    private final AttributeReader attributeReader;

    public MuReleaseParser(AttributeReader attributeReader) {
        this.attributeReader = attributeReader;
    }

    @Override
    public Release parse(MuFolder folder) {
        Attributes attributes = attributeReader.read(folder.mu());

        return new Release(
                folder.root(),
                getArtists(folder.root(), attributes),
                getTitle(folder.root(), attributes),
                attributes
        );
    }

    private static List<String> getArtists(Path root, Attributes attributes) {
        List<String> artists = attributes.getArtists().stream()
                .map(Attribute::value)
                .toList();

        if (artists.isEmpty()) {
            return List.of("TODO-ARTIST");
        }

        return artists;
    }

    private static String getTitle(Path root, Attributes attributes) {
        return attributes.getTitle()
                .map(Attribute::value)
                .orElseGet(() -> root.toFile().getName());
    }

}
