package net.einself.dto;

import java.util.List;
import java.util.Optional;

public class Attributes {

    private final List<Attribute> attributes;

    public Attributes(List<Attribute> attributes) {
        this.attributes = attributes;
    }

    public Optional<Attribute> getAttribute(String key) {
        return attributes.stream()
                .filter(attribute -> attribute.key().name().equals(key))
                .findFirst();
    }

    public List<Attribute> getAttributes(String key) {
        return attributes.stream()
                .filter(attribute -> attribute.key().name().equals(key))
                .toList();
    }

    public List<Attribute> getArtists() {
        return getAttributes("artist");
    }

    public Optional<Attribute> getReleaseYear() {
        return getAttribute("release-year");
    }

    public Optional<Attribute> getTitle() {
        return getAttribute("title");
    }

}
