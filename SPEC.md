# mu format — specification

**Version 1.0.0 — Draft.**

This document is the **normative specification** of the mu content-addressed music collection. It defines the on-disk format only: what a valid collection looks like, and how the layers relate.

It is deliberately implementation-neutral. It prescribes no programming language, no libraries and no command-line interface.

The keywords **must**, **must not**, **should** and **may** are used in their usual normative sense.

## Status

This document carries its own version, independent of the on-disk `format` value in `meta/.mu` (section 4.0): the version identifies the state of the text, `format` identifies the layout on disk. Version 1.0.0 describes `format = 1`.

It is a **draft**. While the specification is in draft status, `format = 1` may still change incompatibly and the text may change without the version being raised. Collections written against a draft are not guaranteed to be readable by the first stable release. From the first non-draft version onward the usual rule applies: an incompatible change to the on-disk layout requires `format = 2`, and the document version follows semantic versioning — major for incompatible format changes, minor for compatible additions, patch for clarifications that leave the format untouched.

## 1. Model

Three layers:

| Layer    | Purpose                                 | Mutable                           | In git |
|----------|-----------------------------------------|-----------------------------------|--------|
| `store/` | media files, addressed by their content | never (append-only)               | no     |
| `meta/`  | metadata, references, structure         | yes, by hand or via a tool        | yes    |
| `views/` | symlink trees for browsing and playback | generated, disposable at any time | no     |

### Core principles

1. **Content determines identity.** A media file is addressed by the hash of its content, not by its path.
2. **Entities have stable IDs.** Releases and artists carry an identifier that never changes. Names are attributes, not keys.
3. **Nothing is ever written into media files.** No tags, no renaming, no conversion. The store is bit-identical to what was imported.
4. **Views are pure functions of `meta` + `store`.** Every view is reproducible from the other two layers.
5. **A release has no location.** It has attributes. Where it shows up is the view's decision.

## 2. Directory layout

```
music/
├── .gitignore
├── store/
│   ├── ab/
│   │   ├── abcd3f…64hex…
│   │   └── ab77e1…64hex…
│   └── 3f/
│       └── 3f0a91…64hex…
│
├── meta/
│   ├── .mu                                    # collection format version file
│   ├── artists/
│   │   └── overmono.mu
│   │
│   └── releases/
│       └── good-lies.mu
│
└── views/
    ├── by-artist/
    │   └── Overmono/
    │       └── Good Lies [2023]/
    │           ├── 01 Feeling Plain.flac -> ../../../../store/ab/abcd3f…
    │           ├── cover.jpg             -> ../../../../store/3f/3f0a91…
    │           └── log.txt               -> ../../../../store/1a/1a2b3c…
    └── by-origin/
        └── Overmono - Good Lies (2023) [FLAC]/
            ├── 01 Overmono - Feeling Plain.flac -> ../../../store/7d/7d44e2…
            └── Good Lies.m3u                    -> ../../../store/9c/9c1d7a…
```

The collection root is the directory containing `meta/.mu` (and the `store/` and `meta/` subdirectories).

## 3. Store

### 3.1 Addressing

- Hash: **SHA-256** over the unmodified file content, hex, **lowercase**, 64 characters.
- The hash addresses **the file content only**. Two byte-identical files produce the same blob, no matter what they were named.
- Tags are part of that content. Two rips of the same CD whose files carry different tags therefore differ in content and are **two distinct blobs**. That is the intended consequence of principle 3 (section 1): the store keeps what was imported, tags included, and never normalizes them away.
- What content addressing does discard is the file's **name** and its position in a directory tree. Both are metadata, not identity, and live in `meta/` alongside the blob reference (sections 4.5, 4.9) — the same move the file type makes below, and for the same reason.

### 3.2 Layout

```
store/<hash[0:2]>/<hash>
```

- Sharded by the first two hex characters → 256 buckets.
- The blob filename is the bare hash: exactly 64 characters, **no extension**. The store path is a pure function of the content and of nothing else.
- Resolution **is** this path formula and nothing else. `store/` is never scanned to locate a blob, and a blob is never discovered by listing a directory. Entries under `store/` that do not match `<hash[0:2]>/<hash>` therefore have no effect on resolution; the format assigns them no meaning and a tool may use them for its own purposes.
- File type information is deliberately **not** kept here. It is metadata, not identity, and therefore lives in `meta/` as part of the blob reference (section 4.5), where it stays correctable.

