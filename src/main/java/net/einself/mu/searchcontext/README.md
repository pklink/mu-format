# searchcontext

Orchestrates the `mu search` workflow: scans entity files and matches releases, artists,
credits, and tracks against query strings with optional field/year/medium/role filters.

## Dependencies

- collection
- metadata
- naming

## SPEC.md references

- Section 4.2 — entity types, NFC-normalized identities
- Section 4.3 — query NFC normalization before comparison
- Section 4.5 — credit-based search
- Section 4.6 — track search (title, isrc)
- Section 4.7 — release/artist scalar attribute search