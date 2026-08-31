# storage

Content-addressed blob store: files are stored under `store/` keyed by their SHA-256 hash,
with deduplication on identical content.

## Dependencies

- shared
- collection

## SPEC.md references

- Section 3.1 — content addressing (SHA-256)
- Section 3.3 — deduplication, identical blobs are not overwritten
- Section 3.4 — visibility guarantee