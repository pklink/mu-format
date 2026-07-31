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

Mapping of the spec's platform-neutral requirements onto the JDK:

| SPEC.md requirement          | Java API                                                  |
|------------------------------|-----------------------------------------------------------|
| NFC normalization (§4.3)     | `java.text.Normalizer.normalize(s, Form.NFC)`             |
| SHA-256 (§3.1)               | `MessageDigest.getInstance("SHA-256")`, streamed          |
| atomic publication (§3.4)    | `Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE)` |
| atomic rebuild (§5.5)        | `Files.move(…, StandardCopyOption.ATOMIC_MOVE)`           |
| read-only blobs (§2.1 below) | `Files.setPosixFilePermissions(…, r--r--r--)`             |
| write lock (section 3 below) | `FileChannel.tryLock()` on `meta/.lock`                   |

SPEC.md §3.4 requires only that a blob become visible as a whole; it prescribes no mechanism. This implementation satisfies that with a rename, and `ATOMIC_MOVE` requires source and target on the same filesystem. The staging directory `store/.tmp/` therefore lives inside the store, and `views.new/` beside `views/`. Both names are choices of this tool, not part of the format — SPEC.md §3.2 leaves any entry under `store/` that is not a `<hash[0:2]>/<hash>` pair without meaning.

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
3. Generate a release UUID, create `meta/releases/<uuid>.mu` as TOML with exactly one `[[credit]]` of role `main`.
4. Add audio files in filename order as `[[track]]` tables, each with `number`/`disc` (parsed from a leading prefix in the original name such as `01 `, `A1 `, `1-05 `) and a `blob` reference; the remainder of the name becomes `title`. A letter prefix maps to a string `disc` (`A1 ` → `disc = "A"`, `number = 1`), matching SPEC.md §4.7.
5. Reference images named `cover|front|folder` as `cover-front = "<hash>.<ext>"`.
6. Trigger `mu build`.

Options: `--release <uuid>` (import into an existing release), `--artist <uuid>` (sets the `main` credit), `--dry-run`.

`import` does not read tags from media files.

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

> **Open question.** Without `--artist` there is no artist UUID for the required `main` credit, so the result fails `mu lint`. It is not yet decided whether `import` creates an artist stub (and from which name) or whether `--artist` becomes mandatory.

### 2.2 `mu lint`

Validates `meta/` against SPEC.md and classifies each finding. Checks, in this order:

| Check                                                                             | Severity                       |
|-----------------------------------------------------------------------------------|--------------------------------|
| `meta/.mu` present, `format` an integer not higher than implemented              | error                          |
| entity file is valid TOML                                                         | error                          |
| required attributes present (`name`, `title`, `blob`, `number`)                   | error                          |
| cardinality respected (no `title` as an array)                                    | error                          |
| `disc`/`number` well-typed (`number` integer, `disc` integer or non-empty string) | error                          |
| `number` ≥ 1, and an integer `disc` ≥ 1                                          | error                          |
| `number` unique per disc                                                          | error                          |
| `bit-depth` and `sample-rate` integers ≥ 1 (§4.2)                                 | error                          |
| release has ≥ 1 credit with `role = "main"`                                       | error                          |
| `role` and `artist` present in every credit                                       | error                          |
| `credit.artist` points to an existing UUID                                        | error                          |
| blob reference exists in the store                                                | error                          |
| `kind` and `blob` present in every asset                                          | error                          |
| `asset.blob` exists in the store                                                  | error                          |
| `join` on a role other than `main`                                                | warning                        |
| duplicate `(role, artist)` at the same level                                      | warning                        |
| string values NFC-normalized (including `as`)                                     | warning (fixable with `--fix`) |
| release without tracks                                                            | warning                        |
| `release-year-medium` equal to `release-year-original` (redundant, §4.8)          | warning (fixable with `--fix`) |
| attribute names not in the schema                                                 | notice                         |
| `role`, `asset.kind`, `type`, `source-medium` not in the V1 vocabulary            | notice                         |
| blob reference extension not matching `[a-z0-9]{1,8}` (§4.5)                      | notice                         |

`--strict` turns warnings into errors.

Unreferenced blobs are **not** a lint concern — that is a store question, reported by `mu gc`.

### 2.3 `mu build`

Regenerates `views/` from `meta/` + `store/` using the atomic rebuild of SPEC.md §5.5, and must satisfy the determinism requirement of §5.6.

> **Open question.** `mu build <view>` rebuilds a single view, but §5.5 replaces `views/` as a whole. A selective build must carry the untouched views over into `views.new/`, otherwise `mu build by-release-year-original` deletes every other view. Not yet specified.

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

### 2.6 `mu migrate`

Adopts an existing collection. Not yet specified.

## 3. Locking

All writing commands take an exclusive lock on `meta/.lock` via `FileChannel.tryLock()`. A second process aborts immediately with exit code 3.

Read-only commands (`lint`, `verify`) do not lock.

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

- **View target ambiguity.** A release with two `main` credits appears under two `by-artist` directories, but has only one entry in `by-release-year-original` and `by-source-medium`. Which one it links to is undefined (SPEC.md §5.4).
- **Sanitization order.** §5.2 trims before truncating, so truncation can reintroduce a trailing space or dot.
- **Composite name length.** The 200-byte limit is per attribute value; `<billing> - <title>` can exceed the 255-byte limit of common filesystems.
- **`uuid[0:8]` uniqueness.** 32 bits, described as "guaranteed unique" in §5.3. Practically collision-free at collection scale, but not guaranteed; no fallback is defined.
- **Reserved characters.** Only `/` is replaced. Names break on SMB/exFAT targets, which reject `\ : * ? " < > |`. Only relevant if cross-platform mirroring becomes a goal.
- **Extension vs. content.** Since the extension moved out of the store path into the reference (§3.2, §4.5), a reference can resolve correctly and still carry the wrong extension — a FLAC linked as `.jpg` in `views/`. `lint` only checks the shape of the extension, not whether it matches the bytes; detecting that needs content sniffing, which is not specified and not implemented.
- **Atomicity is stated, not enforceable.** §3.4 lets a reader trust a blob that resolves, but nothing on disk proves the guarantee was honoured. A foreign tool that writes blobs in place leaves a store whose paths may hold truncated content, and no reader can tell without re-hashing — which is exactly what §3.4 exists to make unnecessary. `mu verify` is the only remedy, and it is not free.
- **No shared staging convention.** With `store/.tmp/` removed from the format (§3.2), two implementations cannot clean up after each other's interrupted writes. Orphaned staging files accumulate until the tool that created them runs again. Harmless for correctness, since they never resolve, but the space is only reclaimable by hand.