### 3.3 Immutability

- An existing blob is **never** overwritten. If the target path already exists, the content is identical by definition → writing it again is a no-op.
- Identical content yields exactly **one** blob, no matter what the source files were named. Deduplication does not depend on filenames, extensions or letter case.

### 3.4 Atomicity

A blob becomes visible at its final path only as a whole. Every path `store/<h[0:2]>/<h>` that exists holds complete content whose SHA-256 is `<h>`; an observer never sees a partially written blob, and a writer interrupted at any point never leaves one behind.

This is the guarantee a reader relies on: a blob that resolves can be used without verifying it first.

How a writer achieves this is **not specified**. The staging location, the ordering of operations and the cleanup of anything left over from an interrupted write are the writer's business, as long as the guarantee above holds.

### 3.5 What belongs in the store

**All** binary content: audio files, cover art, rip logs, scans, booklets. Cover art is content-addressed and referenced just like tracks (`cover-front = "<hash>.jpg"`). Everything that is neither audio nor cover art is referenced through `[[asset]]` tables (section 4.8). `meta/` contains no binary data.

## 4. Meta

Every entity is **one TOML file**, named `<id>.mu`, encoded as UTF-8 without BOM, with **LF** line endings. The entity type is determined by the path (`artists/` vs. `releases/`).

### 4.0 Collection marker

`meta/.mu` marks the collection root and carries the format version:

```toml
format = 1
```

- `format` is an **integer**, required. This specification (version 1.0.0, draft) defines version `1`.
- A tool that encounters a `format` value **higher** than the version it implements must refuse to write and should refuse to read, rather than silently degrade.

Within `meta/`, the format knows `.mu`, `artists/` and `releases/`. Entries directly under `meta/` that are none of these have no meaning; the format assigns them none, and a tool may use them for its own purposes — for a write lock, for staging, for a cache. This mirrors section 3.2: what the format does not define, it does not claim.

### 4.1 Entities

| Entity  | Path                    |
|---------|-------------------------|
| Artist  | `meta/artists/<id>.mu`  |
| Release | `meta/releases/<id>.mu` |

Tracks are not separate files but `[[track]]` tables inside the release file (section 4.7).

`<id>` is the entity's **identifier**. The filename stem is the entity's identity; the identifier is not repeated inside the file.

An identifier **must**:

1. be non-empty and at most **200 bytes** long when encoded as UTF-8;
2. be NFC-normalized (section 4.3);
3. contain neither `/` (U+002F) nor control characters (U+0000–U+001F);
4. neither begin nor end with a space or a dot;
5. be unique within its directory, compared after NFC normalization **and** case folding.

Rules 1, 3 and 4 are exactly the transformations of section 5.2, so a valid identifier passes name construction unchanged — which is what makes the last step of the collision ladder guaranteed unique (section 5.3). Rule 5 names case folding because widely used filesystems (APFS, NTFS) are case-insensitive by default, where `Abc.mu` and `abc.mu` would be **one** file. An artist and a release may carry the same identifier; the path determines the type.

An identifier is **stable**: once assigned it **must not** be changed, and it **must not** be reused for a different entity. In particular it does not track the entity's `name` or `title` — an artist who changes their name keeps their identifier. Renaming an entity file changes the entity's identity and invalidates every reference to it (section 4.5). This is principle 2 of section 1: names are attributes, not keys.

Two forms satisfy this equally, and the choice is the writer's:

- A **readable identifier**, derived from the name when the entity is created (`overmono`, `good-lies`). Diffs and hand edits stay legible, which matters because `meta/` is meant to be editable by hand (section 1). The cost is that two writers can derive the same identifier for two different entities, and that a later rename tempts one into changing it — which the rule above forbids.
- An **opaque identifier**, randomly generated; UUIDv4 and UUIDv7 are the obvious choices. Collision-free without coordination, which matters for section 6: independent branches create entities without talking to each other, and merging two colliding identifiers would fuse two distinct entities without raising a conflict. The cost is that every reference becomes unreadable.

