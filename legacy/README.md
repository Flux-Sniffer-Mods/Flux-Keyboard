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
| `app-code/` | Upstream app code nothing used any more (an emoji list adapter, a trackpad debug overlay) |

Code the app still needs only for backwards compatibility lives in its `legacy` package
(`app/src/main/java/it/palsoftware/pastiera/legacy/`): settings and files saved by earlier
versions, read and converted to today's format (old Alt binding, SYM page order, launcher
shortcuts, removed themes, nav mode defaults, the German layout default, custom layouts and
dictionaries saved under old names), and shortcuts apps still return the pre-Android 8 way.
