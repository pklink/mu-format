# metadata

Domain model for the mu-format: the `Release` entity with credits, tracks, and assets, plus
TOML entity file reading and writing.

## Dependencies

- shared
- collection

## SPEC.md references

- Section 1 — TOML types, null/absent tolerance
- Section 4 — entity file format (UTF-8, LF, no BOM)
- Section 4.2 — disc dual-typing (integer/string)
- Section 4.6 — credit rules (at least one `role = "main"`)
- Section 4.8 — release/artist entity schema
- Section 4.9 — origin path on tracks and assets
- Section 6 — versioned `meta/` directory