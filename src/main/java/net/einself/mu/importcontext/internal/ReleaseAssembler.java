package net.einself.mu.importcontext.internal;

import net.einself.mu.naming.api.Nfc;
import net.einself.mu.naming.internal.ExtensionDeriver;
import net.einself.mu.storage.api.Blob;
import net.einself.mu.metadata.api.Release;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Assembles the release entity from the imported files (IMPLEMENTATION.md section 2, steps 3-5).
 */
public class ReleaseAssembler {

    private final ExtensionDeriver extensionDeriver;

    private final TrackPrefixParser trackPrefixParser;

    private final CoverFrontSelector coverFrontSelector;

    private final AssetKindMapper assetKindMapper;

    public ReleaseAssembler(ExtensionDeriver extensionDeriver,
                            TrackPrefixParser trackPrefixParser,
                            CoverFrontSelector coverFrontSelector,
                            AssetKindMapper assetKindMapper) {
        this.extensionDeriver = extensionDeriver;
        this.trackPrefixParser = trackPrefixParser;
        this.coverFrontSelector = coverFrontSelector;
        this.assetKindMapper = assetKindMapper;
    }

    public Release assemble(String id,
                            String title,
                            String artistId,
                            List<SourceFile> files,
                            Map<SourceFile, Blob> blobs,
                            String originDir) {
        Optional<SourceFile> cover = coverFrontSelector.select(files);
        List<SourceFile> audio = files.stream()
                .filter(file -> file.kind() == FileKind.AUDIO)
                .toList();

        return new Release(
                id,
                Nfc.normalize(title),
                List.of(new Release.Credit("main", artistId)),
                tracks(audio, blobs, originDir),
                assets(files, cover, blobs, originDir),
                cover.map(file -> reference(file, blobs)).orElse(null),
                cover.map(file -> originPath(file, originDir)).orElse(null),
                originDir);
    }

    private List<Release.Track> tracks(List<SourceFile> audio,
                                       Map<SourceFile, Blob> blobs,
                                       String originDir) {
        List<TrackPosition> positions = parsePositions(audio);
        List<Release.Track> tracks = new ArrayList<>();
        for (int i = 0; i < audio.size(); i++) {
            SourceFile file = audio.get(i);
            TrackPosition position = positions.get(i);
            tracks.add(new Release.Track(
                    position.disc(),
                    position.number(),
                    reference(file, blobs),
                    position.title(),
                    originPath(file, originDir)));
        }
        return tracks;
    }

    /**
     * Parses the position of every audio file, falling back to sequential numbering in filename
     * order for the whole release if any filename carries no usable prefix or if the parsed
     * positions are not unique. SPEC.md section 4.7 requires {@code number} to be present and
     * unique per disc, so a partially parsed set would produce an invalid entity file.
     */
    private List<TrackPosition> parsePositions(List<SourceFile> audio) {
        List<TrackPosition> parsed = new ArrayList<>(audio.size());
        for (SourceFile file : audio) {
            Optional<TrackPosition> position = trackPrefixParser.parse(stem(file));
            if (position.isEmpty()) {
                return sequential(audio);
            }
            parsed.add(position.get());
        }
        return isUnique(parsed) ? parsed : sequential(audio);
    }

    private static List<TrackPosition> sequential(List<SourceFile> audio) {
        List<TrackPosition> positions = new ArrayList<>(audio.size());
        for (int i = 0; i < audio.size(); i++) {
            positions.add(new TrackPosition(null, i + 1, Nfc.normalize(stem(audio.get(i)))));
        }
        return positions;
    }

    private static boolean isUnique(List<TrackPosition> positions) {
        Set<String> seen = new HashSet<>();
        return positions.stream()
                .allMatch(position -> seen.add(position.disc() + "\u0000" + position.number()));
    }

    private List<Release.Asset> assets(List<SourceFile> files,
                                       Optional<SourceFile> cover,
                                       Map<SourceFile, Blob> blobs,
                                       String originDir) {
        return files.stream()
                .filter(file -> file.kind() != FileKind.AUDIO)
                .filter(file -> cover.filter(file::equals).isEmpty())
                .map(file -> new Release.Asset(
                        assetKindMapper.map(file.filename()),
                        reference(file, blobs),
                        originPath(file, originDir)))
                .toList();
    }

    private String reference(SourceFile file, Map<SourceFile, Blob> blobs) {
        return extensionDeriver.reference(blobs.get(file).hash(), file.filename());
    }

    private static String originPath(SourceFile file, String originDir) {
        return originDir == null ? null : file.relativePath();
    }

    private static String stem(SourceFile file) {
        return CoverFrontSelector.stem(file.filename());
    }

}
