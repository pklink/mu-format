# mu — implementation notes

The `mu` command-line tool. The on-disk format is defined in [SPEC.md](SPEC.md) and is independent of everything here; where the two touch, SPEC.md is authoritative.

## 1. Platform

| Concern     | Choice                                                           |
|-------------|------------------------------------------------------------------|
| Language    | Java                                                             |
| Build       | Gradle (`build.gradle`)                                          |
| CLI parsing | `info.picocli:picocli:4.7.6`                                     |
| File utils  | `commons-io:commons-io:2.16.1`                                   |
| TOML        | `io.github.wasabithumb:jtoml:1.5.2`                              |
| Tests       | JUnit 5 + AssertJ                                                |

Mapping onto the JDK. Rows with a `SPEC.md §` are obligations of the format, the rest are decisions of this tool.

| Requirement                       | Java API                                                  |
|-----------------------------------|-----------------------------------------------------------|
| NFC normalization (SPEC.md §4.3)  | `java.text.Normalizer.normalize(s, Form.NFC)`             |
| SHA-256 (SPEC.md §3.1)            | `MessageDigest.getInstance("SHA-256")`, streamed          |
| atomic publication (SPEC.md §3.4) | `Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE)` |
| read-only blobs (§2.2)            | `Files.setPosixFilePermissions(…, r--r--r--)`             |
| view swap (§3)                    | `Files.move(…, StandardCopyOption.ATOMIC_MOVE)`           |
| write lock (§6)                   | `FileChannel.tryLock()` on `meta/.lock`                   |

SPEC.md §3.4 requires only that a blob become visible as a whole, not how. This tool renames, and `ATOMIC_MOVE` needs source and target on one filesystem — hence `store/.tmp/` inside the store and `views.new/` beside `views/`. None of those paths is part of the format: §3.2 and §4.0 leave any unknown entry under `store/` and `meta/` without meaning, and §5.5 leaves the build procedure open. `0444` is likewise a tool decision and best-effort: where POSIX permissions are missing (exFAT, SMB) the call fails and is ignored.

The TOML library is the one constraint the format placed on the choice. §4.6 rule 9 makes the block table the canonical write form, which rules out any writer that emits a table inside an array inline — `jackson-dataformat-toml` does exactly that, and `org.tomlj:tomlj` does not write at all. jtoml writes `SortMethod.STRATIFIED` by default: primitives first, then arrays of tables, each group lexicographic. Key order inside an entity file is therefore alphabetical rather than the order the examples in SPEC.md use, and not the writer's to choose — but it is stable, which is all §6 needs from it. Entity files are written with `LINE_SEPARATOR = LF` and `WRITE_BOM = NEVER` (§4), and published by rename, so a reader never sees a half-written one.

## 2. `mu import`

```
mu import [--release <id>] [--artist <id>] [--origin] [--dry-run] <path>...
```

Global option `--root <path>`, default: search upwards for a directory containing `meta/.mu`, the way `git` does (§4.0).

1. Collect files recursively, classify by extension into audio / image / other.
2. Hash each file while streaming and take it into the store (§2.2).
3. Create `meta/releases/<id>.mu` as TOML with exactly one `[[credit]]` of role `main`. The identifier is a UUIDv4: §4.1 would allow a readable one, but `import` reads no tags, and the directory name is not trustworthy enough for an identity that must never change.
4. Audio files in filename order become `[[track]]` tables with a `blob` reference and `number`/`disc`, parsed from a leading prefix such as `01 `, `A1 `, `1-05 `; the remainder of the name becomes `title`. A letter prefix gives a string `disc` (`A1 ` → `disc = "A"`, `number = 1`, §4.7).
5. An image named `cover|front|folder` becomes `cover-front = "<hash>.<ext>"`. Every remaining non-audio file becomes an `[[asset]]`, its `kind` guessed from the extension: images `scan`, `.log` `log`, `.cue` `cue`, anything else `other`. The vocabulary is open (§4.8), so a wrong guess is valid and is corrected by editing the entity file.

`--release` imports into an existing release, `--artist` sets the `main` credit.

`title` is required (§4.8) and `import` reads no tags, so it takes the base name of the imported directory — the same source `--origin` trusts for `origin-dir`, and the only one available. For file arguments it is the name of the containing directory.

