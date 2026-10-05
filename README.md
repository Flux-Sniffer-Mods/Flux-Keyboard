# Flux Keyboard

**The companion app for the Unihertz Titan 2 Elite.** A keyboard first, built around its physical
keys and trackpad, and an app that makes the rest of the phone better too: the
keyboard light, screen size per app and shortcuts for things the phone hides away, all without
root. It works on any Android phone with a hardware keyboard, and is at its best on the Titan 2 Elite.

<p align="center"><img src="docs/screenshots/hero.png" alt="Flux Keyboard's emoji pages in a chat on the Titan 2 Elite" width="420"></p>

> **Flux Keyboard** is an unofficial fork of [Pastiera](https://github.com/palsoftware/pastiera),
> created by Andrea Palumbo (PalSoftware) and developed by Andrea Palumbo, Patrick Zauner and the
> Pastiera contributors. Most of what makes it a keyboard is their work, credited
> [below](#built-on-pastiera). Flux Keyboard is not affiliated with or endorsed by the Pastiera
> team, so please report problems to [this repository](https://github.com/Flux-Sniffer-Mods/Flux-Keyboard/issues),
> not upstream.

Flux Keyboard is up to date with **Pastiera 0.86**, Pastiera's final planned feature release, and
merges Pastiera's later changes as they land. Pastiera keeps receiving security fixes, and its
development continues as [Plektra](https://github.com/pkb-rocks/plektra).

## Highlights

### For the Titan 2 Elite
- **Trackpad swipes**: swipe left, up or right on the keyboard to pick a suggestion, or down to delete a word, in every app while you type; it learns how far you swipe.
- **Keyboard light, no root (Shizuku)**: on with the screen and off with it, following the screen's brightness as it changes (adaptive brightness and fades included), or flashing for notifications.

<p align="center">
  <img src="docs/screenshots/shizuku-light.png" alt="Keyboard light settings in Shizuku extras" width="300">
</p>

- **Screen size presets** for the whole phone, each applied with one tap: Default (300 dpi), Tablet and Desktop (about 200 dpi on the Titan 2 Elite).

<p align="center">
  <img src="docs/screenshots/screen-size.png" alt="Screen size presets, each with its own Apply button" width="300">
</p>

- **Screen size per app**: each app at its own size, kept through the recent apps screen and back to yours when you leave. Plus shortcuts to force-stop the app in front, toggle Battery Saver or cut an app off the network.

<p align="center">
  <img src="docs/screenshots/screen-size-per-app.png" alt="Screen size per app: Discord at Desktop, Instagram at Tablet, WhatsApp at Default" width="300">
</p>

- **The same app at two sizes**: Instagram at the phone's own size, then at Desktop, with more on the screen.

<p align="center">
  <img src="docs/screenshots/instagram-default.png" alt="Instagram at the phone's own size" width="270">
  <img src="docs/screenshots/instagram-desktop.png" alt="Instagram at the Desktop size" width="270">
</p>

- **Recommended settings**: the setup it's tuned with on the Titan 2 Elite, applied in one step and offered again after updates as a "from → to" list.

<p align="center">
  <img src="docs/screenshots/recommended.png" alt="Recommended settings offered as from and to" width="280">
</p>

- **Modifier LEDs** under the suggestions, with a colour each: Shift, Alt, SYM, Ctrl, and a fifth for the emoji key.

<p align="center">
  <img src="docs/screenshots/led-strip.png" alt="The status LEDs under the suggestion bar" width="520">
</p>

### A keyboard built around physical keys
- **Emoji, symbols and kaomoji as pages**: Q and P turn through every emoji by category, symbols (arrows, maths, currency, punctuation, shapes) and over 300 kaomoji, each going round on its own, opening on what you use most.

<p align="center">
  <img src="docs/screenshots/symbols.png" alt="Symbols as pages" width="420">
  <img src="docs/screenshots/kaomoji.png" alt="Kaomoji as pages" width="420">
</p>

- **Skin tones**: hold an emoji for its skin tones, one per key on the pages or in a row in the picker, and pick a default one.

<p align="center">
  <img src="docs/screenshots/skin-tones.png" alt="An emoji held for its skin tones" width="420">
  <img src="docs/screenshots/picker-skin-tones.png" alt="An emoji held in the picker for its skin tones" width="420">
</p>

- **Search everything**: emoji by name, every Unicode symbol, and kaomoji (here "double" among the emoji). Each search has its own tabs in the bottom bar: emoji categories, the symbols' groups (# → ∑ € § ★ ♪) and the kaomoji's moods (Joy, Love, Sad, Mad and more), each jumping straight to its place.

<p align="center">
  <img src="docs/screenshots/emoji-search.png" alt="Emoji search: double, with the emoji categories in the bar" width="280">
  <img src="docs/screenshots/symbol-search.png" alt="Symbol search, with the symbols' groups in the bar" width="280">
  <img src="docs/screenshots/kaomoji-search.png" alt="Kaomoji search, with their moods in the bar" width="280">
</p>

- **Kaomoji by mood, action or name**: happy, sad, lenny face and hundreds more.

<p align="center">
  <img src="docs/screenshots/kaomoji-happy.png" alt="Kaomoji search: happy" width="280">
  <img src="docs/screenshots/kaomoji-sad.png" alt="Kaomoji search: sad" width="280">
  <img src="docs/screenshots/kaomoji-lenny.png" alt="Kaomoji search: lenny" width="280">
</p>

- **GIFs**: search them from the emoji pages or the picker, with quick searches (LOL, Love, Sad, Wow, Yes, No, Bye) in the bar, and star one to keep it in your **favourites** (★), with the ones you sent last under them.

<p align="center">
  <img src="docs/screenshots/gif-search.png" alt="GIF search: cat" width="420">
  <img src="docs/screenshots/gif-favourites.png" alt="GIF favourites and recents" width="420">
</p>

- **Tap SYM or the emoji key** for one symbol or emoji, with no screen in the way; a dedicated **emoji key** (Right Shift by default) for the picker.
- **Shortcuts everywhere**: Ctrl+Z undo in any app, the same app shortcuts in every app, Ctrl+Shift+Q/W/E to pick a suggestion, Enter that sends or adds a line the way each app expects, and Nav Mode for arrows on the letters.

<p align="center">
  <img src="docs/screenshots/app-shortcuts.png" alt="App shortcuts: the same standard combos in every app" width="300">
</p>

- **Shift, Alt and Ctrl your way**, side by side: what a tap does, whether two taps lock it, what Space does, whether it lets go after use, and Backspace deleting forwards.

<p align="center">
  <img src="docs/screenshots/modifiers.png" alt="Shift, Alt and Ctrl side by side" width="320">
</p>

- **A quick launcher that does more**: apps, their own shortcuts (Discord's "New message"), contacts to call or message, websites, Termux scripts and tasks, and Niagara search if you prefer it.

<p align="center">
  <img src="docs/screenshots/quick-launcher.png" alt="The quick launcher: Discord and its own New message shortcut" width="320">
</p>

### Typing that keeps up
- **Suggestions as you type**, in the bar above the keys.

<p align="center">
  <img src="docs/screenshots/suggestions.png" alt="Suggestions in the bar while typing" width="520">
</p>

- **Spell checking in every app**, working with Gboard's corrections, plus password managers' autofill chips, **one-time codes** from your notifications and copied passwords offered without ever being shown.

<p align="center">
  <img src="docs/screenshots/autofill.png" alt="Bitwarden's autofill chips in the suggestion bar" width="520">
</p>

- **Learns as you go** (words you use, the emails and numbers you type) and **forgets when asked**: Incognito typing and **Offline mode**, together as a private mode on one key.
- **Clean links**: tracking and Google, Facebook and Instagram redirects gone from what you copy and paste.
- **Small things right**: capitals after a full stop or an emoticon, no stray spaces in emails, web addresses or numbers, emoticons that keep their shape, and voice input that keeps listening.

### Yours
- **A background picture** you place behind the keys.

<p align="center">
  <img src="docs/screenshots/background-picture.png" alt="A picture behind the keys" width="480">
</p>

- **Themes**: one for light mode and one for dark, colours from your wallpaper, and every colour editable.

<p align="center">
  <img src="docs/screenshots/theme-picker.png" alt="Choosing themes: following the system, wallpaper colours and a background picture" width="300">
  <img src="docs/screenshots/theme-colours.png" alt="Editing a theme's colours" width="300">
</p>

- **Edit layouts in the app**, and keyboard-free apps like Termux:X11 and Niagara that still get the keys (and the LEDs).
- **Terminal mode** for Termux: the keyboard stays out of sight with just its LEDs showing, Alt and SYM type their symbols, Ctrl stays Ctrl, and Termux is set up for the keyboard with one pasted command.

<p align="center">
  <img src="docs/screenshots/terminal-mode.png" alt="Terminal mode settings: the LEDs, the emoji key and Termux set up for the keyboard" width="300">
</p>

- **A tutorial** that sets up the extras for you, and **updates from the app**, full releases or dev builds.

<p align="center">
  <img src="docs/screenshots/tutorial.png" alt="The tutorial's welcome page" width="280">
</p>

Everything else, including per-app exact typing and languages, automatic Shift by field type,
snippet placeholders and voice input that keeps listening, is in the [changelog](FORK_CHANGES.md).

## Built on Pastiera

These are the Pastiera team's work, which Flux Keyboard builds on and keeps. Thank you to Andrea
Palumbo, Patrick Zauner and every Pastiera contributor.

**Typing and modifiers**
- Long press for Alt or Shift characters, with configurable timing.
- Shift, Ctrl and Alt as one-shot or locked (double tap), configurable latching, and clearing Alt on space.
- Multi-tap for keys with several characters (for example Cyrillic), and bounce keys.
- Standard shortcuts: Ctrl+C/X/V/A, Ctrl+Backspace, arrows on Ctrl+E/S/D/F or I/J/K/L, selection, Tab, Page Up/Down and Esc, all customisable.
- **Nav Mode**: double tap Ctrl outside text fields for arrows and many more mappings, with word navigation and media controls.
- Double space for a full stop and a capital, configurable punctuation spacing (French spacing, brackets, commas) and smart quotes.

**Layouts and languages**
- QWERTY, AZERTY, QWERTZ, Greek, Arabic, Russian and Armenian phonetic transliteration and more, with Alt maps for the Titan 2, Titan 2 Elite and original Titan.
- Layout switching with a tap on the language code, Ctrl+Space or Alt+Enter; JSON import and export with a preview.
- The layout web editor at [pastierakeyedit.vercel.app](https://pastierakeyedit.vercel.app/).
- A translated interface (English, Italian, German, Greek, Spanish, French, Armenian, Polish, Russian, Ukrainian, Vietnamese) and the onboarding tutorial.

**Status bar, symbols and variations**
- The compact status bar with modifier LEDs, the variations and suggestions bar, and Solderina, the smallest bar (Pastierina in Pastiera).
- The Titan 2 Elite's rounded-display geometry, its display contour calibration and Pastiera's contour LEDs, which Flux Keyboard's contoured LEDs grew from.
- SYM pages for emoji, symbols and the clipboard, usable by touch or keys, with an in-app SYM editor, emoji grid and Unicode picker.
- The variations bar: accents of the last letter or static sets, its editor, and dragging it as a swipe pad to move the cursor.
- Clipboard history with pinned items, hidden while the phone is locked.

**Suggestions and corrections**
- Dictionary suggestions and auto-correction, suggestions from several dictionaries, learned next words, and adding words from a swipe or a substitution.
- The user dictionary with search and editing, per-language substitutions and the shared "Pastiera Recipes".
- Snippet expansion, and emoji and symbol shortcodes.
- Native trackpad gestures on the Titan 2 and Titan 2 Elite (directly or through Shizuku), with separate sensitivities.

**Apps and extras**
- Launcher shortcuts (press a letter to open an app), power shortcuts with SYM anywhere, and the QuickLauncher with Niagara search.
- Enter behaviour per app.
- Speech input from Alt+Ctrl or the microphone on the bar.
- The on-screen keyboard mode with themes, a theme editor, per-app themes, presets, a number row and long-press layers.
- Clicks Power Keyboard support: controls, firmware status and SYM profiles.
- Backup and restore in a ZIP (settings, layouts, variations, SYM and Ctrl maps, dictionaries, themes and typing sounds), Android auto-backup, and the searchable settings with shareable links.

The full list of what Pastiera 0.86 added over 0.85 is in the [changelog](FORK_CHANGES.md#from-the-pastiera-team-085-to-086).

## Installation

1. Download the APK from the [latest release](https://github.com/Flux-Sniffer-Mods/Flux-Keyboard/releases/latest), or a dev build (marked Pre-release) from [all releases](https://github.com/Flux-Sniffer-Mods/Flux-Keyboard/releases).
2. Android Settings → System → Languages & input → Virtual keyboard → Manage keyboards.
3. Enable "Flux Keyboard" and pick it when typing.

Flux Keyboard (app ID `io.github.fluxsniffermods.fluxkeyboard`) installs next to Pastiera and
doesn't replace or update it.

## Requirements

- Android 10 (API 29) or newer.
- A phone with a physical keyboard (profiled on the Unihertz Titan 2 and Titan 2 Elite, adaptable with JSON layouts).

## Building

- Build a debug APK with `./gradlew :app:assembleStableDebug` and run the tests with `./gradlew :app:testStableDebugUnitTest`.
- Signed builds come from `.github/workflows/fork-build.yml`, picked by the branch it runs on:
  - **`flux-release` (the default branch): full releases** such as `1.0.0`, tagged `flux/v1.0.0`. The version is the newest one in the `"releases"` list of `app/src/main/assets/fork/whats_new.json`, which also records when it was built.
  - **`flux-dev`: dev builds** such as `1.0.1-flux.202610061200`, named after the next patch release (so the next full release, patch or minor, supersedes and installs over them) plus the build time, published as pre-releases.
- Each release lists only what changed since the build before it (a full release since the previous full release, a dev build since the previous build of either kind), from the What's new entries' `"after"` times.
- The repository's "Latest" release is always the latest full release. The app's update check reads the release tags: Stable offers full releases, Dev offers both.
- Dev work goes on `flux-dev` as individual commits, one per change, and Pastiera's changes are merged into it as they land. For a full release, add it to `"releases"`, fold the commits since the last full release into category commits, move `flux-release` up to the result and run the workflow on `flux-release`. Commits at or below `flux-release` are never rewritten.
- A full release deletes the dev builds before it (their releases and tags) when it publishes; builds run one at a time.
- `tools/find-keyboard-gesture-page.sh` (as root from Termux, with the page open) prints which screen a phone's keyboard gesture settings are.

Pastiera's release, nightly and CI workflows aren't kept: Flux Keyboard builds only with
`fork-build.yml`. When Pastiera changes them, a merge keeps them deleted.

## Contributing

Issues and suggestions for Flux Keyboard go to [this repository](https://github.com/Flux-Sniffer-Mods/Flux-Keyboard/issues).
Pastiera itself now accepts security, compatibility and maintenance changes, with feature work in
Plektra.

Flux Keyboard follows [Pastiera's forking policy](https://github.com/palsoftware/pastiera#forking-policy):
its own name, application ID, update feed and branding, the copyright and licence notices kept, and
no claim to be an official Pastiera release.

## Licence and credits

Pastiera is licensed under the [GNU General Public License v3](LICENSE), and Flux Keyboard is
distributed under the same licence. Pastiera was created by Andrea Palumbo (PalSoftware) and is
developed by Andrea Palumbo, Patrick Zauner and the contributors credited in the app's About screen
and in the [upstream repository](https://github.com/palsoftware/pastiera). Third-party components
are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md), and the fork's changes in
[FORK_CHANGES.md](FORK_CHANGES.md).

The Titan 2 Elite's keyboard light through Shizuku (the vendor service's timeout, the
never-off value and the broadcast that turns the light off) follows what
[PhysiBoard](https://github.com/brobata/physiboard) by brobata found and documented on the
phone. Thank you to brobata.

If you enjoy Flux Keyboard, consider supporting the people it's built on:
[Pastiera on Open Collective](https://pastiera.eu/donate) and
[Andrea Palumbo on Ko-fi](https://ko-fi.com/palsoftware).
