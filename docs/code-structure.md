# Code structure

The app's sources live in `app/src/main/java/it/palsoftware/pastiera/`. The package name is
upstream's and stays, as Android identifies the keyboard, its services and its saved settings by it.

## Top level

Android's entry points and app-wide pieces only: the application, the activities and services
the manifest names (their names can't move without Android forgetting that the keyboard is
enabled), and:

| File | What it holds |
|---|---|
| `SettingsManager.kt` | Every preference key, default and shared settings type |
| `SettingsManager<Area>.kt` | That area's settings, as `SettingsManager` extensions: Themes, SoftwareKeyboard, Titan2Elite, Modifiers, SoundHaptics, TextInput, Suggestions, Variations, SymEmoji, StatusBar, Trackpad, NavMode, Launcher, Layouts, Apps, Clicks, Accessibility |
| `ImeIdentity.kt` | The keyboard's identity in Android's input method lists |
| `BuildInfo.kt`, `FeatureStatus.kt`, `OfflineMode.kt`, `RestrictedSettings.kt`, `ShizukuStatus.kt` | App-wide state and checks |

## Packages

| Package | What it does |
|---|---|
| `inputmethod` | The keyboard service, event routing and the status bar controller |
| `inputmethod.keys` | Key filters (bounce, accidental presses), multi-tap and physical key resolution |
| `inputmethod.launcher` | Launcher shortcuts and opening the quick launcher |
| `inputmethod.aospkeyboard` | The on-screen keyboard and software keyboard mode |
| `inputmethod.statusbar`, `inputmethod.ui` | Status bar buttons, LEDs, the variation bar, the emoji picker |
| `inputmethod.suggestions`, `inputmethod.expansion` | The suggestion bar, snippets and shortcodes |
| `inputmethod.subtype` | Input styles registered with Android |
| `inputmethod.trackpad`, `inputmethod.voice`, `inputmethod.telex` | Trackpad gestures, voice input, Vietnamese Telex |
| `core` | Modifier, nav mode and SYM state; `core.suggestions` for dictionaries, suggestions and auto-replace |
| `data` | Layouts, key mappings, emoji, symbols, GIFs and variations loaded from assets and files |
| `settings` | Settings screens, setting links and the settings search entries |
| `theme` | Keyboard themes, background pictures and LED colours |
| `sym` | SYM, emoji and variation editors and pickers |
| `apps` | Per-app behaviour, app pickers and launcher shortcut screens |
| `device` | Titan 2 Elite and hardware keyboard settings, trackpad gesture settings |
| `clicks` | Clicks Power Keyboard support (Bluetooth, buttons, charging) |
| `autocorrect`, `layout`, `tutorial` | Their settings screens |
| `commands`, `shortcuts` | Quick launcher commands and app shortcuts |
| `adb` | Features run through Shizuku or ADB (keyboard light, screen size) |
| `backup`, `clipboard`, `dictionaries`, `otp`, `spellcheck`, `update` | As named |
| `legacy` | Code kept only to read what earlier versions saved; see below |

## Legacy

`legacy` (in the app) converts settings and files saved by earlier versions to today's format;
nothing new is written in those shapes. Upstream files the build doesn't use, and app code and
resources nothing uses any more, are kept in the repository's `legacy/` folder, outside the build.
