# importcontext

Orchestrates the `mu import` workflow: walks source directories, assembles releases, stores
blobs, and writes entity files.

## Dependencies

- shared
- collection
- storage
- metadata
- naming

## SPEC.md references

- Section 4.5 — asset kind from extension
- Section 4.6 — credit assembly, completeness check
- Section 4.7 — track position prefixes, track ordering
- Section 4.8 — release/artist entity files
- Section 4.9 — `--origin` recording and validation