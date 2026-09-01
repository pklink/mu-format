# metadata

Domain model for the mu-format: the `Release` entity with credits, tracks, and assets, plus
TOML entity file reading/writing (`EntityFile`) and entity scanning (`MetadataScanner`).

## Dependencies

- shared
- collection
- naming

## SPEC.md references

- Section 4.3 — value conventions and cardinality, disc dual-typing (integer/string)
- Section 4 — entity file format (UTF-8, LF, no BOM)
- Section 4.5 — credit rules (at least one `role = "main"`)
- Section 4.7 — release/artist entity schema
- Section 4.8 — origin path on tracks and assets
- Section 6 — versioned `meta/` directory