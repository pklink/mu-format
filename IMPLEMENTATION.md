# mu — implementation notes

This document describes the **reference implementation**: the `mu` command-line tool, its commands, and the platform decisions behind them.

The on-disk format itself is defined in [SPEC.md](SPEC.md) and is independent of everything written here. Where this document refers to format rules, SPEC.md is authoritative.

## 1. Platform

| Concern     | Choice                                                           |
|-------------|------------------------------------------------------------------|
| Language    | Java                                                             |
| Build       | Gradle (`build.gradle`)                                          |
| CLI parsing | `info.picocli:picocli:4.7.6`                                     |
| File utils  | `commons-io:commons-io:2.16.1`                                   |
| TOML        | a TOML 1.0 parser, e.g. `org.tomlj:tomlj` (not yet a dependency) |
| Tests       | JUnit 5 + AssertJ                                                |

Mapping onto the JDK. A `SPEC.md §` reference marks a requirement of the format; the remaining rows are decisions of this tool, described in the sections given.

| Requirement                       | Java API                                                  |
|-----------------------------------|-----------------------------------------------------------|
| NFC normalization (SPEC.md §4.3)  | `java.text.Normalizer.normalize(s, Form.NFC)`             |
| SHA-256 (SPEC.md §3.1)            | `MessageDigest.getInstance("SHA-256")`, streamed          |
| atomic publication (SPEC.md §3.4) | `Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE)` |
| view swap (§2.3)                  | `Files.move(…, StandardCopyOption.ATOMIC_MOVE)`           |
| read-only blobs (§2.1)            | `Files.setPosixFilePermissions(…, r--r--r--)`             |
| write lock (§3)                   | `FileChannel.tryLock()` on `meta/.lock`                   |

Only the rows carrying a `SPEC.md §` reference are obligations, and one of them fixes only the goal, not the means. SPEC.md §3.4 requires that a blob become visible as a whole but prescribes no mechanism; this implementation uses a rename, and `ATOMIC_MOVE` requires source and target on the same filesystem. The staging directory `store/.tmp/` therefore lives inside the store, and `views.new/` beside `views/`. Neither name is part of the format, and neither is `meta/.lock`: SPEC.md §3.2 leaves any entry under `store/` that is not a `<hash[0:2]>/<hash>` pair without meaning, SPEC.md §4.0 does the same for entries under `meta/` that are not `.mu`, `artists/` or `releases/`, and SPEC.md §5.5 leaves the build procedure open entirely.

Blob permissions are likewise a tool decision. `0444` is best-effort: on filesystems without POSIX permissions (exFAT, SMB) the call fails and is ignored, since nothing in the format depends on it.

## 2. Commands

```
mu import <path>...        take files into the store, create a meta skeleton
mu build [view]            regenerate views
mu lint [--strict]         check meta consistency
mu verify [--quick]        check store integrity
mu gc [--dry-run]          move unreferenced blobs to store/.trash/
mu migrate <source>        adopt an existing collection
```

Global option: `--root <path>` (default: search upwards for a directory containing `meta/.mu`, the way `git` does; SPEC.md §4.0).

### 2.1 `mu import`

```
mu import ~/rips/Overmono\ -\ Good\ Lies/
```

1. Collect files recursively, classify by extension into audio / image / other.
2. Hash each file while streaming and take it into the store (see below).
3. Generate a release identifier, create `meta/releases/<id>.mu` as TOML with exactly one `[[credit]]` of role `main`. SPEC.md §4.1 accepts a readable or an opaque identifier; this tool generates a UUIDv4, because at this point it has no title it could derive a readable one from — `import` does not read tags (below), and the directory name is not trustworthy enough to become an identity that must never change.
4. Add audio files in filename order as `[[track]]` tables, each with `number`/`disc` (parsed from a leading prefix in the original name such as `01 `, `A1 `, `1-05 `) and a `blob` reference; the remainder of the name becomes `title`. A letter prefix maps to a string `disc` (`A1 ` → `disc = "A"`, `number = 1`), matching SPEC.md §4.7.
5. Reference images named `cover|front|folder` as `cover-front = "<hash>.<ext>"`.
6. Trigger `mu build`.

Options: `--release <id>` (import into an existing release), `--artist <id>` (sets the `main` credit), `--origin` (record the source tree, below), `--dry-run`.

`import` does not read tags from media files.

#### Recording the origin tree

`--origin` fills in what SPEC.md §4.9 defines: the base name of the imported directory becomes `origin-dir`, and every file gets the path it had relative to that directory as `origin-path` — on its `[[track]]` or `[[asset]]` table, or as `cover-front-origin-path` for the image picked in step 5.

