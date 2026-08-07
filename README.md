# mu format

[![Project Status: WIP – Initial development is in progress, but there has not yet been a stable, usable release suitable for the public.](https://www.repostatus.org/badges/latest/wip.svg)](https://www.repostatus.org/#wip)

Manage your music collection without touching any media files.

* non-destructive
* flat file
* git friendly

## The idea

Three layers, each with one job:

| Layer    | Purpose                                 | Mutable                           | In git |
|----------|-----------------------------------------|-----------------------------------|--------|
| `store/` | media files, addressed by their content | never (append-only)               | no     |
| `meta/`  | metadata, references, structure         | yes, by hand or via the CLI       | yes    |
| `views/` | symlink trees for browsing and playback | generated, disposable at any time | no     |

Media files go into the store under the SHA-256 of their content and are never written to again — no tags, no renaming, no conversion. All metadata lives beside them in one plain TOML file per release or artist, small enough to edit by hand and to diff in git. Everything you actually browse (`by-artist/`, `by-credit/`, `by-release-year-original/`, `by-source-medium/`, `by-origin/`) is a tree of symlinks, regenerated from the other two layers and disposable at any time.

Content addressing drops a file's name, which is usually the point — but not when the release arrived as a unit, with a playlist or a checksum file that only resolves against the original filenames. Those names and the directory they came in are kept in the metadata, and `by-origin/` puts the tree back together exactly as it was received.

```
music/
├── store/ab/abcd3f…64hex…               # content-addressed, never modified
├── meta/releases/b27e3c80-….mu          # one TOML file per entity, in git
└── views/by-artist/Overmono/Good Lies [2023]/
        └── 01 Feeling Plain.flac -> ../../../../store/ab/abcd3f…
```

A release has no location. It has attributes. Where it shows up is the view's decision.

## Documentation

| Document                               | Contents                                                                           |
|----------------------------------------|------------------------------------------------------------------------------------|
| [SPEC.md](SPEC.md)                     | normative on-disk format: store, meta, schema, views, git. Implementation-neutral. |

## CLI

```
mu import <path>...        take files into the store, create a meta skeleton
mu search <query>          search releases, artists and tracks in meta
mu build [view]            regenerate views
mu lint [--strict]         check meta consistency
mu verify [--quick]        check store integrity
```

`import` and `search` are implemented; the rest are sketches.

## Architecture

The implementation is a **modulith** — a single-module Gradle project organized into domain modules with enforced boundaries. All modules live under `net.einself.mu`:

| Module          | Role                                                     | Dependencies                                  |
|-----------------|----------------------------------------------------------|-----------------------------------------------|
| `shared`        | Shared kernel: `ExitCode`, `MuException`                 | none (Java stdlib only)                       |
| `collection`    | Collection root discovery, format version, advisory lock | shared                                        |
| `storage`       | Content-addressed blob store (SHA-256)                   | shared, collection                            |
| `metadata`      | TOML entity files, `Release` model, repositories         | shared, collection, naming                    |
| `naming`        | NFC normalization, name sanitizing, extension derivation | shared                                        |
| `importcontext` | `mu import` workflow and orchestration                   | shared, collection, storage, metadata, naming |
| `searchcontext` | `mu search` workflow and query matching                  | shared, collection, metadata                  |
| `cli`           | Picocli commands (adapter layer only)                    | all module APIs                               |

### Module dependency graph

```
                     ┌─────────┐
                     │   cli   │  (adapter: coordinates workflows)
                     └────┬────┘
                          │
           ┌──────────────┼──────────────┐
           │              │              │
           v              v              v
    ┌─────────────┐  ┌──────────┐  ┌──────────┐
    │importcontext│  │  search  │  │  (other  │
    │             │  │ context  │  │ commands)│
    └──────┬──────┘  └────┬─────┘  └──────────┘
           │              │
      ┌────┼────┬────┬────┼────┐
      │    │    │    │    │    │
      v    v    v    v    v    v
   ┌────┐┌────┐┌────┐┌────┐┌────┐
   │coll││stor││meta││nami││meta│
   │ectn││age ││data││ng  ││data│
   └─┬──┘└─┬──┘└─┬──┘└─┬──┘└─┬──┘
     │     │     │     │     │
     └─────┴─────┴─────┴─────┘
               │
               v
          ┌────────┐
          │ shared │  (kernel: no dependencies)
          └────────┘
```

### Module structure

Each module (except `shared` and `cli`) splits into two packages:

- **`<module>.api`** — public interface, marked with jMolecules `@Module` annotation
- **`<module>.internal`** — private implementation, not accessible from outside the module

Modules are instantiated through static factory classes (`CollectionModule`, `StorageModule`, `ImportModule`, etc.) that hide internal implementation details and return only API types.

### Architectural rules

Module boundaries are **enforced at build time** by ArchUnit tests (`ModulithArchitectureTest`, `DddArchitectureTest`):

1. **No cycles** — modules form a directed acyclic graph
2. **`.internal` is private** — only the owning module can access its internal package
3. **CLI is an adapter** — depends only on `<module>.api`, never on `.internal`
4. **Shared kernel is independent** — `shared` has zero dependencies on other modules

The `shared` module provides the foundation (`MuException`, `ExitCode`) that all other modules build on. The `cli` module sits at the top of the dependency graph and coordinates work through the module APIs — it contains no domain logic itself, only command definitions and output formatting.

### Modules in detail

#### `shared` — Shared kernel

The dependency-free foundation for all modules. Contains:

- **`ExitCode`** — enum defining the 5 exit codes (SUCCESS, PROBLEMS, USAGE, LOCK_HELD, IO_ERROR)
- **`MuException`** — domain exception carrying an exit code and optional detail lines for error reporting

No other module may be imported here. This keeps error handling lightweight and prevents circular dependencies.

#### `collection` — Collection root management

Discovers and validates collection roots. Provides:

- **`CollectionRoot`** — value object representing the directory containing `meta/.mu`, with accessors for all standard paths (`.store()`, `.meta()`, `.releases()`, `.artists()`, `.staging()`, `.lock()`)
- **`CollectionService`** — finds the collection root by walking up from the working directory, reads and validates the format version from `meta/.mu`
- **`CollectionLock`** — advisory file lock preventing concurrent writes; acquisition fails immediately rather than blocking

The lock file (`meta/.lock`) and staging directory (`store/.tmp`) are scratch locations not defined by SPEC.md — the spec leaves entries it doesn't define without meaning, which is what permits the tool to place them there.

#### `storage` — Content-addressed blob store

Manages the `store/` directory with SHA-256 content addressing. Implements:

- **`BlobRepository`** — stores files at `store/<hash[0:2]>/<hash>`, computes SHA-256, ensures atomicity (no partial writes visible), deduplicates identical content automatically
- **`Blob`** — value object with hash and source path
- **Staging** — writes to `store/.tmp/` first, then publishes with atomic rename to guarantee SPEC.md section 3.4 (complete blobs only)

File extensions are deliberately excluded from the store path — they're metadata, not identity, and live in `meta/` as part of blob references (SPEC.md section 4.5).

#### `metadata` — TOML entities and persistence

Domain model and repository for releases and artists. Provides:

- **`Release`** — aggregate root with credits, tracks, assets, cover references, and origin paths (SPEC.md section 4.8)
- **`ReleaseRepository`** — loads/saves `.mu` files from `meta/releases/`, scans for entities
- **TOML writing** — LF line endings, no BOM, deterministic serialization with NFC normalization

Releases are immutable records. The repository handles all TOML parsing and serialization, keeping the format details isolated from domain logic. Uses `naming` module for NFC normalization required by SPEC.md section 4.3.

#### `naming` — Name sanitization and normalization

Enforces SPEC.md section 5.2 (name construction) and validates origin paths. Provides:

- **`NameSanitizer`** — applies the 6-step sanitization (NFC normalization, `/` → `_`, strip control characters, trim spaces/dots, truncate to 200 bytes UTF-8, fallback to `_`)
- **`Nfc`** — Unicode NFC normalization required by SPEC.md section 4.3
- **`ExtensionDeriver`** — extracts file extensions from source filenames, validates them against `[a-z0-9]{1,8}`, builds blob references as `<hash>.<ext>`

The `NameSanitizer.isUnchanged()` test is critical for origin path validation — paths must survive sanitization unchanged to be written verbatim in `by-origin/` views (SPEC.md section 4.9).

#### `importcontext` — Import workflow

Orchestrates the `mu import` command. Responsibilities:

- **Collect source files** — recursively scan input paths, classify files by type (audio, image, other)
- **Store blobs** — compute SHA-256, write to `store/`, track deduplication
- **Assemble release** — derive release ID from directory name, classify assets by extension, build `Release` aggregate
- **Detect tracks** — parse track numbers from filenames (`01 Title.flac`, `2-05 Title.m4a`), build disc/number positions
- **Handle origin paths** — optionally record `origin-dir` and per-file `origin-path` when `--origin` is set
- **Generate TOML** — serialize the `Release` to `.mu` format

Coordinates `storage`, `metadata`, and `naming` modules. The workflow is transactional: either all files are stored and metadata is written, or nothing is (via dry-run or on error).

#### `searchcontext` — Search workflow

Implements the `mu search` command. Provides:

- **Parallel search** — scans artists, releases, and tracks simultaneously
- **Query matching** — case-insensitive substring match on names, titles, album titles, file paths
- **Result aggregation** — returns structured results with entity type, path, matching field

Reads TOML files directly (no write path). Searches are read-only and take no lock.

#### `cli` — Command-line adapter

Picocli command definitions and output formatting. Contains:

- **`Main`** — entry point, global options (`--root`, `--format`), exception handler that converts `MuException` to exit codes
- **`ImportCommand`** — `mu import` with options for dry-run, origin recording, artist assignment
- **`SearchCommand`** — `mu search` with entity type filtering
- **`OutputFormatter`** — formats results as human-readable text or JSON

No domain logic. Commands instantiate services through module factories, invoke them, and format the results. The CLI is the only module that depends on all others.

### Key patterns

**Error handling**

Domain modules throw `MuException(ExitCode, message[, details])` with structured exit codes. The CLI's exception handler in `Main` catches them and converts to process exit codes (0–4). Uncaught exceptions become `IO_ERROR`. Never call `System.exit` outside `Main.main`.

Exit codes are fixed: `SUCCESS` (0), `PROBLEMS` (1), `USAGE` (2), `LOCK_HELD` (3), `IO_ERROR` (4).

**Path resolution**

All collection paths come from `CollectionRoot` accessors (`.store()`, `.meta()`, `.releases()`, `.staging()`, `.lock()`), never string concatenation. This keeps path construction centralized and correct.

**TOML writing**

Single configured `JToml` instance from `Main.toml()` ensures LF separators and no BOM, as required by SPEC.md section 4. Domain modules receive this instance through their factory methods rather than creating their own.

**Concurrency control**

Write operations take an advisory lock via `CollectionLock.acquire(root)` in try-with-resources. A second process attempting to acquire the lock fails immediately with `LOCK_HELD` instead of waiting. Dry runs skip locking entirely since they write nothing.

**Specification primacy**

SPEC.md is the normative on-disk format. When code behavior and SPEC.md disagree, SPEC.md wins — fix the code, not the spec.

## Status

Early development. The format specification is settling. `mu import` and `mu search` work; `build`, `lint` and `verify` are not implemented yet, so a collection can be filled and searched but not yet browsed or checked.

## Development

```
./gradlew test             run tests
./gradlew spotlessApply    format all sources
./gradlew build            compile, test, check formatting
./gradlew installGitHook   install the pre-commit hook (auto-formats on commit)
```

## License

See [LICENSE](LICENSE).