Files are collected without a filter: everything under the directory goes into the store, `.DS_Store` included. The format has no opinion here, and a blocklist would be a guess of a different kind — one that silently drops bytes the user handed over.

Order is by NFC-normalized relative path in code point order, never the order the filesystem lists. `import` writes no attribute it cannot read off the filesystem, so `type`, the year attributes, `source-medium` and the audio properties are left out entirely rather than guessed.

**Numbering falls back as a whole.** `number` is required and unique per disc (§4.7), so a set in which only some filenames carry a prefix cannot be numbered from the prefixes alone. If any audio file fails to parse, or if the parsed positions collide, `import` discards all of them and numbers the release sequentially in filename order, `title` becoming the whole stem. Half-parsed numbering would produce an entity file `lint` rejects; a leading `1984 ` is not read as a track number, and neither is `00 `.

**Without `--artist` the release is written incomplete.** §4.6 requires a credit with `role = "main"` whose `artist` resolves; `import` writes the credit with `role` alone and says so on stderr. The alternatives were worse: a mandatory `--artist` refuses work that is otherwise complete, and a stub artist would have to invent a name from the directory — the one string §4.1 warns is not trustworthy enough to hang an identity on. `lint` reports the gap, and filling it in is a one-line edit.

> **Not implemented.** `--release` exits 2. Importing into an existing release means reading an entity file and writing it back, which loses the comments and the key order a hand-edited file carries. Until that round-trip is settled, refusing is better than silently creating a second release.

### 2.1 `--origin`

Records what §4.9 defines: the base name of the imported directory as `origin-dir`, each file's relative path as `origin-path` on its `[[track]]` or `[[asset]]` table, or as `cover-front-origin-path` for the image of step 5.

Off by default. Most imports are a loose directory whose name carries nothing worth keeping, and a recorded path nobody wants is still a value to be checked and a tree to be materialized. It pays where the directory *is* the artefact — a playlist or checksum file that only resolves against the original names.

- Exactly **one** directory argument. Several paths, or a file, leave no name for `origin-dir` (exit 2).
- Every segment must survive §5.2 unchanged (§4.9). If one does not, `import` lists every offending path and aborts before writing anything (exit 1); importing the rest would produce exactly the half-tree the option exists to prevent. §4.9 does permit a partial tree, since a hand-written entity file may be incomplete — this tool does not produce one.
- Origin paths must be unique within the release, compared after NFC normalization and case folding (§4.9). The check runs before the store is touched, together with the one above.

### 2.2 Taking a file into the store

§3.4 fixes the guarantee, not the mechanism. Per file:

1. Copy the source into `store/.tmp/<random>`, hashing it in the same pass.
2. Target path is `store/<h[0:2]>/<h>`.
3. Target exists → discard the temp file, count a dedup. The content is identical by definition (§3.3), so nothing is overwritten.
4. Otherwise create the shard directory and **rename** onto the target with `ATOMIC_MOVE`.
5. Set `0444`, best-effort.

An abort before step 4 leaves garbage in `store/.tmp/`, which the next run clears before it starts. Nothing there is reachable by the path formula, so an interrupted import cannot produce a resolvable but incomplete blob. Another implementation may stage elsewhere — but then neither tool cleans up after the other.

Every blob is in place before the entity file is written. The two failure modes are not symmetric: blobs nothing references are invisible — §3.2 resolves by path formula and never by listing — whereas a release referencing a blob that is not there is broken. `--dry-run` hashes without copying and takes no lock; it writes nothing, so there is nothing to exclude anyone from.

### 2.3 Deriving the extension

The store path is the bare hash (§3.2); the extension lives only in the reference, as a rendering hint (§4.5). `import` takes it from the source filename: the part after the **last** `.`, lower-cased with ASCII rules, used only if it matches `[a-z0-9]{1,8}`; a leading dot starts no extension (`.gitignore`); if nothing qualifies, the reference is the bare hash. A heuristic that trusts the filename — but since the result lives in `meta/`, a wrong extension is fixed by editing the entity file, not by re-importing.

## 3. `mu build [view]` — sketch

Not implemented; the shape below is settled, the details are not.

Regenerates `views/` from `meta/` + `store/`, deterministically (§5.6). The format constrains the resulting tree (§5.4), not the procedure. `build` never edits `views/` in place:

