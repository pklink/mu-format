# mu format — specification

**Version 1.0.0 — Draft.**

This document is the **normative specification** of the mu content-addressed music collection. It defines the on-disk format only: what a valid collection looks like, and how the layers relate.

It is deliberately implementation-neutral. It prescribes no programming language, no libraries and no command-line interface. For the reference implementation of the `mu` tool see [IMPLEMENTATION.md](IMPLEMENTATION.md).

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
2. **Entities have stable IDs.** Releases and artists carry an opaque identifier. Names are attributes, not keys.
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
│   ├── .lock                                  # write lock, not in git
│   ├── .mu                                    # collection format version file
│   ├── artists/
│   │   └── 9f2b4a1d-6c31-4f8e-9a02-1d7c4b5e8a90.mu
│   │
│   └── releases/
│       └── b27e3c80-5d44-4a1f-8f6b-2e9c07a3d115.mu
│
└── views/
    ├── by-artist/
    │   └── Overmono/
    │       └── Good Lies [2023]/
    │           ├── 01 Feeling Plain.flac -> ../../../../store/ab/abcd3f…
    │           ├── cover.jpg             -> ../../../../store/3f/3f0a91…
    │           └── log.txt               -> ../../../../store/1a/1a2b3c…
    ├── by-credit/
    │   └── feat/
    │       └── Anz/
    │           └── Overmono - Good Lies -> ../../../by-artist/Overmono/Good Lies [2023]
    ├── by-release-year-original/
    │   └── 2023/
    │       └── Overmono - Good Lies -> ../../by-artist/Overmono/Good Lies [2023]
    └── by-source-medium/
        └── vinyl/…
