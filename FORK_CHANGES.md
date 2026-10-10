# Flux Keyboard changelog

Flux Keyboard is up to date with **Pastiera 0.86** (September 2026), Pastiera's final feature release; Pastiera keeps receiving security fixes, and its development continues as [Plektra](https://github.com/pkb-rocks/plektra). This page lists first what Flux Keyboard adds, then [what the Pastiera team built between 0.85 and 0.86](#from-the-pastiera-team-085-to-086), which Flux Keyboard includes. New Pastiera changes are merged into the dev builds as they land, so Flux Keyboard is never behind Pastiera.

Flux Keyboard is an unofficial fork of [Pastiera](https://github.com/palsoftware/pastiera), the physical-keyboard input method created by Andrea Palumbo (PalSoftware) and developed by Andrea Palumbo, Patrick Zauner and the Pastiera contributors. All credit for Pastiera itself goes to them; this page lists only what the fork changes. Flux Keyboard is not affiliated with or endorsed by the Pastiera team, and like Pastiera it is licensed under the GNU GPL v3.

The fork is tuned for the Unihertz Titan 2 Elite and works on any phone with a hardware keyboard. It installs alongside Pastiera (app ID `io.github.fluxsniffermods.fluxkeyboard`) and starts with a few recommended settings. Sections and items are ordered with the biggest differences first.

## Emoji, symbols and GIFs

- **Emoji picker key**: a dedicated key (Right Shift by default), assigned by pressing it. A modifier key opens the picker when released, so it still works in chords like Ctrl+Shift+Q.
- **Hold or tap the emoji key**: hold it and press a key to type that key's emoji from the emoji layer; optionally, one tap makes the next key do the same, a second tap opens the emoji screen, and Back cancels it. Held without choosing anything, it just lets go.
- **Tap SYM for one symbol** (option): one tap makes the next key type its symbol without opening the symbols; a second tap opens them, and Back cancels it. Holding SYM works as before, and held without choosing anything it just lets go. (Modifiers & SYM > Tap, lock & long press.)
- **GIF search** (KLIPY) in the picker and on the emoji layer, with favourites, recents and caching (a search's results are kept for a week after last use).
- **Symbol search** across every Unicode symbol.
- **Kaomoji** on the symbols page's P key: over 300 in Gboard's groups, a page of 24 at a time on taller keys, each key's letter under its kaomoji (P for the next page, Q for the one before), with the ones you typed last on a Recents page first. Pages stay open after a pick unless you choose otherwise (Close after picking).
- **Layers as pages**: the emoji layer, the symbols page and the kaomoji open on a **default page**, Developer's pick of the most used until you change it with the pencil in the bottom-left corner, with your recents one page back on Q (topped up with their own; or open on the recents, per layer: Start on recents). Search is on A, GIFs or the kaomoji on L, and Q and P turn through pages of every emoji by category and every symbol, going round at either end; the keys with a job keep it.
- **Skin tones**: hold an emoji on the layer's pages for its skin tones, one per key, and choose a default skin tone for the picker and the layer.
- **Kaomoji search**: ⌕ on the first kaomoji page searches them by mood, action, expression or name (lenny face, table flip, shrug).
- **Tabs for each search**: GIF search has favourites (★) and quick searches (LOL, Love, Sad, Wow, Yes, No, Bye), symbol search the symbols page's groups, and kaomoji search their moods (Joy, Love, Sad, Mad, Wow, Shy, Hug, Hi). Tapping one jumps straight to it; emoji search's category tabs clear the search and jump to the category.
- **Recent emoji first** on the emoji layer (option): the ones you used last take its first keys and its own move along; the symbols page has Recents too.
- Every panel (emoji, symbols, kaomoji, GIFs, clipboard) has corner buttons as wide as the bar's side buttons, reaching the display's sides, with its bottom keys centred between them; the GIF page's KLIPY credit has a line of its own above the GIFs.
- **Emoji layer**: search, a Recents key, and **profiles** for common situations (chatting, work, social…) that can follow the app you're in.
- Search, Recents and GIF keys on the layers have a tint of their own, and nothing in the default layers sits under them.
- Emoji beyond the system font, and names for every emoji in search.
- A search key (⌕) on every panel; letters type the layer's mappings unless type-to-search is on. What opens ready to type, the search key, what Enter picks and recently used first have a Search & Enter page of their own.

## Typing

- **Learn words you use often**: a word that isn't in the dictionary goes into it once you've typed it three times, so it's suggested and never auto-corrected (on by default).
- **Remember emails and phone numbers**: ones you type yourself are kept in your dictionary and offered as chips in email and phone fields; tap one to fill the field. Nothing pasted or filled in, nothing from password fields (on by default).
- **Incognito typing**: learn nothing when an app asks (private tabs), or always: no new words, emails or numbers, next-word predictions, word use, or recent emoji, symbols and GIFs. What was learned before is still offered. Words are never learned from password, email or web address fields.
- **Ctrl + Shift + D adds the last word** you typed to the dictionary (Auto-correction > Dictionary, on by default), and the suggestions' **“add to dictionary”** chip can be switched off.
- **Spell checker**: Flux Keyboard as Android's spell checker, underlining typos in any app, and reading words with apostrophes whole: contractions (couldn't, won't, can't) and elisions (l'homme, dell'anno).
- **No automatic spaces after punctuation** in email, sign-in, web address and password fields, nor inside numbers, times and decimals (1,000, 12:30, 3.14), and a clearer punctuation spacing dialog: each mark named, with No space before and Space after columns.
- **Emoticons keep their shape**: punctuation typed straight into more, as in :-) ;( :D, gets no space after it; if a word follows (:Do) the space comes back. Double Space for a full stop is off by default.
- **Inline autofill**: password manager chips (Bitwarden, say) in the suggestion bar, centred and drawn like its suggestion buttons in your theme's colours.
- **One-time codes**: a code from a notification (sign-in, bank, delivery) is offered as a chip in the next text field for three minutes, and not again once typed. The code is the number next to a word like "code" or "PIN" (whole words: "shipping" and "security briefing" don't count), so order and account numbers aren't mistaken for it. Off until you give it notification access.
- **Pick suggestions from the keyboard**: Ctrl+Shift+Q, W or E takes the left, middle or right suggestion (Ctrl+1/2/3 on keyboards with a number row). Either Shift works, including Right Shift while it's the emoji key.
- **Trackpad swipes**: optionally, swipe left, up or right anywhere on the trackpad for the left, middle or right suggestion, and choose whether a swipe left or down deletes the previous word. Short and slanted swipes count, whichever way they mostly go. Left and right swipes have their own sensitivity. While you type, the swipes go to the keyboard rather than scrolling the app underneath, and **keyboard swipes per app** (with Scroll assistant on for every app) chooses the apps they may scroll, or the ones they're kept from (Flux Keyboard's own settings among them), with Select all.
- **Shift + Backspace deletes forwards** on the Titan too (the setting now sees a held Shift, and is among the recommended settings).
- **Ctrl+Z undoes typing in any app**, from the keyboard's own edit history; Ctrl+Shift+Z or Ctrl+Y redoes it.
- **Select from the cursor**: a key shortcut turns on Nav Mode with the selection anchored at the cursor, so arrows, Home, End and cursor swipes extend it.
- **Private mode**: one key shortcut (or the quick launcher) turns on Incognito typing everywhere and Offline mode together.
- **Suggestion swipes learn from you**: a pick you undo makes them need a little more, a swipe that falls short a little less.
- **Works with Gboard's spell checker**: when Android's spell checker is another app's, its corrections join Flux Keyboard's suggestions.
- **Backspace undoes an auto-replace** and keeps the space after it; text replacements can be undone with auto-replace off too.
- **Automatic Shift by field type**: choose which kinds of text field start with a capital (text, names and addresses by default; search, links and email addresses off). A kind that's off gets no automatic Shift, even when an app asks for capitals. Fields that don't say what they are have an Other kind of their own (off until you choose it).
- **Paste suggestion**: what you just copied, offered in the next text field (long text is shortened without breaking an emoji). In a password field it's offered as ⎘ •••••••• (never the text itself), including a password you copied from a password manager; those stay in memory only and never go into the clipboard history.
- **Clean copied links**: a link you copy loses its tracking (utm_, fbclid, YouTube's si, is and ab_channel…) on the clipboard itself, so it's clean wherever it's pasted, and opens the full site rather than the mobile one. Google, AMP, Facebook, Instagram and YouTube redirects become the link they lead to, and pasting with Ctrl+V cleans too. Password managers' copies are left alone.
- **Emoji suggestions**: an emoji for the word you're typing.
- **Exact typing** (Apps): in the apps you pick (SSH clients, code editors, AI agents) nothing rewrites what you type: no auto-correct, text replacements, auto-capitals, double-space full stop or automatic spaces. Optionally also wherever an app itself asks for no suggestions.
- **Remember the language per app**: each app gets back the language you last typed in there.
- **Snippets** fill in `{date}`, `{time}`, `{datetime}`, `{isodate}`, `{day}` and `{clipboard}`.
- **Smart toggle**: Alt and Ctrl switch themselves off by context.
- **Voice input keeps listening** through pauses until you stop it or stay silent.
- **Ctrl+Shift+Space** switches language backwards.
- No automatic Shift in scripts without capitals (Thai, Arabic, CJK…).
- **Bold suggestions** option.

## Keyboard layouts

- **Edit layouts in the app**: tap a key to change what it types with and without Shift, then save, restore the original, save as a new layout or **export** it as JSON. No web editor needed.

## Apps and the quick launcher

- **App shortcuts**: the same shortcuts in every app, suggested per category, plus each app's own launcher shortcuts and settings (Ctrl+Alt+1–4, Ctrl+,).
- **Quick launcher**: the built-in one by default, in the keyboard's theme colours, with apps' own long-press shortcuts ("New message", "Scan QR code") as results.
- **Flux Keyboard's own quick launcher shortcuts** for the apps in its app shortcut and Enter lists: New message, New post, New note or Compose email, and Search, each only where the app accepts it. They can be turned off on their own (Shortcuts for compatible apps).
- **Add a shortcut**: put a shortcut an app offers for the home screen (a contact's direct dial, a bookmark, a settings page) in the quick launcher's results (Apps > Quick Launcher), or long-press an app in the quick launcher and pick Add a shortcut. Flux Keyboard also makes the common ones itself, for apps that only offer theirs to the home screen: call or message a contact (calling straight away if you allow the phone permission), a website, and **Termux tasks** (scripts in ~/.shortcuts, run as Termux:Widget runs them; needs allow-external-apps in Termux). Your Termux:Widget scripts and tasks are also listed in the quick launcher on their own, kept up to date (option).
- **Niagara search as the quick launcher** (option): Back, the key or the gesture, before opening anything returns to the app you opened it from, and on Niagara's home screen the built-in quick launcher opens instead.
- **Search bars wait for typing** (option): when an app opens with its search bar focused, the keyboard bar stays hidden until you type or tap the bar (also palsoftware/pastiera#319).
- **Terminal mode**: Termux gets the keyboard's Alt and SYM, a real Ctrl, a hidden keyboard and a choice of what the emoji key does (the extra keys by default, or a key it sends, Alt included). Nav Mode sends its arrows straight to the terminal, and swipes on the keys move the cursor.
- **Termux set up in one paste**: a command, copied for you with Termux opened (in the tutorial and Terminal mode), lets other apps run commands (the quick launcher's scripts need it) and removes Termux's own extra-keys row and its text box, which a phone with keys doesn't need. It also offers to start **Shizuku at boot** with Termux:Boot: it waits for Wi-Fi, pairs Termux with wireless debugging once and finds Shizuku wherever it's installed.
- Pastiera's **Enter per app** gets standards by app category: chat apps send with Enter (Shift + Enter for a new line), email and note apps keep Enter for new lines and send with Ctrl + Enter, and every installed chat, email and notes app is listed to change. Search boxes and one-line fields always get their own action (search, go, next).
- **Terminal apps** get no microphone button.
- **Hidden-keyboard apps** (Niagara Launcher and Termux:X11 by default; others from the tutorial), with LEDs and panels per app; only the LEDs that are lit show there, where the LEDs usually are. Niagara keeps its LEDs and the emoji and symbols panels by default, for quick replies from notifications. The keyboard bar no longer pops up on Niagara's home screen (palsoftware/pastiera#319). SYM chords reach the keyboard there, so SYM + Space opens the quick launcher instead of the symbols panel.
- The **quick launcher** opens from other apps (key mappers, Tasker) and the app icon.
- **Linux desktop** keyboard layout from the keyboard's Alt map and SYM page.

## Built-in shell and gaming mode

- **Built-in shell**: pair once with wireless debugging (the code is typed into a notification, digits on the keys), and Flux Keyboard runs its own developer shell, with no Shizuku or Termux needed. After a restart it waits for Wi-Fi, switches wireless debugging on just long enough to start, then off again, and keeps running without Wi-Fi. Everything that used Shizuku uses it first; Shizuku still works when it isn't set up.
- **Gaming mode** (Shell access > Gaming mode): in apps with a game profile, the keys and trackpad play the game. **Gamepad** profiles put the d-pad on WASD, an Xbox controller's Y X B A on O K P L in its places (O on top, K left, P right, L below), the shoulders on Q E Z C, Start and Select on Enter and Backspace, and a stick on each trackpad half; **Keyboard and mouse** profiles keep WASD, turn the top row into the number row and make the trackpad's right half a mouse (a tap clicks); **MMO** profiles move on D Z X C instead, so the whole top row is the number row. Every key and both trackpad halves are remappable per profile, keys left alone reach the game as they are, and typing into a game's text field works as usual. Profiles can be made for each game GameNative, GameHub or GameHub Lite has as a shortcut or installed in its Steam folder, switched from gaming mode's notification, and put on the home screen: the shortcut switches the profile on and starts the game (GameNative's straight away). Each game has one Screen choice: as the app wants (GameNative, with its own Portrait mode per game), kept upright (emulators), or for apps that turn sideways whatever they're told (GameHub) a landscape-shaped screen with the phone upright, clear of the camera cutout. Keys are mapped on a drawing of the keyboard or of a controller. Make the game folders sets up Download/Flux Keyboard/Games with a folder for GameNative, GameHub, Dolphin, PPSSPP, Azahar and Eden: GameNative's and GameHub Lite's Export for frontend files and emulator games there (and in ES-DE's ROMs folder) are listed too, each started straight into the game, the way ES-DE does. Folders of your own (the ones set in a launcher or emulator) can be added too, each set to one launcher or emulator or read by its folders' names. For games started from inside their launcher or emulator, each profile can be saved in that app's own form (a GameNative controls profile named after the game, a Dolphin controller profile, the game's own PPSSPP controls, or the keys to bind in Azahar and Eden) with how to load it there, the app then mapping the keys itself; gaming mode also switches to the running game's profile where it can tell which game it is (PPSSPP's last game, or the game file another frontend started). Games can also be added one at a time, like GameNative's and GameHub's custom games: the launcher or emulator it was added to, then its file, exported file or game ID. GameNative only takes real controllers, so Flux Keyboard saves a controls profile for it (every button and both sticks, invisible) and opens GameNative with the steps to import and pick it; gaming mode then presses it as an Xbox controller; for other games that only take sticks by touch (GameHub, emulators), keys and stick circles can be placed on the game's own on-screen controls, drawn over it. A trackpad half can also hold W A S D or the arrow keys as it's pushed, for emulators that bind keys to a stick (Dolphin, PPSSPP, RetroArch take only real controllers' sticks). Dolphin, PPSSPP, Azahar (and Citra, Lime3DS) and Eden (and yuzu's forks) get their on-screen gamepad's spots straight away, read from their own code or settings. The volume keys are the left shoulder and trigger, and other buttons can be added by pressing them. Asked for in [palsoftware/pastiera#138](https://github.com/palsoftware/pastiera/issues/138).

## Titan 2 Elite, status bar and LEDs

- **Minimal mode** (Look & sound > Keyboard bar): no keyboard bar in any app, the status LEDs optional, while the keys work as usual and the emoji and symbols pages still open on their keys. Inspired by [Numen](https://github.com/jlo-aug/numen) by jlo-aug. The bar is otherwise **Solderina**; the older extended bar is retired. The minimal mode shortcut and menu button switch it on the spot.
- **Extra keys**: Esc, Tab, Ctrl, Alt and arrows in the bar's place, Termux style, from the menu bar, a shortcut or the emoji key in terminal apps (its default there). While the row is open, Q to P press its keys, and its Ctrl or Alt goes with the next key you press. Text fields get their own set: arrows, Home, End, select all, cut, copy and paste. The menu bar starts with extra keys, undo, redo and the clipboard.
- **Keyboard swipes through Shizuku**: with Shizuku running, swipes on the keys reach the keyboard in every app, whether or not the phone's Scroll assistant scrolls it. It's the default for Shizuku users until a source is picked.
- **Recommended settings**: the configuration Flux Keyboard is tuned with, from its own Titan 2 Elite, offered again after each update as a list of what would change (from your value to the recommended one, Apply all or Keep mine). A short list that suits nearly everyone (modifiers, suggestions, LEDs; on the Titan 2 Elite its trackpad swipes and compact bar), applied on a fresh install or with the Apply button in Privacy & system, which then offers to go through the rest. Matters of taste (auto-correct, GIFs, colours, what the emoji key opens) are switches on the tutorial's Your choices page, and settings that need a permission or another app (one-time codes, Niagara search, hidden-keyboard apps) start off and are set up from its Extras page.
- **Trackpad swipes on the Titan 2 Elite**: they reach the keyboard only while the phone's Scroll assistant is on (Settings > Gestures > Keyboard gesture), which the trackpad tutorial and the phone trackpad settings row explain; Cursor assistant is optional, but its cursor mode takes the swipes. A suggestion swipe needs only about one key's width (a tenth of the trackpad), so sliding from one key onto the next picks, however slowly within half a second.
- A status bar fitted to the rounded display: **straight LEDs** and straight corner buttons by default, reaching down and out into the corners on every page (the bar, symbol and emoji pages), with filled corners, a 5 dp lift above the LEDs and room around the SYM screens. Or **contoured LEDs**, Pastiera's contour LEDs reworked: lit and unlit, they run round the display corners on two rails (Shift and Alt on the left; Shift or the emoji LED, Sym and Ctrl on the right) on every page at one height and the same length, drawn over everything, with the corner buttons following the same curve a small gap inside them. The display corners default to a shape calibrated on the phone, with smooth edges.
- The phone trackpad settings row opens the phone's **Scroll assistant** page directly.
- Every Titan 2 Elite setting on one screen, and a **phone trackpad settings** shortcut (also a quick launcher command) that opens the phone's Keyboard gesture page directly (Unihertz's own settings app, `com.agui.settings`), or Settings search with it ready to paste on other phones.
- **Per-LED colours**, a clear active-to-locked jump and an optional sweeping gradient when locked, set from one table with Off, Active and Locked columns. Shift is blue, Ctrl orange, Alt green, SYM purple and the emoji key pink by default.
- **SYM's LED** is lit while SYM is held and locked while it's tapped for one symbol or the symbols are open. A **fifth LED** does the same for the emoji key (on by default; Look & sound > Status LED colours); all five LEDs share the width equally.
- **Shizuku extras** (no root): the Titan 2's keyboard light on and off with the screen (never timing out while it's on), following the display's real brightness (adaptive brightness and fades included, read up to 120 times a second so fades stay smooth), or flashing for notifications, using the vendor calls [PhysiBoard](https://github.com/brobata/physiboard) by brobata documented; **screen size presets** (Default at 300 dpi, Tablet and Desktop at a smallest width of 601 and 860 dp), also as shortcuts, a reset that puts back the density alone, and **screen size per app**, picked from a searchable list of apps (the density changes as each app comes to the front, and carries through the recent apps screen so it doesn't redraw mid-animation, while a home app of your own such as Niagara has its own size); and shortcuts to force-stop the app in front, toggle Battery Saver or cut the app in front off the network. Each screen size has its own Apply button, chosen apps show their icons, and Shizuku can be started at boot.
- **Wallpaper colours** (option): the keyboard takes its colours from your wallpaper.
- **Background picture**: your own picture behind the keys, dragged, zoomed and rotated into place on a preview of the bar. With Auto colours the keys are shaded against it with text that reads, and Key opacity sets how solid they are. It's kept in backups.
- The keyboard follows the system's dark and light mode with Pastiera's Classic Midnight and Classic Cloud themes. The Keyboard theme page chooses the theme on the page itself, explains which theme is used when, and marks the one in use.
- A **customisable menu bar** (which buttons, in what order) with a GIF button, and a preview of it at its real height.

## Settings, tutorial and privacy

- **Flux Keyboard**: its own name, app ID, icon (a keyboard, in Niagara's icon packs too) and home screen fitted to the Titan 2 Elite, crediting and linking the original Pastiera. The compact mode is called **Solderina**.
- **A tutorial of its own**: one-step setup, every new feature, **Your choices** (auto-correct, double-space full stop, emoji suggestions, GIF search, what the emoji key opens, wallpaper colours, each a switch), a page for making it yours, and the extras that need a permission, on pages that scroll. The trackpad has its own up-to-date guide. After an update, **What's new** shows alone, lists only what's new since the version you had (nothing at all after an update with nothing new), and closes with ✕ or Done.
- **Updates from this fork**: Flux Keyboard checks this repository's releases, not upstream's, compares versions properly, and downloads and installs the update itself (Android asks before installing). Choose full releases only (always the latest, however many dev builds follow it), or dev builds too; a dev build starts on dev updates and a full release on releases. Dev builds are offered with their build time ("1.0.1 dev · 6 Oct 2026, 11:50").
- **Restricted settings**: features that need the accessibility service or notification access say when Android blocks them for apps installed from a file, explain step by step how to allow them (in settings and the tutorial), and open App info to lift the block. The tutorial's extras page always shows the steps.
- **Setting links**: hold a setting for a `fluxkeyboard://setting/…` link that opens it in this app, and release notes link to this changelog; nothing points at Pastiera's website.
- **Backups** include every setting, the background picture and the display corner calibration.
- Settings grouped by task, ordered by usefulness, related switches merged into tables (closing after a pick, search, layer pages, Shift/Alt/Ctrl behaviour, capitals, learning), searchable, with rows sized to their text, and choices that go together in one dropdown (which swipe deletes a word). Search keeps your query and your place when you open a result and come back, and nothing is focused on its own.
- Input Languages laid out like every other page.
- **Offline mode**: nothing in the keyboard goes online.
- Settings for hardware the phone doesn't have (Clicks keyboard, Titan 2 layout) stay hidden.
- A **debug export** (Copy or Share) with the last fields opened, password manager suggestions and Automatic Shift's decisions, text masked.
- **Developer options** gather the Dev builds update switch and calibration and debug tools; they're on by default in dev builds and off in full releases.
- **Shift, Alt and Ctrl side by side** (Modifiers & SYM): what a tap does, whether two taps lock it, what Space does, whether it lets go after use, and whether Backspace with it deletes forwards.
- **Pills for switches that belong together**: Learn as you type (words, emails and numbers), Capital after (a full stop, an emoticon) and Show as pages (emoji layer, symbols).
- **Defaults**: suggestion swipes at 100, Ctrl+Space as the only layout switch, and pages that stay open after a pick.
- Text replacement examples in your language ("dont → don't" in English).

## SYM layers and variations

- **Device SYM layer editor** with curated and custom profiles.
- The pencil on the symbol panels edits that layer's mapping; holding it on pages that have them edits the variations.
- Dev's choice static variations by default.

## From the Pastiera team: 0.85 to 0.86

Flux Keyboard includes everything the Pastiera team built for Pastiera 0.86 since their 0.85 release. That work is theirs:

- **Clicks Power Keyboard** support: controls, firmware status, and SYM profiles for Razr and Pixel.
- An **on-screen keyboard mode** based on AOSP, with themes, a number row, layout styles and long-press layers.
- **Keyboard themes** with an editor, draft themes, per-app theme assignment and transparent presets.
- **Titan 2 Elite geometry**: rounded corners on the keyboard and status bar, calibrated contours and a gapless mode.
- **Settings overhaul**, with search and shareable deep links.
- **Learned next-word suggestions**, bigrams, suggestions from several dictionaries, and adding words from substitutions and swipes.
- **Snippet expansion**, and emoji and symbol shortcodes.
- **Punctuation spacing** (French spacing, closing brackets, commas), smart punctuation and mid-word quote replacement.
- **Native trackpad gestures** on the Titan 2 and Titan 2 Elite, with separate sensitivities.
- **Original Titan** keyboard profile and Alt keymap.
- A **unified QuickLauncher** with Niagara search, and Home and document navigation actions.
- Configurable **modifier latching and indicators**, Alt+Enter layout switching, Shift for Nav Mode, **bounce keys** and tap haptics.
- **Greek translation**, and configurable status buttons for the compact mode (Pastierina, called Solderina here).
- Clipboard history **hidden while the phone is locked**.
- **Safer backups** (themes and typing sounds included) and many fixes: suggestions in Telegram, emoji search, Firefox accents, Ctrl shortcuts in number fields and more.
- **Contour LEDs** on the Titan 2 Elite and **Display contour calibration**, which Flux Keyboard reworks into its optional contoured LEDs (see above); straight buttons are Flux Keyboard's default. Pastiera's "Fill lower display corners" is Flux Keyboard's Fill corners setting.
- The menu bar shows **SYM while it is held**, and the navigation bar under the keyboard takes the keyboard's colour.
- A **software bill of materials** (CycloneDX) in release builds, exported from About.

Pastiera 0.86's hand-over to Plektra (its welcome page, update checks and About notices) doesn't apply to Flux Keyboard, which updates from its own releases.

## Upstream issues addressed

palsoftware/pastiera #108, #138, #217, #267, #278, #282, #292, #302, #310, #316, #317, #319.

## Builds

**One APK per language**: full releases come as `flux-keyboard-<version>.apk` (English) and `flux-keyboard-<version>-<language>.apk` for each of the 18 other dictionary languages. Each carries only its language's dictionary, opens in that language's translation where there is one, and updates to the same language's APK. Any other language's dictionary downloads the first time it's used, from Flux Keyboard's own `dictionaries-1` release, which keeps every APK small (about 17 MB, down from 48 MB). The single-language versions were inspired by [Numen](https://github.com/jlo-aug/numen) by jlo-aug.

Signed APKs come from the fork build workflow on GitHub Actions, in two kinds: **full releases** (such as `1.0.0`, tag `flux/v1.0.0`) from the `flux-release` branch, and **dev releases** (such as `1.0.1-flux.202610061200`, published as pre-releases) from the `flux-dev` branch. The repository's latest release is always the latest full release. A full release deletes the dev builds before it; each build's notes list what changed since the build before it; the notes also ship as `RELEASE_NOTES.md` in the build artifact. Builds run one at a time, and a successful build deletes the workflow runs before it (releases keep their APKs). Dev builds are named after the next patch release (after 1.0.0: `1.0.1-flux.<time>`), so the next full release supersedes them and installs over them, and a dev build's Dev update channel and Developer options stay on after it updates to a release. `tools/find-keyboard-gesture-page.sh` (root, from Termux) finds which screen a phone's keyboard gesture settings are. Flux Keyboard builds are signed with the fork's own key and don't update, or get updated by, official Pastiera.
