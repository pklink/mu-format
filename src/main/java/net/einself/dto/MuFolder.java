package net.einself.dto;

import java.nio.file.Path;

public record MuFolder(Path root, Path mu) {

    public MuFolder(Path root) {
        this(root, Path.of(root.toString(), "=mu"));
    }

}