```

The collection root is the directory containing `meta/.mu` (and the `store/` and `meta/` subdirectories).

## 3. Store

### 3.1 Addressing

- Hash: **SHA-256** over the unmodified file content, hex, **lowercase**, 64 characters.
- The hash addresses **the file content only**. Two identical rips of the same CD produce the same blob, regardless of filenames or tags.

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
- The names `.mu` and `.lock` are **reserved** directly under `meta/`; they must not be used as entity filenames.

`meta/.lock` is the **write lock**. A tool that modifies `meta/` must hold it exclusively for the duration of the modification; readers do not take it. It exists so that two writers cannot interleave, which would leave `meta/` in a state neither of them wrote.

The lock is not part of the collection's state: it carries no content that anything interprets, it is never versioned (section 6), and a collection in which the file is absent is valid — the file exists only while, or because, someone has written. Deleting it while no writer is running has no effect.

The locking mechanism is **not specified**, for the same reason as in section 3.4: whether a tool uses an advisory lock on the file, its exclusive creation, or something else is its own business, as long as writers of the same collection exclude one another.

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

An identifier **should** be opaque and randomly generated; **UUIDv4 or UUIDv7 are recommended**. The reason is section 6: independent writers and git branches create entities without coordination, and merging two colliding identifiers would silently fuse two distinct entities — a failure that does not surface as a merge conflict. A name-derived identifier also defeats principle 2 of section 1: it invites renaming the file when the name changes, which breaks every reference to it.

An identifier is **never reused** for a different entity. Renaming an entity file changes the entity's identity and invalidates every reference to it (section 4.5).

### 4.2 Value conventions

- A value is an **integer** when it is a count, or a quantity in a fixed implied unit that admits exactly one spelling: `track.number`, `track.duration` (seconds), `bit-depth` (bits), `sample-rate` (hertz). The point is that such a fact must not be expressible in two ways — with an integer the TOML parser enforces the canonical form, whereas a string would accept `"44100"`, `"44.1 kHz"` and `"44,1kHz"` as three distinct values for one sample rate.
- All other attribute values are **strings**, apart from flags. Descriptive values stay strings even when they look numeric: `release-year-original` and `release-year-medium` (incomplete years such as `"197?"` occur in practice, and the value is used verbatim as a path segment, section 5.4), `bitrate` (VBR presets such as `"V0"`, averages such as `"~245"`, or `"lossless"`) and `channel-mode`.
- **Flag** = boolean `true` (`is-group = true`).
- **Dual-typed**: `track.disc` is the only attribute that accepts two types — an integer for numbered discs, a string for medium sides (`"A"`, `"B"`). Section 4.7.
- **Multiple value** = string array, inherently ordered (`member = ["id1", "id2"]`). There is no ordered/unordered distinction; arrays are always ordered.
- **Multi-line value** = TOML multi-line string (`notes = """ … """`).
- **Credit** = table (`[[credit]]`, `[[track.credit]]`), section 4.6. Credits are the only structure that is neither scalar nor array.
- References are strings without any path component (section 4.5).

### 4.3 Unicode normalization

String values are normalized to NFC on write and normalized to NFC again on read. The repository must have `core.precomposeunicode=true` (section 6).

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

### 4.6 Credits and roles

Artist participation is expressed through **credits**. A credit is a TOML table:

| Field    | Cardinality         | Required | Meaning                                                   |
|----------|---------------------|----------|-----------------------------------------------------------|
| `role`   | single              | yes      | role, lowercase, words separated by `-`                   |
| `artist` | single (ref artist) | yes      | reference to `meta/artists/<id>.mu`                       |
| `as`     | single              | no       | name as printed on **this** release                       |
| `join`   | single              | no       | join phrase that follows **after** this name              |
| `detail` | single              | no       | free-form role qualifier (`"additional"`, `"uncredited"`) |

A credit therefore bridges two layers: `artist` is the entity (queryable, linkable), while `as` and `join` record how the participation is actually printed on the release.

#### Rules

1. **Placement.** Release credits are `[[credit]]` tables in the release file; track credits are `[[track.credit]]` tables inside the respective `[[track]]`.
2. **No `artist` attribute at entity level.** A release has no `artist = [...]`; participation lives exclusively in credits.
3. **Requirement.** Every release has at least one credit with `role = "main"`.
4. **Order is authoritative.** The file order of credits determines the order of the billing line. The builder does **not** reorder credits (unlike `[[track]]`, section 4.7).
5. **Billing line.** It is reconstructed from all credits with `role = "main"`, in file order: for each credit `as` (falling back to the `name` of the referenced artist), followed by `join` if present. If `join` is absent and a further `main` credit follows, `", "` is used. The `join` of the last `main` credit is ignored.
6. **Inheritance applies to `main` only.** If a track contains no credit with `role = "main"`, the release's `main` credits apply. All other roles apply exclusively at the level where they appear: release credits describe the release as a whole, track credits describe the track. There is no merging, no per-role overriding, and no empty list to disable an inherited role.
7. **`join` only on `main`.** On other roles it is meaningless and should not be used.
8. **`title` stays untouched.** The builder **never** synthesizes credit information into a title. If `(feat. …)` is part of the printed title, it lives in `title`; if it is not, it does not appear in the view either. Credits are additional information, never a replacement.
9. **Write form.** The canonical write form is the block table (`[[credit]]`). Inline tables are additionally accepted on read, since TOML defines them as equivalent.

#### Role vocabulary V1

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
artist = "3d1e8f02-4a77-4c19-b8d3-51ac9e6f2b04"
as = "Eloquenz"                                   # as printed
join = " & "

[[credit]]
role = "main"
artist = "8b42c711-9e05-4d3a-a6f8-0c14bd7e3392"

[[credit]]
role = "producer"
artist = "8b42c711-9e05-4d3a-a6f8-0c14bd7e3392"
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
artist = "c907a4b1-2f68-4e50-9d17-b3ea85c6014f"
```

Reconstructed billing line: `Eloquenz & Hulk Hodn`. Track 2 inherits both `main` credits and adds a `feat` credit; its `title` stays exactly as printed on the record.

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

| Attribute  | Cardinality            | Required       | Meaning                               |
|------------|------------------------|----------------|---------------------------------------|
| `number`   | single (int)           | yes            | track number                          |
| `disc`     | single (int or string) | no (default 1) | disc number, or medium side for vinyl |
| `blob`     | single                 | yes            | reference to the audio file           |
| `title`    | single                 | yes            | track name, as printed                |
| `duration` | single (int)           | no             | length in seconds                     |
| `isrc`     | single                 | no             |                                       |

Participating artists are not stored in an attribute but in `[[track.credit]]` tables (section 4.6).

### 4.8 Schema V1

