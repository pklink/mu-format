package net.einself.mu.reader;

import net.einself.mu.dto.MuFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class MuFolderFinder {

    private final HasMuFolder hasMuFolder = new HasMuFolder();

    public List<MuFolder> findAll(Path root) {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(hasMuFolder).map(MuFolder::new).toList();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
