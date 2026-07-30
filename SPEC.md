# mu format — specification

This document is the **normative specification** of the mu content-addressed music collection. It defines the on-disk format only: what a valid collection looks like, and how the layers relate.

It is deliberately implementation-neutral. It prescribes no programming language, no libraries and no command-line interface. For the reference implementation of the `mu` tool see [IMPLEMENTATION.md](IMPLEMENTATION.md).

The key words **must**, **must not**, **should** and **may** are used in their usual normative sense.

## 1. Model

Three layers:

| Layer    | Purpose                                 | Mutable                           | In git |
|----------|-----------------------------------------|-----------------------------------|--------|
| `store/` | media files, addressed by their content | never (append-only)               | no     |
| `meta/`  | metadata, references, structure         | yes, by hand or via a tool        | yes    |
| `views/` | symlink trees for browsing and playback | generated, disposable at any time | no     |

### Core principles

1. **Content determines identity.** A media file is addressed by the hash of its content, not by its path.
2. **Entities have stable IDs.** Releases and artists are UUIDs. Names are attributes, not keys.
3. **Nothing is ever written into media files.** No tags, no renaming, no conversion. The store is bit-identical to what was imported.
4. **Views are pure functions of `meta` + `store`.** Every view is reproducible from the other two layers.
5. **A release has no location.** It has attributes. Where it shows up is the view's decision.

## 2. Directory layout

```
music/
├── .gitignore
├── store/
│   ├── .tmp/                                  # import staging, always empty at rest
│   ├── .trash/                                # blobs set aside by garbage collection
│   ├── ab/
│   │   ├── abcd3f…64hex….flac
│   │   └── ab77e1…64hex….m4a
│   └── 3f/
│       └── 3f0a91…64hex….jpg
│
├── meta/
│   ├── .lock                                  # write lock, not in git
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
    │           ├── 01 Feeling Plain.flac -> ../../../../store/ab/abcd3f….flac
    │           └── cover.jpg             -> ../../../../store/3f/3f0a91….jpg
    ├── by-year/
    │   └── 2023/
    │       └── Overmono - Good Lies -> ../../by-artist/Overmono/Good Lies [2023]
    └── by-medium/
        └── vinyl/…
```

The collection root is the directory containing both `store/` and `meta/`.

## 3. Store

### 3.1 Addressing

- Hash: **SHA-256** over the unmodified file content, hex, **lowercase**, 64 characters.
- The hash addresses **the file content only**. Two identical rips of the same CD produce the same blob, regardless of filenames or tags.

### 3.2 Layout

```
store/<hash[0:2]>/<hash>.<ext>
```

- Sharded by the first two hex characters → 256 buckets.
- `<ext>`: extension of the original file, lowercase, only if it matches `[a-z0-9]{1,8}`; otherwise omitted. The extension is **not part of the content identity** — the hash alone identifies the content — but it **is** part of the store path and of every reference to the blob (section 4.5). It is therefore fixed at import time and must never be changed afterwards.
- Filename length: 64 + 1 + at most 8 = 73 characters.

### 3.3 Immutability

- Blobs are set to `0444` (read-only) after import.
- Blobs are **never** deleted. Garbage collection may only move them to `store/.trash/`.
- An existing blob is **never** overwritten. If the target path already exists, the content is identical by definition → the import is a no-op.
- Because the extension is part of the path, identical content imported under two different extensions yields two store entries. This is accepted: the extension is part of the reference, so both must remain resolvable.

### 3.4 Atomic import

Taking a file into the store must be atomic: an observer must never see a partially written blob at its final path. Per file:

1. Copy the source into `store/.tmp/<random>`, hashing it in the same pass.
2. Compute the target path `store/<h[0:2]>/<h>.<ext>`.
3. If the target exists → discard the temp file, count it as a dedup, done.
4. Otherwise: create the target directory and **rename** the temp file onto the target path atomically.
5. Set `0444`.

If the operation aborts before step 4, all that remains is garbage in `.tmp/`, which the next run cleans up. `store/.tmp/` and the store live on the same filesystem so that the rename in step 4 is atomic.

### 3.5 What belongs in the store

**All** binary content: audio files, cover art, rip logs, scans, booklets. Cover art is content-addressed and referenced just like tracks (`cover-front = "<hash>.jpg"`). Everything that is neither audio nor cover art is referenced through `[[asset]]` tables (section 4.8). `meta/` contains no binary data.

## 4. Meta

Every entity is **one TOML file**, named `<uuid>.mu`, encoded as UTF-8 without BOM. The entity type is determined by the path (`artists/` vs. `releases/`).

### 4.1 Entities

| Entity  | Path                      |
|---------|---------------------------|
| Artist  | `meta/artists/<uuid>.mu`  |
| Release | `meta/releases/<uuid>.mu` |

Tracks are not separate files but `[[track]]` tables inside the release file (section 4.7).

`<uuid>`: UUID v4, lowercase, with hyphens (36 characters). The filename stem is the entity's identity; the UUID is not repeated inside the file.