**Artist** (`meta/artists/<id>.mu`)

| Attribute           | Cardinality           | Required |
|---------------------|-----------------------|----------|
| `name`              | single                | yes      |
| `alias`             | multiple              | no       |
| `is-group`          | flag                  | no       |
| `member`            | multiple (ref artist) | no       |
| `notes`             | single (multi-line)   | no       |
| `sort-name`         | single                | no       |
| `discogs-artist-id` | single                | no       |

**Release** (`meta/releases/<id>.mu`)

| Attribute                                             | Cardinality                                             | Required                       |
|-------------------------------------------------------|---------------------------------------------------------|--------------------------------|
| `title`                                               | single                                                  | yes                            |
| `credit`                                              | credit tables                                           | yes (≥ 1 with `role = "main"`) |
| `type`                                                | single (`album`, `ep`, `single`, `compilation`, `live`) | no                             |
| `release-year-original`                               | single                                                  | no                             |
| `release-year-medium`                                 | single                                                  | no                             |
| `source-medium`                                       | single (`cd`, `vinyl`, `file`, `web`, `tape`)           | no                             |
| `source-store`                                        | single                                                  | no                             |
| `rip-result`                                          | single                                                  | no                             |
| `bit-depth`                                           | single (int)                                            | no                             |
| `sample-rate`                                         | single (int)                                            | no                             |
| `bitrate`, `channel-mode`                             | single                                                  | no                             |
| `discogs-master-id`, `discogs-release-id`             | single                                                  | no                             |
| `cover-front`, `cover-back`                           | single (ref blob)                                       | no                             |
| `asset`                                               | asset tables                                            | no                             |
| `notes`                                               | single (multi-line)                                     | no                             |

`release-year-original` is the year the release was **first** published; `release-year-medium` is the year of the edition actually held. `release-year-medium` is set **only if it differs** from `release-year-original` — a first pressing carries `release-year-original` alone. Views derive the edition year from the two (section 5.3).

`bit-depth` and `sample-rate` are integers and **must be ≥ 1**. `sample-rate` is given in hertz (`44100`, not `44.1`), `bit-depth` in bits. `bitrate` stays a string because it is not always a number: VBR encoders record presets (`"V0"`) or averages (`"~245"`), and a lossless source has no meaningful single value.

The value lists given for `type` and `source-medium` are **open vocabularies**, like `role` (section 4.6) and `asset.kind`. Unknown values are valid and are preserved verbatim; a tool may warn but must not reject them.

Unknown attributes are **allowed** and preserved verbatim; they are ignored when sorting for deterministic builds.

#### Assets

Everything that belongs to a release but is neither audio nor cover art — rip logs, booklets, scans, cue sheets — is referenced through repeatable `[[asset]]` tables:

```toml
[[asset]]
kind = "log"
blob = "1a2b3c….txt"
```

| Attribute | Cardinality       | Required | Meaning                                  |
|-----------|-------------------|----------|------------------------------------------|
| `kind`    | single            | yes      | asset category, see vocabulary below     |
| `blob`    | single (ref blob) | yes      | reference to the file in the store       |
| `title`   | single            | no       | display name, e.g. `"Booklet page 3"`; becomes the view filename (section 5.4) |

Kind vocabulary V1: `log`, `booklet`, `scan`, `cue`, `other`. The vocabulary is **open**; unknown kinds are valid and preserved verbatim (same handling as `role`, section 4.6).

Assets attach to the release, not to individual tracks: rip logs and booklets describe the medium as a whole. They are materialized in `views/` alongside the tracks of the release (section 5.4).

The file order of the `[[asset]]` tables is **authoritative**: where two assets would produce the same view filename, it decides which of them keeps it (section 5.4). Assets behave like credits here (section 4.6, rule 4), not like tracks, whose file order the builder ignores.