A collection may mix both. The examples in this document use readable identifiers because they are easier to follow.

### 4.2 Value conventions

- A value is an **integer** when it is a count, or a quantity in a fixed implied unit that admits exactly one spelling: `track.number`, `track.duration` (seconds).
- All other attribute values are **strings**, apart from flags.
- **Flag** = boolean `true` (`is-group = true`).
- **Dual-typed**: `track.disc` is the only attribute that accepts two types — an integer for numbered discs, a string for medium sides (`"A"`, `"B"`). Section 4.7.
- **Multiple value** = string array, inherently ordered (`member = ["id1", "id2"]`).
- **Multi-line value** = TOML multi-line string (`notes = """ … """`).
- **Credit** = table (`[[credit]]`, `[[track.credit]]`), section 4.6.
- References are strings without any path component (section 4.5).

### 4.3 Unicode normalization

String values are normalized to NFC on write and normalized to NFC again on read.

### 4.4 Cardinality

| Case             | TOML notation                     |
|------------------|-----------------------------------|
| Single value     | `title = "Good Lies"`             |
| Flag             | `is-group = true`                 |
| Multiple value   | `member = ["id1", "id2"]`         |
| Multi-line value | `notes = """` … `"""`             |
| Credit           | `[[credit]]` / `[[track.credit]]` |

An attribute is either scalar or array; mixing the two is invalid.

### 4.5 References

All references are string values, without a path component:

| Reference type | Notation                                                | Resolution                       |
|----------------|---------------------------------------------------------|----------------------------------|
| to an artist   | `artist = "<id>"` (only inside a credit, section 4.6)   | `meta/artists/<id>.mu`           |
| to a blob      | `blob = "<hash>[.<ext>]"`                               | `store/<hash[0:2]>/<hash>`       |
| to cover art   | `cover-front = "<hash>[.<ext>]"`                        | same as blob                     |

A blob reference is the 64-character hash, optionally followed by `.` and an extension. **Resolution uses the hash alone**: everything from the first `.` onward is ignored when computing the store path, which is therefore always computable without scanning a directory. No path ever appears in a reference. `asset.blob` (section 4.8) uses the same notation and the same resolution.

The extension is **not** part of the blob's identity and has no effect on resolution. It is a rendering hint for the builder, which uses it as the file suffix in `views/` (section 5.4). It should describe the content (`flac`, `m4a`, `jpg`) and should match `[a-z0-9]{1,8}`; a tool may warn about other values but must not reject them and must preserve them verbatim. Because the extension lives in `meta/`, a wrong one is corrected by editing the entity file — the store is never touched.

The extension may be omitted: `blob = "<hash>"` is a valid reference (section 5.4 defines the resulting view filename). Two references to the same blob may carry different extensions; this is valid and yields different view filenames for the same content.

The extension is the smallest piece of the original filename that a reference can carry. A reference may additionally record the **whole** original path through `origin-path`, which is an attribute beside the reference rather than part of it; section 4.9.

### 4.6 Credits and roles

Artist participation is expressed through **credits**. A credit is a TOML table:

| Field    | Cardinality         | Required | Meaning                                                   |
|----------|---------------------|----------|-----------------------------------------------------------|
| `role`   | single              | yes      | role, lowercase, words separated by `-`                   |
| `artist` | single (ref artist) | yes      | reference to `meta/artists/<id>.mu`                       |
| `as`     | single              | no       | name as printed on **this** release                       |
| `detail` | single              | no       | free-form role qualifier (`"additional"`, `"uncredited"`) |

A credit therefore bridges two layers: `artist` is the entity (queryable, linkable), while `as` records how the participation was actually printed on the release.

#### Rules

