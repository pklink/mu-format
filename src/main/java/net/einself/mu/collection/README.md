# collection

Responsible for collection root discovery, the `meta/.mu` format version, and the advisory
write lock. Per `api/package-info.java`, this module depends only on `shared`.

## Responsibilities

- **Root discovery**: either explicit via `--root`, or by searching upward from the
  working directory for `meta/.mu` (git-style). Fails with `MuException(USAGE)` if no
  valid root is found, or if the explicit root is invalid.
- **Format version**: reads `format` from `meta/.mu` and refuses to proceed if the format
  is newer than `IMPLEMENTED_VERSION` (SPEC.md section 4.0) — no silent degrading.
- **Write lock**: non-blocking advisory lock on `meta/.lock` via `FileChannel.tryLock()`.
  A second process gets `MuException(LOCK_HELD)` immediately instead of waiting.

## Public API (`collection.api`)

- `CollectionModule` — factory: `createService(JToml)` → `CollectionService`,
  `acquireLock(CollectionRoot)` → `LockHandle`. The intended entry point for other
  modules.
- `CollectionRoot` — record wrapping the root `Path`; derives all well-known paths
  (`store()`, `meta()`, `releases()`, `artists()`, `staging()`, `lock()`, `marker()`) as
  pure functions, so no other module concatenates paths itself.
- `CollectionService` — `findRoot(...)`, `readFormatVersion(...)`.
- `LockHandle` — `AutoCloseable` marker for try-with-resources locking.

## Internals (`collection.internal`)

- `CollectionServiceImpl` — implements `CollectionService`.
- `FormatVersionReader` — reads/validates `format` from `meta/.mu`.
- `CollectionLock` — implements `LockHandle`, holds the lock on `meta/.lock`.

## Dependencies on other modules

- **`shared`** — the only module dependency, declared in `api/package-info.java` and
  confirmed by imports: `ExitCode` and `MuException` for error signaling
  (`CollectionServiceImpl`, `FormatVersionReader`, `CollectionLock`).
- No dependency on `storage`, `metadata`, `naming`, `importcontext`, or `searchcontext`.
- Consumed by `cli` (`ImportCommand`, `SearchCommand`), which call
  `CollectionModule.createService(...)` and `findRoot(...)` — this module has no
  dependency back on `cli`.

## SPEC.md references

- Section 2 — Directory layout: the root is the directory containing `meta/.mu`.
- Section 3.2 — Store layout: unrecognized entries under `store/` are meaningless, which
  is why `staging()` can claim `store/.tmp`.
- Section 4.0 — Collection marker: `meta/.mu` with a required `format` field; newer
  formats must be refused.
- Section 6 — Git integration: only `meta/` is versioned; `meta/.lock` is a pure
  implementation-detail file, not part of the format, and never versioned.