#### Example release (multi-CD)

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
artist = "9f2b4a1d-6c31-4f8e-9a02-1d7c4b5e8a90"

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
artist = "7a11c3d8-0b52-4e67-9f14-a8d206c3b571"
```

#### Example artist

```toml
name = "Overmono"
is-group = true
member = ["4c8e…uuid…", "7a11…uuid…"]
sort-name = "Overmono"
discogs-artist-id = "1234567"
```

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

Two releases by the same artist with the same title produce the same view path. Resolved in this order until unique:

1. `Title`
2. `Title [<edition-year>]`
3. `Title [<edition-year>, <source-medium>]`
4. `Title [<edition-year>, <source-medium>] (<id-prefix>)`

`<edition-year>` is the **derived edition year**: `release-year-medium` if present, otherwise `release-year-original`.

`<id-prefix>` is the shortest prefix of the release identifier that is at least **8 codepoints** long — or the whole identifier, if it is shorter — never splitting a codepoint, and long enough that the prefixes of all releases colliding at this step in this directory are pairwise distinct under the comparison of section 4.1, rule 5. One length is used for the entire colliding group, so all its entries carry a prefix of the same length.

Step 4 is guaranteed unique: the identifiers of one directory are pairwise distinct, an identifier is at most 200 bytes and section 5.2 leaves it unchanged (section 4.1), so the full identifier is always an available fallback. Colliding releases are sorted by identifier in NFC code point order and processed in that order.

#### Collisions in the other views

`by-credit`, `by-release-year-original` and `by-source-medium` key on `<billing> - <title>`, which can collide independently of `by-artist` — two different releases may share a billing line, a title and a year. Resolved in two steps:

1. `<billing> - <title>`
2. `<billing> - <title> (<id-prefix>)`

Step 2 is guaranteed unique, for the reason given above, and `<id-prefix>` is formed the same way — over the releases colliding at this step in this directory. As above, colliding releases are sorted by identifier in NFC code point order and processed in that order. This ladder is independent of the one above; the `by-artist` name is not reused here.

The ladder is applied **per directory**, not per view: the collision scope is the single year directory, medium directory or `<role>/<artist-name>` directory in which the entry is created. Two releases that collide under one role but not under another therefore carry the suffix only where it is needed.

### 5.4 Standard views

| View                       | Structure                                                                                                                                                                  |
|----------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `by-artist`                | `by-artist/<artist-name>/<release-name>/<NN Title.ext>` — the only view with symlinks directly into the store; a release appears under **every** one of its `main` artists |
| `by-credit`                | `by-credit/<role>/<artist-name>/<billing> - <title>` → symlink to the `by-artist` directory                                                                                |
| `by-release-year-original` | `by-release-year-original/<release-year-original>/<billing> - <title>` → symlink to the `by-artist` directory                                                              |
| `by-source-medium`         | `by-source-medium/<source-medium>/<billing> - <title>` → symlink to the `by-artist` directory                                                                              |

Only `by-artist` creates track symlinks; all other views link to its directories.

#### Credits in views

- **Grouping in `by-artist` is based exclusively on credits with `role = "main"`.** An artist who participates only as `feat`, `remixer`, `producer` or similar does **not** get their own `by-artist` directory; that participation is carried by `by-credit` instead.
- `by-artist/<artist-name>` and `by-credit/<role>/<artist-name>` use the `name` attribute of the **artist entity**, not `as`. The directory represents the artist, not a single billing.
- `<billing>` in `by-credit`, `by-release-year-original` and `by-source-medium` is the **reconstructed billing line** of the release (section 4.6, rule 5), i.e. including `as` names and `join` phrases — not a join of entity names.
- A release lacking the attribute a view is keyed on is **omitted from that view** — no `unknown/` bucket, no `_` placeholder. It stays reachable through `by-artist/`.

#### `by-credit`

`by-credit` is the counterpart to `by-artist`: it makes every participation reachable that does not form a billing line.

1. **Scope.** `by-credit` covers every credit whose `role` is **not** `main`, at release level (`[[credit]]`) and at track level (`[[track.credit]]`) alike. `main` is excluded because `by-artist` already covers it; including it would duplicate that view under a second name.
2. **Granularity.** The entry is the **release**, never the individual track. A track credit therefore places the whole release under `<role>/<artist-name>/`. A release appears exactly **once** per `(role, artist)` pair, however many of its tracks carry that credit and whether the credit sits at release or at track level.
3. **Multiple roles.** An artist credited on one release under two roles gets an entry under each of them.
4. **Inheritance does not apply.** Section 4.6, rule 6 inherits `main` credits only, and `main` is out of scope here — a track never inherits a `feat` or `remixer` credit, so nothing is materialized that is not written down.
5. **`<role>`** is the credit's `role` value, sanitized per section 5.2. Since the vocabulary is open (section 4.6), an unknown role yields a directory just like a known one.
6. **Ordering.** Roles are iterated by NFC code point, artists by NFC-normalized `name`, releases by identifier (section 5.6).

#### Track filenames

`<sortkey> <sanitized track-title>.<ext of the blob reference>`, e.g. `01 Feeling Plain.flac`, or `2-05 Kink.m4a` for multi-disc releases. If the reference carries no extension (section 4.5), the name is written without a suffix and **without** a trailing dot: `01 Feeling Plain`. The sort key is derived by the builder from `disc`/`number` (`[<disc>-]<number>`, number zero-padded to **at least** two digits — a `number` of 100 or more keeps all its digits and is not truncated). A string `disc` is inserted verbatim after sanitization (section 5.2), so a vinyl side yields `A-01 Feeling Plain.flac`; zero-padding applies to `number` only.

`title` is taken **verbatim**. The builder never appends `(feat. …)` or any other credit information (section 4.6, rule 8).

Compilation exception: if a track's `main` credits differ from the release's, the filename becomes

`<sortkey> <billing of the track> - <sanitized track-title>.<ext>`

e.g. `03 Kuhn Fu - Waffle House.m4a`. Without that prefix a compilation directory would be unusable. If the track inherits the release credits, the prefix is omitted.

#### Asset filenames

Assets (section 4.8) are materialized in the `by-artist` release directory, flat, beside the tracks:

`<sanitized asset-title, or kind if title is absent>.<ext of the blob reference>`

e.g. `Booklet page 3.jpg` for an asset carrying a `title`, and `log.txt` for one that does not. The extension rule is the one for tracks: it comes from the blob reference, and a reference without an extension yields a name without a suffix and **without** a trailing dot (section 4.5). No sort key is prefixed — assets have no position.

There is no `assets/` subdirectory and no grouping by `kind`. A rip log sits next to the tracks it describes, which is where a player, a tag editor and a human all look for it.

Because the name is derived from a free-form value, it can collide — with another asset of the same `kind`, with cover art, or with a track. The colliding asset gets ` (<n>)` appended before the extension, `<n>` counting up from `2` until the name is free:

```
log.txt
log (2).txt
log (3).txt
```

The counter is unbounded, so a free name is always reached. Two rules make the outcome unambiguous:

1. **An asset never displaces a track or cover art.** In a collision with either, the asset is the one that gets the suffix.
2. **Among assets, file order decides.** The asset that appears earlier in the release file keeps the undecorated name; later ones are suffixed in file order (section 4.8).

Assets appear in `by-artist` only. The other views link to its directories and therefore carry the assets with them.

### 5.5 Disposability

`views/` is **optional**. A collection without it is valid, and the tree may be deleted or regenerated at any time without loss — it carries nothing that is not already derivable from `meta/` and `store/` (section 5.1), and nothing ever resolves through it.

How a builder produces the tree, and whether it rebuilds from scratch or updates an existing tree in place, is **not specified**. Section 5.6 constrains the result, not the procedure: any builder whose output satisfies it conforms.

### 5.6 Determinism

Building twice against the same meta state must produce byte-identical trees. All directory iteration is explicitly sorted (by identifier or by NFC-normalized name, in NFC code point order); directory-listing order is never inherited from the filesystem.

Credits and assets are exempt: their order comes from the file (section 4.6, rule 4; section 4.8) and is therefore already deterministic.

## 6. Git integration

Only `meta/` is versioned. `.gitignore` in the collection root:

```
/store/
/views/
/meta/.lock
.DS_Store
```

To keep the LF line endings of section 4 stable across platforms, add a `.gitattributes` in the collection root:

```
*.mu text eol=lf
```

The repository must be configured for precomposed Unicode, matching the NFC rule in section 4.3:

```sh
git config core.precomposeunicode true
```

Diffs are meaningful because a value change shows up as a one-line change to the entity file:

```
diff --git a/meta/releases/b27e….mu b/meta/releases/b27e….mu
-title = "Good Lise"
+title = "Good Lies"
```
