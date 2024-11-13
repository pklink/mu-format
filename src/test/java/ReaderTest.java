import net.einself.reader.AttributeKeyExtractor;
import net.einself.reader.AttributeReader;
import net.einself.reader.MuFolderFinder;
import net.einself.reader.MuReleaseParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

public class ReaderTest {

    @Test
    void name2() {
        String root = "/Users/pierre/Downloads/=upp";

        MuFolderFinder folderFinder = new MuFolderFinder();

        AttributeKeyExtractor attributeKeyExtractor = new AttributeKeyExtractor();
        AttributeReader attributeReader = new AttributeReader(attributeKeyExtractor);
        MuReleaseParser folderParser = new MuReleaseParser(attributeReader);

        folderFinder.findAll(Path.of(root)).stream()
                .map(folderParser::parse)
                .forEach(System.out::println);
    }
}
