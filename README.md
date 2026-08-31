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

Media files go into the store under the SHA-256 of their content and are never written to again — no tags, no renaming, no conversion. All metadata lives beside them in one plain TOML file per release or artist, small enough to edit by hand and to diff in git. Everything you actually browse (`by-artist/`, `by-origin/`) is a tree of symlinks, regenerated from the other two layers and disposable at any time.

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

Global options: --root <path>, --format text|json
```

### Implemented

**`mu import`**
Adds files to a collection. Walks directories, stores blobs under SHA-256, writes one
`meta/releases/<id>.mu` per import.

| Option           | Description                                              |
|------------------|----------------------------------------------------------|
| `--artist <id>`  | Artist identifier for the main credit                    |
| `--origin`       | Record origin-dir and origin-path (SPEC.md section 4.9)  |
| `--dry-run`      | Report what would happen, write nothing                  |

**`mu search`**
Scans entity files and prints matching releases, artists and tracks. Read-only.

| Option                | Description                                          |
|-----------------------|------------------------------------------------------|
| `-t`, `--type <type>` | Restrict to release, artist, track, or all (default) |
| `-f`, `--field <fld>` | Match only one TOML attribute                        |
| `--year <year>`       | Keep only releases with this release-year-original   |
| `--medium <medium>`   | Keep only releases with this source-medium           |
| `--role <role>`       | Credit matching counts only this role (e.g. feat)    |
| `-n`, `--limit <num>` | Maximum results (0 = unlimited)                      |

Both commands support JSON output via `--format json`.

### Unimplemented

`build`, `lint` and `verify` are sketches without code behind them.

## Architecture

The implementation is a **modulith** — a single-module Gradle project organized into domain modules with enforced boundaries. All modules live under `net.einself.mu`:

| Module          | Role                                                     |
|-----------------|----------------------------------------------------------|
| `shared`        | Shared kernel: `ExitCode`, `MuException`                 |
| `collection`    | Collection root discovery, format version, advisory lock |
| `storage`       | Content-addressed blob store (SHA-256)                   |
| `metadata`      | TOML entity files, `Release` model, repositories         |
| `naming`        | NFC normalization, name sanitizing, extension derivation |
| `importcontext` | `mu import` workflow and orchestration                   |
| `searchcontext` | `mu search` workflow and query matching                  |
| `cli`           | Picocli commands (adapter layer only)                    |

`shared` sits at the bottom with zero project dependencies; `cli` sits at the top and contains no domain logic. The modules in between form a DAG enforced by ArchUnit.

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

Each module has its own README with details, dependencies, and SPEC.md references under `src/main/java/net/einself/mu/<module>/README.md`.

### Key patterns

**Error handling**

Domain modules throw `MuException(ExitCode, message[, details])` with structured exit codes. The CLI's exception handler in `Main` catches them and converts to process exit codes (0–4). Uncaught exceptions become `IO_ERROR`. Never call `System.exit` outside `Main.main`.

Exit codes are fixed: `SUCCESS` (0), `PROBLEMS` (1), `USAGE` (2), `LOCK_HELD` (3), `IO_ERROR` (4).

**Path resolution**

All collection paths come from `CollectionRoot` accessors (`.store()`, `.meta()`,
  `.releases()`, `.artists()`, `.staging()`, `.lock()`, `.marker()`), never string concatenation. This keeps path construction centralized and correct.

**TOML writing**

Single configured `JToml` instance from `Main.toml()` ensures LF separators and no BOM, as required by SPEC.md section 4. Domain modules receive this instance through their factory methods rather than creating their own.

**Concurrency control**

Write operations take an advisory lock via `CollectionModule.acquireLock(root)` in try-with-resources. A second process attempting to acquire the lock fails immediately with `LOCK_HELD` instead of waiting. Dry runs skip locking entirely since they write nothing.

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

Spotless enforces Eclipse 4.34 formatting at build time and via the pre-commit hook.
Error Prone + NullAway run at compile time (NullAway is a hard error on `compileJava`).
CI is configured at `.github/workflows/pr-check.yml`; DeepSource at `.deepsource.toml`.

## License

See [LICENSE](LICENSE).
