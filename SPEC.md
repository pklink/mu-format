# mu format — specification

**Version 1.0.0 — Draft.**

This document is the **normative specification** of the mu content-addressed music collection. It defines the on-disk format only: what a valid collection looks like.

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
    └── by-artist/
        └── Overmono/
            └── Good Lies [2023]/
                ├── 01 Feeling Plain.flac -> ../../../../store/ab/abcd3f…
                └── cover.jpg             -> ../../../../store/3f/3f0a91…
```

The collection root is the directory containing `meta/.mu` (and the `store/` and `meta/` subdirectories).

## 3. Store

### 3.1 Addressing

- Hash: **SHA-256** over the unmodified file content, hex, **lowercase**, 64 characters.
- The hash addresses **the file content only**. Two byte-identical files produce the same blob, no matter what they were named.
- Tags are part of that content. Two rips of the same CD whose files carry different tags differ in content and are **two distinct blobs**. This follows from principle 3 (section 1): the store keeps what was imported.
- Content addressing does discard the file's **name** and its position in a directory tree. Both are metadata and live in `meta/`.

### 3.2 Layout

```
store/<hash[0:2]>/<hash>
```

- Sharded by the first two hex characters → 256 buckets.
- The blob filename is the bare hash: exactly 64 characters, **no extension**. The store path is a pure function of the content.
- Entries under `store/` that do not match `<hash[0:2]>/<hash>` have no meaning; the format assigns them none, and a tool may use them for its own purposes.
- File type information is not kept here. It is metadata and lives in `meta/` as part of the blob reference (section 4.4).

### 3.3 Immutability and atomicity

An existing blob is **never** overwritten. If the target path already exists, the content is identical by definition → writing it again is a no-op. Identical content yields exactly **one** blob.

A blob becomes visible at its final path only as a whole. Every path `store/<h[0:2]>/<h>` that exists holds complete content whose SHA-256 is `<h>`; an observer never sees a partially written blob. A blob that resolves can therefore be used without verifying it first.

How a writer achieves this is **not specified** — staging location, ordering of operations and cleanup are the writer's business, as long as the guarantee above holds.

### 3.4 What belongs in the store

**All** binary content: audio files, cover art, rip logs, scans, booklets. `meta/` contains no binary data.

## 4. Meta

Every entity is **one TOML file**, named `<id>.mu`, encoded as UTF-8 without BOM, with **LF** line endings. The entity type is determined by the path (`artists/` vs. `releases/`).

### 4.0 Collection marker

`meta/.mu` marks the collection root and carries the format version:

```toml
format = 1
```

- `format` is an **integer**, required. This specification (version 1.0.0, draft) defines version `1`.
- A tool that encounters a `format` value **higher** than the version it implements must refuse to write and should refuse to read.

Within `meta/`, the format knows `.mu`, `artists/` and `releases/`. Entries directly under `meta/` that are none of these have no meaning; a tool may use them for its own purposes — for a write lock, for staging, for a cache.

### 4.1 Portable names

A **portable name** is a non-empty Unicode string at most **200 bytes** long when encoded as UTF-8, that:

- is NFC-normalized;
- contains neither `/` (U+002F) nor control characters (U+0000–U+001F);
- neither begins nor ends with a space or a dot;
- is neither `.` nor `..`.

Whenever the format requires a value to be usable as a filesystem name, it requires a portable name. A writer that produces a portable name can write it to the filesystem verbatim.

### 4.2 Entities and identifiers

| Entity  | Path                    |
|---------|-------------------------|
| Artist  | `meta/artists/<id>.mu`  |
| Release | `meta/releases/<id>.mu` |

Tracks are not separate files but `[[track]]` tables inside the release file (section 4.6).

`<id>` is the entity's **identifier**. The filename stem is the entity's identity; the identifier is not repeated inside the file.

An identifier **must** be a portable name (section 4.1) and **must** be unique within its directory, compared after NFC normalization **and** case folding. Case folding accounts for widely used filesystems that are case-insensitive by default (APFS, NTFS), where `Abc.mu` and `abc.mu` would be one file. An artist and a release may carry the same identifier; the path determines the type.

An identifier is **stable**: once assigned it **must not** be changed, and it **must not** be reused for a different entity. In particular it does not track the entity's `name` or `title` — an artist who changes their name keeps their identifier. Renaming an entity file changes the entity's identity and invalidates every reference to it. This is principle 2 (section 1): names are attributes, not keys.

### 4.3 Values and cardinality

| Case             | TOML notation                     |
|------------------|-----------------------------------|
| Single value     | `title = "Good Lies"`             |
| Flag             | `is-group = true`                 |
| Multiple value   | `member = ["id1", "id2"]`         |
| Multi-line value | `notes = """` … `"""`             |
| Credit           | `[[credit]]` / `[[track.credit]]` |

- A value is an **integer** when it is a count, or a quantity in a fixed implied unit that admits exactly one spelling: `track.number`, `track.duration` (seconds).
- All other attribute values are **strings**.
- **Dual-typed**: `track.disc` accepts an integer for numbered discs or a string for medium sides (`"A"`, `"B"`). Section 4.6.
- **Flag** = boolean `true` (`is-group = true`).
- **Multiple value** = string array, inherently ordered (`member = ["id1", "id2"]`).
- An attribute is either scalar or array; mixing the two is invalid.

String values are normalized to **NFC** on write and on read.

### 4.4 References

All references are string values, without a path component:

| Reference type | Notation                                                | Resolution                       |
|----------------|---------------------------------------------------------|----------------------------------|
| to an artist   | `artist = "<id>"` (only inside a credit, section 4.5)   | `meta/artists/<id>.mu`           |
| to a blob      | `blob = "<hash>.<ext>"`                                 | `store/<hash[0:2]>/<hash>`       |

A blob reference is the 64-character hash, followed by `.` and an extension. **Resolution uses the hash alone**: everything from the first `.` onward is ignored when computing the store path. No path ever appears in a reference.

The extension is a rendering hint. It should describe the content (`flac`, `m4a`, `jpg`) and should match `[a-z0-9]{1,8}`; a tool that cannot determine an extension should use `bin`. A wrong extension is corrected by editing the entity file — the store is never touched.

Two references to the same blob may carry different extensions; this is valid and yields different view filenames for the same content.

### 4.5 Credits

Artist participation is expressed through **credits**. A credit is a TOML table:

| Field    | Cardinality         | Required | Meaning                                                   |
|----------|---------------------|----------|-----------------------------------------------------------|
| `role`   | single              | yes      | role, lowercase, words separated by `-`                   |
| `artist` | single (ref artist) | yes      | reference to `meta/artists/<id>.mu`                       |
| `as`     | single              | no       | name as printed on **this** release                       |
| `detail` | single              | no       | free-form role qualifier (`"additional"`, `"uncredited"`) |

#### Rules

1. **Placement.** Release credits are `[[credit]]` tables in the release file; track credits are `[[track.credit]]` tables inside the respective `[[track]]`.
2. **No `artist` attribute at entity level.** A release has no `artist = [...]`; participation lives exclusively in credits.
3. **Requirement.** Every release has at least one credit with `role = "main"`.
4. **Order is authoritative.** The file order of credits determines the order of the billing line.
5. **Billing line.** Reconstructed from all credits with `role = "main"`, in file order: for each credit `as` (falling back to the `name` of the referenced artist), joined with `", "`.
6. **Inheritance applies to `main` only.** If a track contains no credit with `role = "main"`, the release's `main` credits apply. All other roles apply exclusively at the level where they appear. There is no merging and no per-role overriding.
7. **`title` stays untouched.** The builder **never** synthesizes credit information into a title. If `(feat. …)` is part of the printed title, it lives in `title`; if it is not, it does not appear in the view either.
8. **Write form.** The canonical write form is the block table (`[[credit]]`). Inline tables are additionally accepted on read.

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
as = "Eloquenz"

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

Reconstructed billing line: `Eloquenz, Hulk Hodn`. Track 2 inherits both `main` credits and adds a `feat` credit.

### 4.6 Tracks

Tracks are `[[track]]` tables inside the release file:

```toml
[[track]]
disc = 2
number = 5
blob = "beef01….flac"
title = "Kink"
```

- `number` (integer): required, **must be ≥ 1**.
- `disc`: optional, defaults to the integer `1`. Accepts an **integer** for numbered discs or a **string** for medium sides (`disc = "A"` for vinyl). An integer `disc` must be ≥ 1.
- Ordering: by `(disc, number)`. Integer discs sort numerically and always sort **before** string discs; string discs sort by NFC code point. `number` is always compared numerically. The file order of the `[[track]]` tables is **not authoritative**.
- `number` is unique per `disc`.

Track attributes:

| Attribute     | Cardinality   | Required       | Meaning                                |
|---------------|---------------|----------------|----------------------------------------|
| `number`      | single (int)  | yes            | track number                           |
| `disc`        | single (int or string) | no (default 1) | disc number, or medium side for vinyl  |
| `blob`        | single        | yes            | reference to the audio file            |
| `title`       | single        | yes            | track name, as printed                 |
| `duration`    | single (int)  | no             | length in seconds                      |
| `isrc`        | single        | no             |                                        |
| `origin-path` | single        | no             | original path of the file (section 4.8)|

Participating artists are stored in `[[track.credit]]` tables (section 4.5).

### 4.7 Schema

**Artist** (`meta/artists/<id>.mu`)

| Attribute | Cardinality | Required |
|-----------|-------------|----------|
| `name`    | single      | yes      |

**Release** (`meta/releases/<id>.mu`)

| Attribute                    | Cardinality                           | Required                       |
|------------------------------|---------------------------------------|--------------------------------|
| `title`                      | single                                | yes                            |
| `credit`                     | credit tables                         | yes (≥ 1 with `role = "main"`) |
| `asset`                      | asset tables                          | no                             |
| `origin-dir`                 | single                                | no                             |

**Asset** (`[[asset]]` tables on a release)

| Attribute     | Cardinality       | Required | Meaning                                  |
|---------------|-------------------|----------|------------------------------------------|
| `kind`        | single            | yes      | asset category, see vocabulary below     |
| `blob`        | single (ref blob) | yes      | reference to the file in the store       |
| `title`       | single            | no       | display name, e.g. `"Booklet page 3"`    |
| `origin-path` | single            | no       | original path of the file (section 4.8)  |

Kind vocabulary: `cover-front`, `log`, `booklet`, `scan`, `cue`, `other`. The vocabulary is **open**; unknown kinds are valid and preserved verbatim.

Cover art is an asset with `kind = "cover-front"`. A release may carry at most one such asset. A release lacking one has no cover.

Unknown attributes are **allowed** and preserved verbatim on every entity. [DESIGN.md](DESIGN.md) lists conventional attributes that tools may read and write — they are not normative and a collection is valid without them.

### 4.8 Origin names

Content addressing keeps a file's bytes and discards its name (section 3.1). For an artefact that arrived **as a whole** — a purchased download, an archived bundle, any directory whose files refer to each other by name — the names are not noise. Two optional attributes record them:

- `origin-dir` on the release: the name of the directory the release arrived as.
- `origin-path` beside a blob or asset reference: that file's path within the directory, relative to it, `/` as separator.

`origin-dir` **must** be a portable name (section 4.1). Each segment of an `origin-path`, split at `/`, **must** be a portable name. A path therefore never begins or ends with `/` and never contains `//`.