### 4.2 Value conventions

- All attribute values are **strings** (`title = "Good Lies"`, `release-year = "2023"`), except flags and the small set of **integer-typed** attributes explicitly marked in the schema (`track.number`, `track.duration`). Everything descriptive stays a string, including `release-year`, `bit-depth`, `sample-rate` and `bitrate`.
- **Flag** = boolean `true` (`is-group = true`).
- **Dual-typed**: `track.disc` is the only attribute that accepts two types — an integer for numbered discs, a string for medium sides (`"A"`, `"B"`). Section 4.7.
- **Multiple value** = string array, inherently ordered (`member = ["uuid1", "uuid2"]`). There is no ordered/unordered distinction; arrays are always ordered.
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
| Multiple value   | `member = ["uuid1", "uuid2"]`     |
| Multi-line value | `notes = """` … `"""`             |
| Credit           | `[[credit]]` / `[[track.credit]]` |

An attribute is either scalar or array; mixing the two is invalid.

### 4.5 References

All references are string values, without a path component:

| Reference type | Notation                                                | Resolution                       |
|----------------|---------------------------------------------------------|----------------------------------|
| to an artist   | `artist = "<uuid>"` (only inside a credit, section 4.6) | `meta/artists/<uuid>.mu`         |
| to a blob      | `blob = "<hash>.<ext>"`                                 | `store/<hash[0:2]>/<hash>.<ext>` |
| to cover art   | `cover-front = "<hash>.<ext>"`                          | same as blob                     |

Blob references carry hash **and** extension so the store path is computable without scanning a directory. No path ever appears in a reference. `asset.blob` (section 4.8) uses the same notation and the same resolution.

### 4.6 Credits and roles

Artist participation is expressed through **credits**. A credit is a TOML table:

| Field    | Cardinality         | Required | Meaning                                                   |
|----------|---------------------|----------|-----------------------------------------------------------|
| `role`   | single              | yes      | role, lowercase, words separated by `-`                   |
| `artist` | single (ref artist) | yes      | reference to `meta/artists/<uuid>.mu`                     |
| `as`     | single              | no       | name as printed on **this** release                       |
| `join`   | single              | no       | join phrase that follows **after** this name              |
| `detail` | single              | no       | free-form role qualifier (`"additional"`, `"uncredited"`) |

A credit therefore bridges two layers: `artist` is the entity (queryable, linkable), while `as` and `join` record how the participation is actually printed on the release.

#### Rules

1. **Placement.** Release credits are `[[credit]]` tables in the release file; track credits are `[[track.credit]]` tables inside the respective `[[track]]`.
2. **No `artist` attribute at entity level.** A release has no `artist = [...]`; participation lives exclusively in credits.
3. **Requirement.** Every release has at least one credit with `role = "main"`.
4. **Order is authoritative.** The file order of credits determines the order of the billing line. The builder does **not** reorder credits (unlike `[[track]]`, section 4.7).
5. **Billing line.** It is reconstructed from all credits with `role = "main"`, in file order: for each credit `as` (falling back to the `name` of the referenced artist), followed by `join` if present. The `join` of the last `main` credit is ignored.
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
release-year = "2015"
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

- `number` (integer): required. `disc`: optional, defaults to the integer `1`. `disc` accepts an **integer** for numbered discs or a **string** for medium sides (`disc = "A"` for vinyl).
- Ordering: by `(disc, number)`. Integer discs sort numerically and always sort **before** string discs; string discs sort by NFC code point. `number` is always compared numerically. The file order of the `[[track]]` tables is **not authoritative** — the builder sorts them itself. (Credits behave the opposite way, section 4.6, rule 4.)
- Position and disc live exclusively in the `disc`/`number` keys; there is no sort-key string.
- `number` is unique per `disc`.

Track attributes:

| Attribute  | Cardinality           | Required       | Meaning                                |
|------------|-----------------------|----------------|----------------------------------------|
| `number`   | single (int)          | yes            | track number                           |
| `disc`     | single (int / string) | no (default 1) | disc number, or medium side for vinyl  |
| `blob`     | single                | yes            | reference to the audio file            |
| `title`    | single                | yes            | track name, as printed                 |
| `duration` | single (int)          | no             | length in seconds                      |
| `isrc`     | single                | no             |                                        |

Participating artists are not stored in an attribute but in `[[track.credit]]` tables (section 4.6).

### 4.8 Schema V1

**Artist** (`meta/artists/<uuid>.mu`)

| Attribute           | Cardinality           | Required |
|---------------------|-----------------------|----------|
| `name`              | single                | yes      |
| `alias`             | multiple              | no       |
| `is-group`          | flag                  | no       |
| `member`            | multiple (ref artist) | no       |
| `sort-name`         | single                | no       |
| `discogs-artist-id` | single                | no       |

**Release** (`meta/releases/<uuid>.mu`)

