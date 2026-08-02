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
| [IMPLEMENTATION.md](IMPLEMENTATION.md) | the `mu` CLI: `import`, exit codes, platform decisions.                            |

## CLI

```
mu import <path>...        take files into the store, create a meta skeleton
mu build [view]            regenerate views
mu lint [--strict]         check meta consistency
mu verify [--quick]        check store integrity
```

Only `import` is specified in detail; the rest are sketches.

Details in [IMPLEMENTATION.md](IMPLEMENTATION.md).

## Status

Early development. The format specification is settling; the CLI is not yet usable.

## License

See [LICENSE](LICENSE).
