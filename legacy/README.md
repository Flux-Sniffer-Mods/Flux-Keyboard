# Legacy

Files carried over from the upstream project that Flux Keyboard's build, tests and release
workflow don't use. They're kept for reference, not maintained, and can be removed without
affecting the app.

| Path | What it was |
|---|---|
| `refactoring.md` | Upstream's log of splitting up the input method service |
| `build_and_run.bat`, `convert_dictionaries.py` | Upstream local build and one-off dictionary conversion |
| `dict_backup/` | Untruncated source dictionaries used by the old truncate scripts |
| `design/` | Upstream logo sources |
| `fdroiddata/` | Upstream F-Droid metadata |
| `maestro/` | Upstream UI test flows for the Titan 2 Elite |
| `signing/`, `docs/*attestation*` | Upstream signing lineages and key attestations |
| `docs/device-archives/` | Upstream keyboard behaviour snapshots |
| `docs/settings-links.md`, `docs/keyboard-phone-widths.md` | Upstream notes |
| `github/` | Pre-1.0 release notes and upstream nightly release templates |
| `scripts/` | Upstream nightly, F-Droid, PIV signing, key rotation and old dictionary scripts |
| `tools/prediction_bench/` | Upstream next-word model benchmark |

Code kept only for backwards compatibility lives in the app's `legacy` package
(`app/src/main/java/it/palsoftware/pastiera/legacy/`).