| Attribute                                             | Cardinality                                             | Required                       |
|-------------------------------------------------------|---------------------------------------------------------|--------------------------------|
| `title`                                               | single                                                  | yes                            |
| `credit`                                              | credit tables                                           | yes (≥ 1 with `role = "main"`) |
| `type`                                                | single (`album`, `ep`, `single`, `compilation`, `live`) | no                             |
| `release-year`                                        | single                                                  | no                             |
| `release-year-medium`                                 | single                                                  | no                             |
| `source-medium`                                       | single (`cd`, `vinyl`, `file`, `web`, `tape`)           | no                             |
| `source-store`                                        | single                                                  | no                             |
| `rip-result`                                          | single                                                  | no                             |
| `bit-depth`, `sample-rate`, `bitrate`, `channel-mode` | single                                                  | no                             |
| `discogs-master-id`, `discogs-release-id`             | single                                                  | no                             |
| `cover-front`, `cover-back`                           | single (ref blob)                                       | no                             |
| `asset`                                               | asset tables                                            | no                             |

Unknown attributes are **allowed** and preserved verbatim.

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
| `title`   | single            | no       | display name, e.g. `"Booklet page 3"`    |

Kind vocabulary V1: `log`, `booklet`, `scan`, `cue`, `other`. The vocabulary is **open**; unknown kinds are valid and preserved verbatim (same handling as `role`, section 4.6).

Assets attach to the release, not to individual tracks: rip logs and booklets describe the medium as a whole.

#### Example release (multi-CD)

```toml
title = "Good Lies"
type = "album"
release-year = "2023"
source-medium = "cd"
bit-depth = "16"
sample-rate = "44100"
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
5. Truncate to 200 bytes (respecting UTF-8 character boundaries, never mid-codepoint).
6. If the result is empty, use `_`.

### 5.3 Collisions

Two releases by the same artist with the same title produce the same view path. Resolved in this order until unique:

1. `Title`
2. `Title [<release-year>]`
3. `Title [<release-year>, <source-medium>]`
4. `Title [<release-year>, <source-medium>] (<uuid[0:8]>)`

Step 4 is guaranteed unique. Colliding releases are sorted by UUID and processed in that order.

### 5.4 Standard views

| View        | Structure                                                                                                                                                                  |
|-------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `by-artist` | `by-artist/<artist-name>/<release-name>/<NN Title.ext>` — the only view with symlinks directly into the store; a release appears under **every** one of its `main` artists |
| `by-year`   | `by-year/<year>/<billing> - <title>` → symlink to the `by-artist` directory                                                                                                |
| `by-medium` | `by-medium/<medium>/<billing> - <title>` → symlink to the `by-artist` directory                                                                                            |

Only `by-artist` creates track symlinks; all other views link to its directories.

#### Credits in views

- **Grouping** is based exclusively on credits with `role = "main"`. An artist who participates only as `feat`, `remixer`, `producer` or similar does **not** get their own `by-artist` directory in V1. A `by-credit/<role>/<artist>/` view is deliberately **not** part of V1.
- `by-artist/<artist-name>` uses the `name` attribute of the **artist entity**, not `as`. The directory represents the artist, not a single billing.
- `<billing>` in `by-year` and `by-medium` is the **reconstructed billing line** of the release (section 4.6, rule 5), i.e. including `as` names and `join` phrases — not a join of entity names.

#### Track filenames

`<sortkey> <sanitized track-title>.<ext of the blob>`, e.g. `01 Feeling Plain.flac`, or `2-05 Kink.m4a` for multi-disc releases. The sort key is derived by the builder from `disc`/`number` (`[<disc>-]<number>`, number zero-padded to two digits). A string `disc` is inserted verbatim after sanitization (section 5.2), so a vinyl side yields `A-01 Feeling Plain.flac`; zero-padding applies to `number` only.

Assets (section 4.8) are **not** materialized in `views/` in V1. They live in the store and are reachable through `meta/` alone.

`title` is taken **verbatim**. The builder never appends `(feat. …)` or any other credit information (section 4.6, rule 8).

Compilation exception: if a track's `main` credits differ from the release's, the filename becomes

`<sortkey> <billing of the track> - <sanitized track-title>.<ext>`

e.g. `03 Kuhn Fu - Waffle House.m4a`. Without that prefix a compilation directory would be unusable. If the track inherits the release credits, the prefix is omitted.

### 5.5 Atomic rebuild

A rebuild must never leave `views/` in a half-written state:

```
1. build views.new/ (completely)
2. views/ → views.old/   (rename, if present)
3. views.new/ → views/   (rename)
4. delete views.old/ recursively
```

An aborted build leaves `views.new/` or `views.old/` behind; both are removed on the next run.

### 5.6 Determinism

Building twice against the same meta state must produce byte-identical trees. All directory iteration is explicitly sorted (by UUID or by NFC-normalized name); directory-listing order is never inherited from the filesystem.

Credits are exempt: their order comes from the file (section 4.6, rule 4) and is therefore already deterministic.

## 6. Git integration

Only `meta/` is versioned. `.gitignore` in the collection root:

```
/store/
/views/
/meta/.lock
.DS_Store
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