1. **Placement.** Release credits are `[[credit]]` tables in the release file; track credits are `[[track.credit]]` tables inside the respective `[[track]]`.
2. **No `artist` attribute at entity level.** A release has no `artist = [...]`; participation lives exclusively in credits.
3. **Requirement.** Every release has at least one credit with `role = "main"`.
4. **Order is authoritative.** The file order of credits determines the order of the billing line. The builder does **not** reorder credits (unlike `[[track]]`, section 4.7).
5. **Billing line.** It is reconstructed from all credits with `role = "main"`, in file order: for each credit `as` (falling back to the `name` of the referenced artist), joined with `", "`.
6. **Inheritance applies to `main` only.** If a track contains no credit with `role = "main"`, the release's `main` credits apply. All other roles apply exclusively at the level where they appear: release credits describe the release as a whole, track credits describe the track. There is no merging, no per-role overriding, and no empty list to disable an inherited role.
7. **`title` stays untouched.** The builder **never** synthesizes credit information into a title. If `(feat. …)` is part of the printed title, it lives in `title`; if it is not, it does not appear in the view either. Credits are additional information, never a replacement.
8. **Write form.** The canonical write form is the block table (`[[credit]]`). Inline tables are additionally accepted on read, since TOML defines them as equivalent.

#### Role vocabulary

| Role          | Meaning                                       |
|---------------|-----------------------------------------------|
| `main`        | primary participation, forms the billing line |
| `feat`        | guest contribution                            |
| `remixer`     |                                               |
| `producer`    |                                               |
| `written-by`  |                                               |
| `mixed-by`    |                                               |
| `mastered-by` |                                               |

The vocabulary is **open**. Unknown roles are valid and are preserved verbatim.

#### Example

```toml
title = "Fussballprofi + Was Wenn"
type = "single"
release-year-original = "2015"
source-medium = "vinyl"

[[credit]]
role = "main"
artist = "eloquenz"
as = "Eloquenz"            # as printed

[[credit]]
role = "main"
artist = "hulk-hodn"

[[credit]]
role = "producer"
artist = "hulk-hodn"
detail = "additional"

[[track]]
number = 1
blob = "abcd3f….m4a"
title = "Fußballprofi"

[[track]]
number = 2
blob = "ef0122….m4a"
title = "Was Wenn (feat. Umse)"

[[track.credit]]
role = "feat"
artist = "umse"
```

Reconstructed billing line: `Eloquenz, Hulk Hodn`. Track 2 inherits both `main` credits and adds a `feat` credit; its `title` stays exactly as printed on the record.

### 4.7 Tracks and discs

Tracks are `[[track]]` tables inside the release file:

```toml
[[track]]
disc = 2
number = 5
blob = "beef01….flac"
title = "Kink"
```

- `number` (integer): required, **must be ≥ 1**. `disc`: optional, defaults to the integer `1`; an integer `disc` must also be ≥ 1. `disc` accepts an **integer** for numbered discs or a **string** for medium sides (`disc = "A"` for vinyl).
- Ordering: by `(disc, number)`. Integer discs sort numerically and always sort **before** string discs; string discs sort by NFC code point. `number` is always compared numerically. The file order of the `[[track]]` tables is **not authoritative** — the builder sorts them itself. (Credits behave the opposite way, section 4.6, rule 4.)
- Position and disc live exclusively in the `disc`/`number` keys; there is no sort-key string.
- `number` is unique per `disc`.

Track attributes:

| Attribute     | Cardinality            | Required       | Meaning                                |
|---------------|------------------------|----------------|----------------------------------------|
| `number`      | single (int)           | yes            | track number                           |
| `disc`        | single (int or string) | no (default 1) | disc number, or medium side for vinyl  |
| `blob`        | single                 | yes            | reference to the audio file            |
| `title`       | single                 | yes            | track name, as printed                 |
| `duration`    | single (int)           | no             | length in seconds                      |
| `isrc`        | single                 | no             |                                        |
| `origin-path` | single                 | no             | original path of the file, section 4.9 |

Participating artists are not stored in an attribute but in `[[track.credit]]` tables (section 4.6).

### 4.8 Schema

**Artist** (`meta/artists/<id>.mu`)

| Attribute | Cardinality | Required |
|-----------|-------------|----------|
| `name`    | single      | yes      |

**Release** (`meta/releases/<id>.mu`)

