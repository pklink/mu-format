package net.einself.dto;

import java.nio.file.Path;
import java.util.List;

public record Release(
        Path path,
        List<String> artists,
        String title,
        Attributes attributes
) { }
