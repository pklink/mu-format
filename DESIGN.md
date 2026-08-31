# mu format — design notes

This document contains rationale and conventional attributes that are not normative. [SPEC.md](SPEC.md) defines the on-disk format.

## Content addressing

Content determines identity, not path or filename. The same bytes, once stored, are the same blob — no matter what the file was named, where it came from, or how many releases reference it. Two byte-identical files always produce the same blob. Tags are part of a file's content, so two rips of the same CD with different tags are two distinct blobs. This is by design: the store keeps exactly what was imported and never normalizes it away.

Discarding filenames is usually the point. A track called `01_Feeling_Plain_(WEB_rip)_FINAL.flac` gets a clean name based on metadata. But for an artefact that arrived as a complete directory — a download bundle, an archived release — the names matter: a playlist or checksum file inside the directory refers to them. `origin-dir` and `origin-path` record those names so the tree remains reconstructible ([SPEC.md](SPEC.md) section 4.8).

## Identifiers: readable vs opaque

Entity identifiers follow [SPEC.md](SPEC.md) section 4.2. Two forms satisfy it:

- A **readable identifier**, derived from the name when the entity is created (`overmono`, `good-lies`). Diffs and hand edits stay legible, which matters because `meta/` is meant to be editable by hand. The cost is that two writers can derive the same identifier for two different entities, and that a later rename tempts one into changing it — which the rule forbids.

- An **opaque identifier**, randomly generated; UUIDv4 and UUIDv7 are the obvious choices. Collision-free without coordination, which matters when independent branches create entities. The cost is that every reference becomes unreadable.

A collection may mix both.

## Credits

Credits bridge two layers: `artist` references the entity (queryable, linkable), while `as` records how the participation was printed on this particular release. The billing line is reconstructed from `main` credits in file order, using `as` names where present. This preserves archival fidelity without duplicating the entity name.

Only `main` credits inherit from release to track (the artist who made the whole release is typically the artist of every track). All other roles — `feat`, `producer`, `remixer` — apply exclusively at the level where they appear.

## Origin names

`origin-dir` and `origin-path` ([SPEC.md](SPEC.md) section 4.8) are required to be portable names. A checksum file or playlist shipped alongside the files must still resolve when the tree is reconstructed, so the names cannot be sanitized — they must survive the filesystem unchanged. Values that would need rewriting are rejected rather than silently altered, because a rewritten name no longer matches the artefact it claims to reproduce.

## Views

Views are pure functions of `meta/` + `store/`. They are never read and carry no information not already in the other two layers. The layout of specific views is therefore not fixed by the format: every implementation is free to arrange its trees differently, as long as it does not break the immutability guarantees of the store.

## Flat assets

Assets (`[[asset]]` tables) are materialized in the release directory, flat, beside the tracks. A rip log next to the tracks it describes is where a player, a tag editor and a human all look for it. There is no `assets/` subdirectory and no grouping by `kind`.

## Conventional attributes

This section lists keys that the format itself prescribes no behaviour for, but that the reference implementation reads and writes. Tools that share a collection should use the same spelling to keep metadata portable.

Unknown attributes are valid everywhere ([SPEC.md](SPEC.md) section 4.7); the keys below are not normative — a collection is valid without them, and a tool may read, write or ignore them freely.

### Artist

| Attribute           | Cardinality           | Type                      |
|---------------------|-----------------------|---------------------------|
| `alias`             | multiple              | string array              |
| `is-group`          | flag                  | boolean                   |
| `member`            | multiple (ref artist) | string array              |
| `notes`             | single (multi-line)   | string                    |
| `sort-name`         | single                | string                    |
| `discogs-artist-id` | single                | string                    |

Example:

```toml
name = "Overmono"
is-group = true
member = ["tom-russell", "ed-russell"]
sort-name = "Overmono"
discogs-artist-id = "1234567"
```

### Release

| Attribute                     | Cardinality        | Type                                                     |
|-------------------------------|--------------------|----------------------------------------------------------|
| `type`                        | single             | string (`album`, `ep`, `single`, `compilation`, `live`)  |
| `release-year-original`       | single             | string                                                   |
| `release-year-medium`         | single             | string                                                   |
| `source-medium`               | single             | string (`cd`, `vinyl`, `file`, `web`, `tape`)            |
| `source-store`                | single             | string                                                   |
| `rip-result`                  | single             | string                                                   |
| `bit-depth`                   | single (int)       | integer, ≥ 1                                             |
| `sample-rate`                 | single (int)       | integer, ≥ 1, in hertz (`44100`, not `44.1`)             |
| `bitrate`                     | single             | string (VBR presets, averages or `"lossless"`)            |
| `channel-mode`                | single             | string                                                   |
| `notes`                       | single (multi-line)| string                                                   |
| `discogs-release-id`          | single             | string                                                   |
| `discogs-master-id`           | single             | string                                                   |

Vocabularies for `type` and `source-medium` are **open**; unknown values are valid and preserved verbatim.

### Track

| Attribute  | Cardinality   | Type    |
|------------|---------------|---------|
| `duration` | single (int)  | integer |
| `isrc`     | single        | string  |