| Attribute                           | Cardinality                           | Required                       |
|-------------------------------------|---------------------------------------|--------------------------------|
| `title`                             | single                                | yes                            |
| `credit`                            | credit tables                         | yes (≥ 1 with `role = "main"`) |
| `cover-front`                       | single (ref blob)                     | no                             |
| `cover-front-origin-path`           | single                                | no                             |
| `origin-dir`                        | single                                | no                             |
| `asset`                             | asset tables                          | no                             |

`origin-dir` and `cover-front-origin-path` record the artefact the release was received as. Section 4.9 defines them, together with the constraints their values must satisfy.

Unknown attributes are **allowed** and preserved verbatim; they are ignored when sorting for deterministic builds. Appendix A lists conventional attributes that tools may read and write.

#### Assets

Everything that belongs to a release but is neither audio nor cover art — rip logs, booklets, scans, cue sheets — is referenced through repeatable `[[asset]]` tables:

```toml
[[asset]]
kind = "log"
blob = "1a2b3c….txt"
```

| Attribute     | Cardinality       | Required | Meaning                                  |
|---------------|-------------------|----------|------------------------------------------|
| `kind`        | single            | yes      | asset category, see vocabulary below     |
| `blob`        | single (ref blob) | yes      | reference to the file in the store       |
| `title`       | single            | no       | display name, e.g. `"Booklet page 3"`; becomes the view filename (section 5.4) |
| `origin-path` | single            | no       | original path of the file, section 4.9   |

Kind vocabulary: `log`, `booklet`, `scan`, `cue`, `other`. The vocabulary is **open**; unknown kinds are valid and preserved verbatim (same handling as `role`, section 4.6).

Assets attach to the release, not to individual tracks: rip logs and booklets describe the medium as a whole. They are materialized in `views/` alongside the tracks of the release (section 5.4).

#### Example release (multi-CD)

Attributes beyond the schema above — `type`, `release-year-original`, `source-medium`, `bit-depth`, `sample-rate`, `notes`, `duration` — are conventional (Appendix A).

```toml
title = "Good Lies"
type = "album"
release-year-original = "2023"
source-medium = "cd"
bit-depth = 16
sample-rate = 44100
cover-front = "3f0a91….jpg"

notes = """
Ripped 2023-04-01.
AccurateRip ok.
"""

[[credit]]
role = "main"
artist = "overmono"

[[asset]]
kind = "log"
blob = "1a2b3c….txt"

[[track]]
disc = 1
number = 1
blob = "abcd3f….flac"
title = "Feeling Plain"
duration = 234

[[track]]
disc = 1
number = 2
blob = "ab77e1….flac"
title = "Arla Fearn"

[[track]]
disc = 2
number = 1
blob = "beef01….flac"
title = "Kink"

[[track.credit]]
role = "feat"
artist = "anz"
```

### 4.9 Origin paths

Content addressing keeps a file's bytes and discards its name (section 3.1). Usually that is the point: the name was noise, and section 5.4 builds a better one out of metadata. For an artefact that arrived **as a whole** — a purchased download, an archived bundle, any directory that came with a playlist or a checksum file — the names are not noise. They are part of what was received, the files shipped alongside refer to them, and once dropped they cannot be recovered from the store.

Two optional attributes record them:

- `origin-dir` on the release: the name of the directory the release arrived as, a **single** path segment.
- `origin-path` beside a blob reference: that file's path within the directory, relative to it, `/` as separator.

`origin-path` sits on `[[track]]` (section 4.7) and `[[asset]]` (section 4.8). `cover-front` is a scalar with no room for a companion key, so it takes its origin path from the release-level `cover-front-origin-path`.

#### Constraints

Split at `/`, every segment of an `origin-path` — and `origin-dir`, which is one segment — must:

1. be non-empty, and be neither `.` nor `..`;
2. pass the name construction of section 5.2 **unchanged**: NFC, no `/`, no control characters, no leading or trailing space or dot, at most 200 bytes.

A path therefore never begins or ends with `/` and never contains `//`.