```
1. build views.new/ completely
2. views/ → views.old/   (rename, if present)
3. views.new/ → views/   (rename)
4. delete views.old/
```

An aborted run leaves `views.new/` or `views.old/` behind; the next run removes both before it starts. This is **not** a swap: between steps 2 and 3 `views/` does not exist, so an observer sees the old tree, then nothing, then the new one — never a half-built one. Atomic directory swapping is not portable (`RENAME_EXCHANGE` Linux, `RENAME_SWAP` macOS) and the JDK exposes neither; the gap is harmless because nothing reads `views/` (§5.1).

`by-origin` is built in the same pass, and it is the only view whose names the builder does not construct: each segment is written out exactly as recorded (§4.9). Re-sanitizing through §5.2 would be a no-op on valid data, a silent corruption on anything else, and would defeat the point of the view either way.

> **Open questions.** `mu build <view>` rebuilds one view, but step 1 produces a complete `views.new/`; whether the untouched views are copied, hardlinked or rebuilt before the swap is undecided. The name a `cover-front`/`cover-back` reference produces in `by-artist` is undefined (§5.4 covers tracks and assets), and with two `main` credits it is undefined which release directory the single entry in `by-credit`, `by-release-year-original` and `by-source-medium` links to.

## 4. `mu lint [--strict]` — sketch

Not implemented. Validates `meta/` against SPEC.md, in three severities; `--strict` promotes warnings to errors. Read-only, no lock.

**Errors** — the collection is not valid per the format:

- `meta/.mu` present, `format` an integer not above the implemented version (§4.0)
- identifiers valid, and unique per directory under NFC **and** case folding (§4.1)
- entity file parses as TOML; required attributes present, cardinality respected (§4.4, §4.8)
- `number`/`disc` well-typed, ≥ 1, `number` unique per disc (§4.7); `bit-depth`, `sample-rate` integers ≥ 1 (§4.2)
- at least one credit with `role = "main"`; `role` and `artist` present, `artist` resolves (§4.6)
- every `blob`, `cover-*` and `asset.blob` reference exists in the store; `kind` present on assets (§4.5)
- `origin-dir` and every `origin-path` segment valid, `origin-path` unique per release (§4.9)

**Warnings** — valid, but almost certainly not meant: `join` on a non-`main` role, duplicate `(role, artist)` at one level, non-NFC strings (`--fix`), a release without tracks, `release-year-medium` equal to `release-year-original` (`--fix`, §4.8), and the two halves of an incomplete origin record — `origin-dir` without `origin-path` on every file, or `origin-path` without `origin-dir`.

**Notices** — attribute names outside the schema, values outside the listed vocabulary (`role`, `asset.kind`, `type`, `source-medium`), an extension not matching `[a-z0-9]{1,8}` (§4.5).

Case-folded uniqueness can only fail on a case-sensitive filesystem — elsewhere the two files are one — but the check earns its place: such a pair committed from ext4 makes the checkout drop an entity on macOS or Windows. Unreferenced blobs are a store question and not reported here.

## 5. `mu verify [--quick]` — sketch

Not implemented. Re-hashes every blob and compares against its filename, which is the full hash and nothing else (§3.2). `--quick` checks existence only. Corrupt blobs are reported, never touched. Read-only, no lock.

`verify` enumerates the store instead of resolving references, which needs a rule the format does not give: a path is a blob iff it matches `store/[0-9a-f]{2}/[0-9a-f]{64}` and the two leading characters equal the first two of the filename. Everything else under `store/` is skipped without descending — that is what keeps `store/.tmp/` from being reported as a heap of corrupt blobs.

## 6. Locking

`import` and `build` hold `meta/.lock` via `FileChannel.tryLock()`; a second `mu` process aborts immediately with exit code 3 rather than waiting. `lint` and `verify` do not lock. The lock is advisory: it excludes processes using the same call, not a tool that ignores the file. §4.0 leaves the name to the tool, the file has no content anyone interprets, it is never versioned (SPEC.md §6), and its absence is valid — deleting it while no `mu` runs has no effect.

## 7. Exit codes

| Code | Meaning                                                    |
|------|------------------------------------------------------------|
| 0    | success                                                    |
| 1    | problems found (`lint`, `verify`, invalid `origin-path`)   |
| 2    | usage error (bad arguments, root not found)                |
| 3    | lock held                                                  |
| 4    | I/O error                                                  |
