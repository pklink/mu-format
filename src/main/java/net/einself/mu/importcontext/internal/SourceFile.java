package net.einself.mu.importcontext.internal;

/**
 * A file selected for import.
 *
 * @param path         absolute path of the source file
 * @param relativePath path relative to the imported directory, {@code /} as separator
 *                     (SPEC.md section 4.9); the file name alone for file arguments
 * @param kind         classification by extension
 */
public record SourceFile(java.nio.file.Path path, String relativePath, FileKind kind) {

    public String filename() {
        return path.getFileName().toString();
    }

}