Rule 2 is the device section 4.1 already applies to identifiers: a value that survives sanitization untouched can be written to the filesystem verbatim. That is what makes the guarantee in section 5.4 possible — the view reproduces the recorded names exactly, so a checksum file or playlist that shipped with the release still resolves inside the view. A value that would have to be rewritten is invalid rather than quietly sanitized, because a rewritten name no longer matches the artefact it claims to reproduce.

`origin-path` is **not a reference** and takes no part in resolution: the store path comes from the hash in the `blob` value and from nothing else (section 4.5). A wrong `origin-path` misplaces a file in one view and has no other effect.

#### Uniqueness and pairing

Within one release, `origin-path` values are unique, compared after NFC normalization **and** case folding, for the reason given in section 4.1, rule 5. Two files claiming the same original path contradict each other about what was received, which is not a naming accident.

The two attributes are independent and either may stand alone:

- `origin-path` without `origin-dir`: the release has no origin tree, so nothing is materialized (section 5.4). The value is preserved and stays a correct record of where the file came from.
- `origin-dir` without an `origin-path` on every file: the tree is materialized from those files that carry one, and the rest are left out. The format never invents a name to fill a gap — a synthesized name would misrepresent the artefact, and section 5.4 already provides synthesized names in `by-artist`.

#### Example

`type`, `release-year-original` and `source-medium` are conventional (Appendix A).

```toml
title = "Good Lies"
type = "album"
release-year-original = "2023"
source-medium = "web"
origin-dir = "Overmono - Good Lies (2023) [FLAC]"
cover-front = "c4b8e0….jpg"
cover-front-origin-path = "artwork/front.jpg"

[[credit]]
role = "main"
artist = "overmono"

[[asset]]
kind = "playlist"
blob = "9c1d7a….m3u"
origin-path = "Good Lies.m3u"

[[asset]]
kind = "checksums"
blob = "5e21b0….sha256"
origin-path = "checksums.sha256"

[[track]]
number = 1
blob = "7d44e2….flac"
title = "Feeling Plain"
origin-path = "01 Overmono - Feeling Plain.flac"
```

The `kind` values `playlist` and `checksums` are not in the vocabulary listed in section 4.8 and do not need to be — that vocabulary is open, like `role` and `type`.

Nothing here changes how the release is presented anywhere else: `title` stays the curated title, `[[track]]` keeps its own numbering, and `by-artist` names its files as section 5.4 prescribes. The origin tree is a second, parallel presentation of the same blobs, not a replacement for the first.

One artefact is one release. The album above is the same one as in section 4.8, held a second time: different bytes, different blobs, a different medium, and therefore a **second** entity with an identifier of its own — `good-lies` and `good-lies-web`, not one file trying to describe both. Section 5.3 tells the two apart in `by-artist` by their identifier.

## 5. Views

### 5.1 Principle

A view is a directory tree of **relative** symlinks into the store. Views contain no information that is not in `meta/`. They are never read, only written.

### 5.2 Name construction

Filesystem names are derived from attribute values with the following sanitization:

1. NFC normalization.
2. `/` → `_` (U+005F).
3. Strip control characters (U+0000–U+001F).
4. Trim leading and trailing spaces and dots.
5. Truncate to 200 bytes (respecting UTF-8 character boundaries, never mid-codepoint). Truncation occurs after trimming spaces.
6. If the result is empty, use `_`.

### 5.3 Collisions

Two releases by the same artist with the same title produce the same view path. Resolved in two steps:

1. `Title`
2. `Title (<identifier>)`

Step 2 is guaranteed unique: identifiers are pairwise distinct and section 5.2 leaves them unchanged (section 4.1), so the full identifier is always an available fallback. Colliding releases are sorted by identifier in NFC code point order and processed in that order.

The ladder is applied **per directory**, not per view: the collision scope is the single artist directory in which the entry is created.

#### Collisions in `by-origin`

`by-origin` keys on `origin-dir` (section 4.9), and two releases may carry the same one — the same bundle taken in twice, or two artefacts a source named identically. Two steps:

1. `<origin-dir>`
2. `<origin-dir> (<identifier>)`

Step 2 is guaranteed unique for the reason given above, colliding releases sorted by identifier in NFC code point order. The collision scope is `by-origin/` itself, which has only one level of directories.

