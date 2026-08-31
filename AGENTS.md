# AGENTS.md

Single-module Gradle/Java project implementing the `mu` CLI for the mu music-collection
format. `SPEC.md` is the **normative** on-disk format; code comments cite it by section
(e.g. "SPEC.md section 4.6"). When behaviour and SPEC.md disagree, SPEC.md wins — change the
code, not the spec, unless the task is explicitly a spec change.

## Commands

`./gradlew test` is the verification step. `./gradlew build` additionally runs
`spotlessCheck` and fails on unformatted code — run `./gradlew spotlessApply` first.

CLI-level tests go through the seam `Main.execute(String[], PrintStream, PrintStream)`,
which takes injected streams; `main()` only wraps it with `System.exit`:

```java
int exitCode = Main.execute(new String[]{"import", "--root", root, path}, out, err);
```

## Architecture

Modulith inside one Gradle module. Roles and dependencies are documented in
[README.md](README.md#architecture) and per-module READMEs under
`src/main/java/net/einself/mu/<module>/README.md`.

Go through `<module>.api` types and `<Module>Module` factories, never another module's
`.internal` — arch tests enforce it. Do not use the existing CLI commands as a template
for layering.

## Conventions

- Tasks: only close/complete tasks when explicitly asked by the user.
- Planning mode (read-only): no commits, no Linear tickets, no pull requests.
- Errors: throw `MuException(ExitCode, message[, details])`. Do not call `System.exit`
  outside `Main.main`.
- Paths inside a collection come from `CollectionRoot` accessors, not string concatenation.
- TOML is written through the single configured `JToml` instance from `Main.toml()`
  (LF separators, no BOM — required by SPEC.md section 4). Do not build your own.
- Unimplemented options fail loudly with `USAGE` rather than degrading.
- Writes take the advisory lock via `CollectionModule.acquireLock(root)` in
  try-with-resources; a second process aborts with `LOCK_HELD` instead of waiting. Dry runs
  take no lock.

## Testing

JUnit 5 + AssertJ. Test classes live in `net.einself.mu.<module>` (flat, no `.internal`
mirror) and may reach into `internal` classes directly. Naming: `method_expectedBehaviour()`;
subject field named `underTest`; `// arrange`, `// act`, `// assert` in the larger tests.
Fixtures use `@TempDir`; a minimal collection root is a directory with `meta/.mu` containing
`format = 1`, and CLI tests then pass `--root <path>`.

## Git

Conventional commits with a **subject line only — never a body**.

## Status

`mu import` and `mu search` work. `build`, `lint` and `verify` from the README's CLI table
are unimplemented sketches — do not assume any code exists for them.