It is **off by default**. Most imports are a loose directory whose name carries nothing worth keeping, and a recorded path nobody wants is still a value `lint` has to check and a tree `build` has to materialize. It is worth turning on where the directory *is* the artefact — a release that arrived as a unit, with a playlist or a checksum file that only resolves against the original names.

Two restrictions follow from the format:

- `--origin` takes **exactly one** directory argument. With several paths, or with a file, there is no single directory whose name could become `origin-dir`; that is a usage error (exit 2).
- Every path segment must satisfy §4.9, i.e. survive SPEC.md §5.2 unchanged. If any does not, `import` aborts **before** writing anything and lists every offending path (exit 1). Dropping the offenders and importing the rest would produce exactly the half-tree the option exists to prevent — a checksum file in a tree that is missing two of its files verifies no better than one whose files were renamed. The format does permit a partial tree (§4.9), because a hand-written entity file may legitimately be incomplete; this tool does not produce one.

#### Taking a file into the store

SPEC.md §3.4 states the guarantee — a blob is visible at its final path only as a whole — and leaves the mechanism open. This tool uses copy-and-rename, per file:

1. Copy the source into `store/.tmp/<random>`, hashing it in the same pass.
2. Compute the target path `store/<h[0:2]>/<h>`.
3. If the target exists → discard the temp file, count it as a dedup, done. The content is identical by definition (SPEC.md §3.3), so nothing is overwritten.
4. Otherwise: create the shard directory and **rename** the temp file onto the target path with `ATOMIC_MOVE`.
5. Set `0444`, best-effort.

If the run aborts before step 4, all that remains is garbage in `store/.tmp/`, which the next run of a writing command clears before it starts. Nothing under `store/.tmp/` is ever a blob: it is not reachable by the path formula, so an interrupted import cannot produce a resolvable but incomplete blob.

`store/.tmp/` is this tool's staging directory, not a format requirement. Another implementation may stage elsewhere — but then `mu` will not clean up after it, and it will not clean up after `mu`.

#### Deriving the extension

The store path is the bare hash (SPEC.md §3.2); the extension appears only in the reference, where it is a rendering hint (SPEC.md §4.5). The format leaves its derivation to the tool. `import` takes it from the source file's base name:

- the substring after the **last** `.`, lower-cased with ASCII rules, used only if it matches `[a-z0-9]{1,8}`;
- a leading dot does not start an extension (`.gitignore` has none);
- if nothing qualifies, the reference is written as the bare hash and the view file gets no suffix.

This is a heuristic, not a guarantee: it trusts the source filename. Because the result lives in `meta/`, a wrong extension is fixed by editing the entity file — no re-import, no change to the store.

> **Open question.** Without `--artist` there is no artist identifier for the required `main` credit, so the result fails `mu lint`. It is not yet decided whether `import` creates an artist stub (and from which name) or whether `--artist` becomes mandatory.

### 2.2 `mu lint`

Validates `meta/` against SPEC.md and classifies each finding. Checks, in this order:

| Check                                                                             | Severity                       |
|-----------------------------------------------------------------------------------|--------------------------------|
| `meta/.mu` present, `format` an integer not higher than implemented              | error                          |
| entity identifier valid per §4.1 (length, NFC, forbidden characters, edges)      | error                          |
| entity identifiers unique per directory under NFC and case folding (§4.1)        | error                          |
| entity file is valid TOML                                                         | error                          |
| required attributes present (`name`, `title`, `blob`, `number`)                   | error                          |
| cardinality respected (no `title` as an array)                                    | error                          |
| `disc`/`number` well-typed (`number` integer, `disc` integer or non-empty string) | error                          |
| `number` ≥ 1, and an integer `disc` ≥ 1                                          | error                          |
| `number` unique per disc                                                          | error                          |
| `bit-depth` and `sample-rate` integers ≥ 1 (§4.2)                                 | error                          |
| release has ≥ 1 credit with `role = "main"`                                       | error                          |
| `role` and `artist` present in every credit                                       | error                          |
| `credit.artist` points to an existing artist identifier                          | error                          |
| blob reference exists in the store                                                | error                          |
| `kind` and `blob` present in every asset                                          | error                          |
| `asset.blob` exists in the store                                                  | error                          |
| `origin-dir` and every `origin-path` segment valid per §4.9                       | error                          |
| `origin-path` unique per release under NFC and case folding (§4.9)                | error                          |
| `join` on a role other than `main`                                                | warning                        |
| duplicate `(role, artist)` at the same level                                      | warning                        |
| string values NFC-normalized (including `as`)                                     | warning (fixable with `--fix`) |
| release without tracks                                                            | warning                        |
| `release-year-medium` equal to `release-year-original` (redundant, §4.8)          | warning (fixable with `--fix`) |
| `origin-dir` set, but a track, asset or cover without `origin-path`               | warning                        |
| `origin-path` set on a release without `origin-dir`                               | warning                        |
| attribute names not in the schema                                                 | notice                         |
| `role`, `asset.kind`, `type`, `source-medium` not in the listed vocabulary        | notice                         |
| blob reference extension not matching `[a-z0-9]{1,8}` (§4.5)                      | notice                         |