The suffix lands on the **directory** and never on a file inside it. That is the point of putting it there: a checksum file or playlist refers to the names beside it, not to the name of the directory it sits in, so decorating the directory keeps the tree usable while renaming a file would not.

### 5.4 Standard views

| View        | Structure                                                                                                                        |
|-------------|----------------------------------------------------------------------------------------------------------------------------------|
| `by-artist` | `by-artist/<artist-name>/<release-name>/<NN Title.ext>` — names built from metadata; a release appears under **every** one of its `main` artists |
| `by-origin` | `by-origin/<origin-dir>/<origin-path>` — the release as it was received, names verbatim (section 4.9)                            |

`by-artist` and `by-origin` are the two views that symlink directly into the store, and they are complements: `by-artist` names every file from metadata, `by-origin` names none of them.

#### Credits in views

- **Grouping in `by-artist` is based exclusively on credits with `role = "main"`.** An artist who participates only as `feat`, `remixer`, `producer` or similar does **not** get their own `by-artist` directory.
- Directory names use the `name` attribute of the **artist entity**, not `as`. The directory represents the artist, not a single billing.
- A release lacking the attribute a view is keyed on is **omitted from that view** — no `unknown/` bucket, no `_` placeholder.

#### Track filenames

`<sortkey> <sanitized track-title>.<ext of the blob reference>`, e.g. `01 Feeling Plain.flac`, or `2-05 Kink.m4a` for multi-disc releases. If the reference carries no extension (section 4.5), the name is written without a suffix and **without** a trailing dot: `01 Feeling Plain`. The sort key is derived by the builder from `disc`/`number` (`[<disc>-]<number>`, number zero-padded to **at least** two digits — a `number` of 100 or more keeps all its digits and is not truncated). A string `disc` is inserted verbatim after sanitization (section 5.2), so a vinyl side yields `A-01 Feeling Plain.flac`; zero-padding applies to `number` only.

`title` is taken **verbatim**. The builder never appends `(feat. …)` or any other credit information (section 4.6, rule 7).

Compilation exception: if a track's `main` credits differ from the release's, the filename becomes

`<sortkey> <billing of the track> - <sanitized track-title>.<ext>`

e.g. `03 Kuhn Fu - Waffle House.m4a`. Without that prefix a compilation directory would be unusable. If the track inherits the release credits, the prefix is omitted.

`<billing>` here is the reconstructed billing line (section 4.6, rule 5), i.e. including `as` names — not a join of entity names.

#### Cover filename

Cover art is materialized as `cover.<ext of the blob reference>`, e.g. `cover.jpg`. The extension rule is the one for tracks: it comes from the blob reference, and a reference without an extension yields `cover` without a suffix and without a trailing dot.

#### Asset filenames

Assets (section 4.8) are materialized in the `by-artist` release directory, flat, beside the tracks:

`<sanitized asset-title, or kind if title is absent>.<ext of the blob reference>`

e.g. `Booklet page 3.jpg` for an asset carrying a `title`, and `log.txt` for one that does not. The extension rule is the one for tracks: it comes from the blob reference, and a reference without an extension yields a name without a suffix and **without** a trailing dot (section 4.5). No sort key is prefixed — assets have no position.

There is no `assets/` subdirectory and no grouping by `kind`. A rip log sits next to the tracks it describes, which is where a player, a tag editor and a human all look for it.

An asset's view name must be unique within the release and must not equal a track filename or the cover filename. A release where derived asset names collide, or collide with a track or the cover, is **invalid** — same treatment as `origin-path` (section 4.9), no decoration.

#### `by-origin`

`by-origin` reproduces the artefact a release was received as (section 4.9). It is the one view that contributes no naming of its own.

