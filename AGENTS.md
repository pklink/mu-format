# AGENTS.md

Single-module Gradle/Java project implementing the `mu` CLI for the mu music-collection
format. `SPEC.md` is the **normative** on-disk format; code comments cite it by section
(e.g. "SPEC.md section 4.6"). When behaviour and SPEC.md disagree, SPEC.md wins — change the
code, not the spec, unless the task is explicitly a spec change.

## Commands

```
./gradlew test                                    # full suite
./gradlew test --tests '*ImportCommandTest*'      # one class
./gradlew test --tests '*ImportCommandTest.import_storesEveryFileAndCreatesTheRelease*'
./gradlew build                                   # compile + test
```

No lint, formatter, typecheck, codegen or CI configuration exists. `./gradlew test` is the
only verification step.

Java 25 toolchain, pinned by `mise.toml` and `build.gradle`. Gradle 9.6.1 via wrapper.

## Running the CLI

There is **no `application` plugin**, so `./gradlew run` does not exist and the jar has no
`Main-Class`. Exercise the CLI through the test seam:

```java
int exitCode = Main.execute(new String[]{"import", "--root", root, path}, out, err);
```

`Main.execute(String[], PrintStream, PrintStream)` (`src/main/java/net/einself/mu/cli/Main.java:52`)
takes injected streams; `main()` only wraps it with `System.exit`. All CLI-level tests use it.

## Architecture

Modulith inside one Gradle module. Packages under `net.einself.mu`:

| Package         | Role                                                      |
|-----------------|-----------------------------------------------------------|
| `shared`        | shared kernel: `ExitCode`, `MuException`. No dependencies |
| `collection`    | collection root discovery, `meta/.mu` version, write lock |
| `storage`       | content-addressed blob store (SHA-256)                    |
| `metadata`      | TOML entity files, `Release` model, repositories          |
| `naming`        | NFC normalization, name sanitizing, extension deriving    |
| `importcontext` | `mu import` workflow                                      |
| `searchcontext` | `mu search` workflow                                      |
| `cli`           | Picocli commands, adapter layer only                      |

Each module splits into `<module>.api` (public, jMolecules `@Module` on `package-info.java`)
and `<module>.internal` (private). `<Module>Module` classes in `api` are the intended
factories (e.g. `StorageModule.createRepository(root)`).

### Module boundaries are enforced by arch tests

`ModulithArchitectureTest` and `DddArchitectureTest` use ArchUnit 1.4.1 to verify that
`.internal` packages are not accessed from outside their module and that the CLI only depends
on module APIs.

For new code: go through `<module>.api` types and `<Module>Module` factories, never through
another module's `.internal`. Do not use the existing CLI commands as a template for layering.

## Conventions

- Errors: throw `MuException(ExitCode, message[, details])`. `Main`'s `ExceptionHandler`
  turns it into the exit code and prints `mu: <message>` plus indented detail lines; anything
  else becomes `IO_ERROR`. Do not call `System.exit` outside `Main.main`.
- Exit codes are fixed: `SUCCESS 0`, `PROBLEMS 1`, `USAGE 2`, `LOCK_HELD 3`, `IO_ERROR 4`.
- Paths inside a collection come from `CollectionRoot` accessors (`store()`, `meta()`,
  `releases()`, `staging()`, `lock()`), not string concatenation.
- TOML is written through the single configured `JToml` instance from `Main.toml()`
  (LF separators, no BOM — required by SPEC.md section 4). Do not build your own.
- Unimplemented options fail loudly (`--release` throws `USAGE`) rather than degrading.
- Writes take the advisory lock via `CollectionLock.acquire(root)` in try-with-resources; a
  second process aborts with `LOCK_HELD` instead of waiting. Dry runs take no lock.

## Testing

- JUnit 5 + AssertJ. Test classes live in `net.einself.mu.<module>` (flat, no `.internal`
  mirror) and may reach into `internal` classes directly.
- Naming: `method_expectedBehaviour()`; subject field named `underTest`; `// arrange`,
  `// act`, `// assert` comments in the larger tests.
- Fixtures use `@TempDir`. A minimal collection root is a directory with `meta/.mu`
  containing `format = 1`; CLI tests then pass `--root <path>`.
- `BlobStoreTest` uses `Assumptions` to skip POSIX-permission assertions on unsupported
  filesystems.

## Git

Commit messages are conventional commits with a **subject line only — never a body**.
Types in use: `docs`, `chore`, `feat`, `test`, `refactor`. Put everything the reader needs
into the subject; if it does not fit, the commit is doing too much.

## Status

`mu import` and `mu search` work. `build`, `lint` and `verify` from the README's CLI table
are unimplemented sketches — do not assume any code exists for them.