`--strict` turns warnings into errors.

The case-folded uniqueness check only ever fires on a case-sensitive filesystem: elsewhere the two files are one, so the collection cannot reach that state locally. It is worth running anyway, because such a pair can be created on ext4 and committed, and the repository is then unusable on macOS or Windows — where a checkout produces one file, silently dropping an entity.

The two `origin-*` warnings are the two halves of an incomplete record. `origin-dir` without an `origin-path` on every file produces a `by-origin` tree with holes — permitted by §4.9, and precisely the state in which a checksum file shipped inside that tree stops verifying. `origin-path` without `origin-dir` is the mirror image: correct data that no view will ever show. Neither is an error, because both are reachable by hand-editing an entity file that is on its way somewhere.

Unreferenced blobs are **not** a lint concern — that is a store question, reported by `mu gc`.

### 2.3 `mu build`

Regenerates `views/` from `meta/` + `store/`, satisfying the determinism requirement of SPEC.md §5.6. The format constrains only the resulting tree (SPEC.md §5.5); the procedure below belongs to this tool.

`build` never edits `views/` in place:

```
1. build views.new/ (completely)
2. views/ → views.old/   (rename, if present)
3. views.new/ → views/   (rename)
4. delete views.old/ recursively
```

An aborted build leaves `views.new/` or `views.old/` behind; both are removed at the start of the next run. `views.new/` sits beside `views/` so that step 3 is an `ATOMIC_MOVE` (section 1).

This is **not** a swap. Between steps 2 and 3 there is a window in which `views/` does not exist at all: an observer sees the old tree, then nothing, then the new tree — never a half-built one. Atomic directory swapping is not portable (`RENAME_EXCHANGE` is Linux-only, `RENAME_SWAP` macOS-only) and the JDK exposes neither, so the gap is accepted. It is harmless because nothing reads `views/` (SPEC.md §5.1); a player pointed at the tree during a rebuild sees it vanish and reappear.

`by-origin` is built in the same pass as the other views. It is the second one that symlinks into the store (SPEC.md §5.4) and the only one whose names the builder does not construct: each segment is written out exactly as recorded, and `lint` has already checked it against §4.9. The builder must not re-sanitize — passing those names through SPEC.md §5.2 again would be a no-op on valid data and a silent corruption on anything else, and either way it would defeat the guarantee the view exists for.

> **Open question.** `mu build <view>` rebuilds a single view, but step 1 produces a complete `views.new/`. A selective build must carry the untouched views over before the swap, otherwise `mu build by-release-year-original` deletes every other view. Whether they are copied, hardlinked or rebuilt is not yet decided.

### 2.4 `mu verify`

Reads every blob and compares its SHA-256 against the filename, which is the hash in full and nothing else (SPEC.md §3.2).

`--quick` only checks existence.

Corrupt blobs are reported, never touched automatically.

#### What counts as a blob

`verify` and `gc` are the only commands that enumerate the store rather than resolving a reference. Since SPEC.md §3.2 defines resolution as the path formula alone and gives no meaning to anything else under `store/`, enumeration needs its own rule:

> A path is a blob if and only if it matches `store/[0-9a-f]{2}/[0-9a-f]{64}` and the two leading characters equal the first two of the filename. Everything else under `store/` is skipped, without descending into it.

That rule is what keeps `store/.tmp/` and `store/.trash/` out of both commands. Without it, `verify` reports incomplete staging files as corrupt blobs, and `gc` finds the contents of its own trash, sees them unreferenced and trashes them again.

### 2.5 `mu gc`

Collects all `blob`, `cover-front`/`cover-back` and `asset.blob` references from `meta/`, compares them against the store contents (using the blob rule of §2.4), and moves anything unreferenced to `store/.trash/<date>/`, keeping the `<h[0:2]>/<h>` shard layout inside. Never `rm`. Emptying the trash stays a manual task.

Not deleting is a policy of this tool, not a rule of the format: SPEC.md §3.3 forbids overwriting a blob, but says nothing about removal. A blob under `store/.trash/` no longer resolves — it is outside the path formula — so trashing one referenced by `meta/` breaks that reference just as deleting it would. `gc` is therefore only as safe as its reference collection is complete.

Every reference must be reduced to its hash before the comparison — everything from the first `.` onward is not part of the store path (SPEC.md §4.5). Comparing reference strings against filenames verbatim would leave no reference matching any blob and send the entire store to the trash.