`origin-path` is **not a reference** and takes no part in resolution: the store path comes from the hash in the `blob` value. A wrong `origin-path` has no effect beyond misplacing the file in a view.

Within one release, `origin-path` values are unique, compared after NFC normalization **and** case folding. The two attributes are independent and either may stand alone.

### 4.9 Complete example

Attributes beyond the schema — `type`, `release-year-original`, `source-medium`, `bit-depth`, `sample-rate`, `notes` — are conventional ([DESIGN.md](DESIGN.md)).

```toml
title = "Good Lies"
type = "album"
release-year-original = "2023"
source-medium = "web"
origin-dir = "Overmono - Good Lies (2023) [FLAC]"
bit-depth = 16
sample-rate = 44100

notes = """
Ripped 2023-04-01.
AccurateRip ok.
"""

[[credit]]
role = "main"
artist = "overmono"

[[asset]]
kind = "cover-front"
blob = "3f0a91….jpg"
origin-path = "artwork/front.jpg"

[[asset]]
kind = "log"
blob = "1a2b3c….txt"

[[asset]]
kind = "booklet"
blob = "b3c4d5….pdf"
title = "Booklet page 3"

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

## 5. Views

A view is a directory tree of relative symlinks into the store, reproducible from `meta/` + `store/` and disposable at any time. Views contain no information that is not in `meta/` and are never read — code never resolves through them.

The layout, naming and collision rules of specific views are a decision of each implementation. The reference implementation documents its views in [README.md](README.md).

## 6. Git integration

Only `meta/` is versioned. `.gitignore` in the collection root:

```
/store/
/views/
/meta/.lock
.DS_Store
```

`/meta/.lock` is a scratch entry the reference implementation places under `meta/` (section 4.0); a tool using a different name adds its own line.

To keep the LF line endings of section 4 stable across platforms, add a `.gitattributes` in the collection root:

```
*.mu text eol=lf
```

The repository should be configured for precomposed Unicode, matching the NFC rule in section 4.1:

```sh
git config core.precomposeunicode true
```

Diffs are meaningful because a value change shows up as a one-line change:

```
diff --git a/meta/releases/b27e….mu b/meta/releases/b27e….mu
-title = "Good Lise"
+title = "Good Lies"
```