1. **Scope.** A release appears if and only if it carries `origin-dir`; one that does not is omitted, like any release lacking the attribute a view keys on. A file appears if and only if it carries an `origin-path` — for cover art, if the release carries `cover-front-origin-path`.
2. **Names verbatim.** Every path segment is used exactly as recorded. Section 5.2 is **not** applied here, and not applying it changes nothing: section 4.9 already requires each segment to pass it unchanged. That is the whole point of the constraint — a checksum file or playlist carried along in the tree still resolves against the names beside it.
3. **Structure.** All segments but the last become real directories, created as needed. Only the last segment is a symlink, and it points into the store, relative like every other link (section 5.1).
4. **No suffixes, no ladder.** Origin paths are unique within a release (section 4.9), so nothing can collide.
5. **The two filename rules above do not apply.** No sort key, no compilation prefix, no asset title, and no extension taken from the blob reference — the recorded path already carries whatever suffix the file had.
6. **Ordering.** Releases by `origin-dir`, files by `origin-path`, both in NFC code point order (section 5.6).

Two releases carrying the same `origin-dir` collide in this view; section 5.3 resolves it.

### 5.5 Disposability

`views/` is **optional**. A collection without it is valid, and the tree may be deleted or regenerated at any time without loss — it carries nothing that is not already derivable from `meta/` and `store/` (section 5.1), and nothing ever resolves through it.

How a builder produces the tree, and whether it rebuilds from scratch or updates an existing tree in place, is **not specified**. Section 5.6 constrains the result, not the procedure: any builder whose output satisfies it conforms.

### 5.6 Determinism

Building twice against the same meta state must produce byte-identical trees. All directory iteration is explicitly sorted (by identifier or by NFC-normalized name, in NFC code point order); directory-listing order is never inherited from the filesystem.

In `by-origin` the sort keys are `origin-dir` for releases and `origin-path` for files, again in NFC code point order. Both are total orders: origin paths are unique within a release, and colliding `origin-dir` values are separated by section 5.3 before the tree is written.

Credits are exempt: their order comes from the file (section 4.6, rule 4) and is therefore already deterministic.

## 6. Git integration

Only `meta/` is versioned. `.gitignore` in the collection root:

```
/store/
/views/
/meta/.lock
.DS_Store
```

`/meta/.lock` is not required by the format. It is the scratch entry the reference implementation places under `meta/` (section 4.0); a tool using a different name adds its own line.

To keep the LF line endings of section 4 stable across platforms, add a `.gitattributes` in the collection root:

```
*.mu text eol=lf
```

The repository should be configured for precomposed Unicode, matching the NFC rule in section 4.3:

```sh
git config core.precomposeunicode true
```

Diffs are meaningful because a value change shows up as a one-line change to the entity file:

```
diff --git a/meta/releases/b27e….mu b/meta/releases/b27e….mu
-title = "Good Lise"
+title = "Good Lies"
```

## Appendix A: conventional attributes

This appendix lists keys that the format itself prescribes no behaviour for, but that the reference implementation reads and writes. Tools that share a collection should use the same spelling to keep metadata portable.

Unknown attributes are valid everywhere (section 4.8); the keys below are not normative — a collection is valid without them, and a tool may read, write or ignore them freely.

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

| Attribute                     | Cardinality       | Type                                                     |
|-------------------------------|-------------------|----------------------------------------------------------|
| `type`                        | single            | string (`album`, `ep`, `single`, `compilation`, `live`)  |
| `release-year-original`       | single            | string                                                   |
| `release-year-medium`         | single            | string                                                   |
| `source-medium`               | single            | string (`cd`, `vinyl`, `file`, `web`, `tape`)            |
| `source-store`                | single            | string                                                   |
| `rip-result`                  | single            | string                                                   |
| `bit-depth`                   | single (int)      | integer, ≥ 1                                             |
| `sample-rate`                 | single (int)      | integer, ≥ 1, in hertz (`44100`, not `44.1`)             |
| `bitrate`                     | single            | string (VBR presets, averages or `"lossless"`)            |
| `channel-mode`                | single            | string                                                   |
| `notes`                       | single (multi-line)| string                                                   |
| `discogs-release-id`          | single            | string                                                   |
| `discogs-master-id`           | single            | string                                                   |

Vocabularies for `type` and `source-medium` are **open**; unknown values are valid and preserved verbatim.

### Track

| Attribute  | Cardinality   | Type    |
|------------|---------------|---------|
| `duration` | single (int)  | integer |
| `isrc`     | single        | string  |