`origin-path` and `origin-dir` are not references and contribute nothing here (SPEC.md §4.9). A file recorded in an origin tree is reachable only through the `blob` value beside it, so the reference collection above is already complete — recording origin paths neither protects a blob from `gc` nor exposes one to it.

### 2.6 `mu migrate`

Adopts an existing collection. Not yet specified.

## 3. Locking

The format defines no write lock. SPEC.md §4.0 leaves entries directly under `meta/` that are not `.mu`, `artists/` or `releases/` to the tool, and this implementation places `meta/.lock` there.

All writing commands take that lock via `FileChannel.tryLock()`, which is advisory: it excludes other processes using the same call, not a tool that ignores the file altogether. A second `mu` process aborts immediately with exit code 3 rather than waiting. Read-only commands (`lint`, `verify`) do not lock.

The lock carries no content that anything interprets, it is never versioned (SPEC.md §6), and a collection in which the file is absent is valid — it exists only while, or because, a `mu` process has written. Deleting it while none is running has no effect.

## 4. Exit codes

| Code | Meaning                                     |
|------|---------------------------------------------|
| 0    | success                                     |
| 1    | problems found (`lint`, `verify`)           |
| 2    | usage error (bad arguments, root not found) |
| 3    | lock held                                   |
| 4    | I/O error                                   |

## 5. Known gaps

Points raised in review that the specification does not yet answer. Section references are to [SPEC.md](SPEC.md).

- **View target ambiguity.** A release with two `main` credits appears under two `by-artist` directories, but has only one entry in `by-credit`, `by-release-year-original` and `by-source-medium`. Which one those link to is undefined (SPEC.md §5.4).
- **Cover art in views is unspecified.** The tree in SPEC.md §2 shows `cover.jpg` in the `by-artist` release directory, but §5.4 defines filenames for tracks and assets only. The name a `cover-front`/`cover-back` reference produces is left to the tool, which also makes the asset collision rule (§5.4) depend on an undefined name. §4.9 closes this for `by-origin`, where a cover carries the name it was received with, and leaves it exactly as open for `by-artist`.
- **Sanitization order.** §5.2 trims before truncating, so truncation can reintroduce a trailing space or dot.
- **Composite name length.** The 200-byte limit is per attribute value; `<billing> - <title>` can exceed the 255-byte limit of common filesystems. Should this ever be closed by truncating the composite, the truncation must not reach the `(<id-prefix>)` suffix of §5.3 — the uniqueness guarantee of that step rests on the suffix surviving intact.
- **Reserved characters.** Only `/` is replaced. Names break on SMB/exFAT targets, which reject `\ : * ? " < > |`. Only relevant if cross-platform mirroring becomes a goal. Since §4.1 constrains identifiers by the same list — it forbids only what §5.2 rewrites — the gap now reaches `meta/` too: an identifier containing `?` or `:` is valid per the format and still cannot be checked out on Windows. §4.9 inherits it once more and makes it sharper: a received filename containing `?` is a valid `origin-path`, and because §4.9 forbids rewriting the name, a builder targeting such a filesystem has no legal fallback — it can only leave the file out or fail.
- **Extension vs. content.** Since the extension moved out of the store path into the reference (§3.2, §4.5), a reference can resolve correctly and still carry the wrong extension — a FLAC linked as `.jpg` in `views/`. `lint` only checks the shape of the extension, not whether it matches the bytes; detecting that needs content sniffing, which is not specified and not implemented.
- **Atomicity is stated, not enforceable.** §3.4 lets a reader trust a blob that resolves, but nothing on disk proves the guarantee was honoured. A foreign tool that writes blobs in place leaves a store whose paths may hold truncated content, and no reader can tell without re-hashing — which is exactly what §3.4 exists to make unnecessary. `mu verify` is the only remedy, and it is not free.
- **`meta/` has no atomicity guarantee.** §3.4 gives one for blobs: every store path that exists holds complete content, so a reader may trust it without checking. Nothing states the equivalent for entity files, so a reader may observe a `.mu` file mid-write and parse half a TOML document — or, with a non-atomic writer, find one truncated after a crash. Unlike the write lock, which §4.0 no longer mentions because it constrains processes rather than the artefact, this one is a property of the on-disk state and would be checkable. This document does not fix how `import` publishes an entity file either; section 2.1 specifies the mechanism for blobs only.
- **No shared staging convention.** With `store/.tmp/` removed from the format (§3.2), two implementations cannot clean up after each other's interrupted writes. Orphaned staging files accumulate until the tool that created them runs again. Harmless for correctness, since they never resolve, but the space is only reclaimable by hand.
