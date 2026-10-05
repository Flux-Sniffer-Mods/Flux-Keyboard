# Universal app shortcuts

Settings → Apps → App shortcuts. One set of standard key combos means the same
thing in every app. When an app documents its own shortcut for the action,
Pastiera sends that shortcut in place of the combo you pressed. When it doesn't,
a suggested intent can open the app's own screen instead (see "Suggested
shortcuts" below). There are presets for 300+ apps. Every one can be changed
per app, and you can add any other app and record its shortcuts yourself
(keyboard apps can't be added).

Code: `app/src/main/java/it/palsoftware/pastiera/shortcuts/AppShortcuts.kt`
(combos, documented presets, rules), `SuggestedAppShortcuts.kt` (generated
suggestions), `AppIntents.kt`, `AppShortcutSettings.kt` (storage) and
`PhysicalKeyboardInputMethodService.remapAppShortcut` (the key hook).

## Standard combos

| Combo | Action | Where |
|---|---|---|
| Ctrl+N | New (message, note, event, tab) | anywhere |
| Ctrl+F | Search or find | anywhere |
| Ctrl+O | Open or go to | outside text fields |
| Ctrl+R | Refresh | outside text fields |
| Ctrl+Enter | Send | anywhere |
| Ctrl+W | Close or finish | anywhere |
| Ctrl+Shift+R | Reply | outside text fields |
| Ctrl+Shift+A | Reply all | outside text fields |
| Ctrl+Shift+F | Forward | outside text fields |
| Ctrl+E | Archive | outside text fields |
| Ctrl+D | Delete | outside text fields |
| Ctrl+U | Mark unread | outside text fields |
| Ctrl+Shift+S | Star, flag, pin or bookmark | outside text fields |
| Ctrl+Z | Undo the last action | outside text fields |
| Alt+↓ / Alt+↑ | Next / previous item (message, tab, sheet, session) | anywhere |
| Ctrl+M | Menu or side panel | anywhere |
| Ctrl+/ | Keyboard shortcut help | anywhere |
| Ctrl+, | The app's settings | anywhere |
| Ctrl+Alt+1 to 4 | The app's own launcher shortcuts 1 to 4 | anywhere |

The combos follow common desktop habits where the apps agree: Ctrl+N, Ctrl+F,
Ctrl+R, Ctrl+W, Ctrl+Enter, Ctrl+/. Where the apps disagree, the combo is one
that none of the presets uses for something else.

Rules (`AppShortcutRemapper.resolve`):

- Only a standard combo is ever changed. Plain typing, Alt+letter symbols and
  every other combo go through untouched.
- Pastiera changes nothing when the app's own shortcut is already the standard
  combo. When the app has no shortcut for that action, a suggested intent is
  used if there is one and the app accepts it, then one of the app's own
  launcher shortcuts read on the phone; otherwise nothing changes.
- In a text field, Pastiera never sends a shortcut that would type a character
  (Gmail's `C`, Keep's `/`) or move the cursor (a plain arrow). It also skips
  actions meant for lists and message views.
- In a text field, only a held Ctrl counts, and only when "held Ctrl uses Nav
  Mode" is off. A latched Ctrl there keeps working as Pastiera's cursor and
  selection keys.
- Termux keeps Ctrl+letter for the shell. Its preset only maps session
  switching.

## Suggested shortcuts: the top apps in every category

Most apps publish no Android keyboard shortcuts. For those, a preset can **suggest** an intent
into the app's own screens, which Pastiera starts when you press the standard combo in that app:

- **New (Ctrl+N):** the app's share screen with nothing filled in, i.e. a new post, message or
  note. Used in Social, Communication, Productivity and Business apps, and in X.
- **Search (Ctrl+F):** the app's own search screen (`ACTION_SEARCH`), and for the best-known
  apps one of their own search links (e.g. `spotify:search`, `https://www.reddit.com/search/`,
  `https://x.com/search`, `geo:0,0?q=` for Google Maps).

These are suggestions, not documented shortcuts. Each intent is aimed at the app itself, and
Pastiera only starts it when that app on the phone accepts it; otherwise the key goes to the
app unchanged. A documented key shortcut always wins over a suggestion. You can turn
suggestions off (Settings → Apps → App shortcuts → Suggested shortcuts), or override them app
by app ("Don't remap", or record the app's own combo).

### Which apps

The apps are Google Play's own categories (US store, September 2026), ranked by downloads and
then by number of reviews: the top 100 in **Social** and the top 10 in each other main category.
Games, keyboards and system components (carrier services, accessibility suite and the like) are
left out, and so is Pastiera. `tools/app-shortcuts/crawl.py` collects the data from Google Play
(each category page plus "similar apps", with Social crawled deeper, 333 Social apps in all),
and `tools/app-shortcuts/generate.py` writes `SuggestedAppShortcuts.kt` and
`tools/app-shortcuts/play-ranking.json` (the ranking used).

Social is Play's Social category **plus a hand-picked list of social media apps**
(`HandPickedSocialApps.kt`). Google Play files some of the biggest outside Social: X and Quora are
in News & magazines, Snapchat in Communication, Pinterest in Lifestyle, LinkedIn in Business,
YouTube in Video players and Twitch in Entertainment. Those count as Social here, in the app list
and for Enter per app. Play's Social top 100 is mostly live-video and chat-room apps by download
count, so the hand-picked list also adds social media apps that rank below it: Bluesky, Mastodon,
Nextdoor, Substack, Weibo and Lemon8 (New opens their share screen, Search their search screen).
WhatsApp and Discord stay in Communication as messengers.

**Social (top 100):** Facebook (10B+), Instagram (5B+), TikTok (1B+), Facebook Lite (1B+), Likee (1B+), Instagram Lite (1B+), Bigo Live (500M+), ShareChat Status, Video & Live (500M+), Threads (500M+), XClub (500M+), VK (100M+), Tango (100M+), Reddit (100M+), Tumblr (100M+), OK (100M+), NGL (100M+), Moj (100M+), MeetMe (100M+), Litmatch (100M+), Moj Lite (100M+), Josh (100M+), Pi Network (100M+), Telegram X (100M+), Saya Lite (100M+), OmeTV (100M+), Chingari (100M+), LOVOO (50M+), Tagged (50M+), Waplog (50M+), BAND (50M+), MICO (50M+), SayHi Chat Meet Dating People (50M+), IMVU (50M+), Widgetable (50M+), Text Me (50M+), textPlus (50M+), Chamet (50M+), KakaoStory (50M+), Who (50M+), StreamKar (50M+), SoulChill (50M+), SUGO：Voice Chat Party (50M+), Omega (50M+), Weverse (10M+), Clubhouse (10M+), Kismia (10M+), MeYo (10M+), Between (10M+), HOLLA (10M+), Jeevansathi.com® Matrimony App (10M+), HeeSay (10M+), Hoop (10M+), Yubo (10M+), Locket Widget (10M+), BeReal. Your friends for real. (10M+), Tellonym (10M+), YouNow (10M+), W (10M+), Muzz (10M+), 微博 (10M+), Voya (10M+), Poppo Live (10M+), Hornet (10M+), SuperLive (10M+), Meetup (10M+), Camfrog (10M+), PopUp (10M+), rednote (10M+), Chatous (10M+), Connected2.me (10M+), Karrot (10M+), mewe (10M+), Chatspin Random Video Chat Duo (10M+), Bermuda Video Chat (10M+), HoYoLAB (10M+), Spoon (10M+), Chatta (10M+), Blogger (10M+), MEEFF (10M+), Lumi (10M+), buz (10M+), BuzzCast (10M+), Gemgala (10M+), 17LIVE (10M+), Linky AI (10M+), Kumu Livestream Community (10M+), Clapper (10M+), FreeTone Calls & Texting (10M+), Xiaomi Community (10M+), Text Free (10M+), REALITY (10M+), GETTR (10M+), YoHo (10M+), Achat (10M+), Veego (10M+), Joi (10M+), Vava.chat (10M+), 네이버 블로그 (10M+), SoLive (10M+), Camsurf (10M+)

**Communication:** WhatsApp Messenger (10B+), Google Chrome (10B+), Google Messages (10B+), Gmail (10B+), Google Meet (10B+), Messenger (5B+), Contacts (5B+), Snapchat (1B+), Truecaller (1B+), WhatsApp Business (1B+)

**Productivity:** Google Drive (10B+), Google Calendar (10B+), Microsoft OneDrive (5B+), ChatGPT (1B+), Google Gemini (1B+), Microsoft Word (1B+), Samsung Notes (1B+), Microsoft Outlook (1B+), Microsoft Excel (1B+), Microsoft Copilot (1B+)

**Entertainment:** Netflix (1B+), Prime Video (500M+), Disney+ (500M+), YouTube Kids (500M+), HBO Max (100M+), Twitch (100M+), DramaBox (100M+), Crunchyroll (100M+), BookMyShow (100M+), ZEE5 (100M+)

**Video players & editors:** YouTube (10B+), MX Player (1B+), CapCut (1B+), Visha (1B+), VivaVideo (500M+), Kwai (500M+), KineMaster (500M+), VLC for Android (500M+), YouCut (100M+), Screen Recorder (100M+)

**Music & audio:** YouTube Music (5B+), Spotify (1B+), Samsung Music (1B+), Shazam (500M+), JioSaavn (500M+), Lark Player (500M+), StarMaker (500M+), Audiomack (100M+), SoundCloud (100M+), Gaana (100M+)

**Shopping:** Temu (1B+), SHEIN (1B+), Amazon Shopping (1B+), AliExpress (500M+), Alibaba.com (500M+), Wildberries (100M+), Walmart (100M+), Myntra (100M+), eBay online shopping & selling (100M+), Daraz Online Shopping App (100M+)

**Maps & navigation:** Uber (1B+), Waze Navigation & Live Traffic (500M+), inDrive. Rides with fair fares (100M+), Yandex Go (100M+), Bolt (100M+), Rapido (100M+), 99 (100M+), Yandex Maps and Navigator (100M+), DiDi Rider (100M+), Yango (50M+)

**Travel & local:** Google Maps (10B+), Google Maps Go (1B+), Booking.com (500M+), Google Earth (500M+), Grab (100M+), Gojek (100M+), redBus Book Bus, Train Tickets (100M+), Yandex Navigator (100M+), BlaBlaCar (100M+), MakeMyTrip (100M+)

**News & magazines:** X (1B+), Google News (1B+), Briefing (1B+), Flipboard (500M+), Opera News (100M+), Quora (50M+), SmartNews (50M+), NewsBreak (50M+), CNN (50M+), Breaking News (10M+)

**Books & reference:** Google Play Books & Audiobooks (1B+), Wattpad (100M+), Amazon Kindle (100M+), Audible (100M+), 네이버 (100M+), ReadEra (50M+), Pratilipi Novel (50M+), Wikipedia (50M+), Oxford Dictionary & Thesaurus (50M+), Dictionary (50M+)

**Photography:** Google Photos (10B+), Picsart AI Photo Editor, Video (1B+), Gallery (1B+), Video Editor & Maker (500M+), B612 AI Photo&Video Editor (500M+), Remini (500M+), FaceApp (500M+), BeautyPlus (500M+), Camera360 (100M+), Photo Editor (100M+)

**Finance:** PhonePe UPI Payments, Loan App (1B+), Google Wallet (1B+), DANA Indonesia Digital Wallet (100M+), Cash App (100M+), easypaisa (100M+), PayPal (100M+), GoPay (100M+), BHIM Bharat's Own Payments App (100M+), BBVA México (50M+), JMO (50M+)

**Education:** Duolingo (500M+), Brainly (100M+), Google Classroom (100M+), Gauth (100M+), Cake (100M+), Kahoot! Play & Create Quizzes (100M+), Memrise (50M+), ClassDojo (50M+), Busuu (50M+), Babbel (50M+)

**Health & fitness:** Samsung Health (1B+), Flo Ovulation & Period Tracker (100M+), Period Calendar Period Tracker (100M+), Home Workout (100M+), Sweatcoin・Walking Step Counter (100M+), MyFitnessPal (100M+), Six Pack in 30 Days (100M+), Lose Weight App for Men (100M+), Step Counter (100M+), Lose Weight (100M+)

**Food & drink:** Blinkit (100M+), Uber Eats (100M+), Zepto (100M+), foodpanda (100M+), Rappi (100M+), McDonald's (100M+), DoorDash (50M+), PedidosYa (50M+), McDonald's (50M+), Too Good To Go (50M+)

**Lifestyle:** Pinterest (1B+), SmartThings (1B+), Google Home (1B+), Amazon Alexa (100M+), Life360 (100M+), Glovo (100M+), Astrotalk (100M+), LG ThinQ (100M+), Super Slime Simulator (100M+), Xiaomi Home (50M+)

**Business:** Zoom Workplace (1B+), LinkedIn (1B+), Microsoft Teams (500M+), Indeed Job Search (100M+), Uber (100M+), Meta Business Suite (100M+), Adobe Scan AI PDF Scanner, OCR (100M+), PDF Scanner app (100M+), Microsoft Authenticator (100M+), TeamViewer Remote Control (100M+)

**Tools:** Google (10B+), Phone by Google (5B+), Samsung My Files (5B+), SHAREit (1B+), Google Translate (1B+), File Manager (1B+), Google Lens (1B+), Google Go (1B+), Samsung Members (1B+), Mi Browser (1B+)

**Dating:** Tinder Dating App (500M+), Badoo Dating App (100M+), happn (100M+), Bumble Dating App (100M+), Plenty of Fish (50M+), Skout (50M+), Mamba Dating App (50M+), Boo Dating App (10M+), Hinge Dating App (10M+), Bumpy (10M+)

**Sports:** Cricbuzz (100M+), ESPN (100M+), Sofascore (100M+), NFL (100M+), BeSoccer (100M+), Da Fit (100M+), 365Scores (50M+), OneFootball Live Soccer scores (50M+), FotMob (50M+), DAZN (50M+)

**Weather:** Weather (1B+), The Weather Channel (100M+), Weather & Radar Forecast (100M+), AccuWeather (100M+), 1Weather Forecasts & Radar (100M+), Weather (100M+), Weather Radar (50M+), Transparent clock and weather (50M+), Yandex Weather & Rain Radar (50M+), Windy.com (50M+)

## The app's own shortcuts, read on the phone

Most apps publish a few screens for other apps to open: the shortcuts you see when you long-press their launcher icon (declared in the app's `shortcuts.xml`), and, for many, a settings screen (`Intent.ACTION_APPLICATION_PREFERENCES`). Pastiera reads these from each installed app when you first press one of the combos below in it, and again after the app updates. It keeps only screens the app exports to other apps, so nothing opens that the app doesn't allow.

| Combo | Opens |
| --- | --- |
| Ctrl+Alt+1 to 4 | The app's first four launcher shortcuts, in the app's order. On a keyboard without a number row, hold Ctrl+Alt and press the keys that type 1 to 4 (the keyboard's own Alt layer, then Pastiera's). |
| Ctrl+, | The app's settings screen. |
| Ctrl+N, Ctrl+F | A launcher shortcut whose id or label says new, compose, create, write, post or draft (search or find for Ctrl+F), when the app documents no key shortcut and has no catalogue suggestion. |

This works for every installed app, not only the ones in the list. The "Suggested shortcuts" switch turns it off with the suggestions, and choosing "Don't remap" for a combo in an app turns it off there. Each app's page lists what it offers.

Dynamic shortcuts (the ones apps add at run time, such as recent chats) can only be read by the home screen app, so they aren't included.

## Enter by category

Settings > Apps > Enter per app, next to App shortcuts, uses the same app list. Each app gets a standard from its Play category, and per-app overrides always win.

| Standard | Apps | What Enter does |
| --- | --- | --- |
| Chat | Social, Dating and Communication apps, plus chat apps ranked elsewhere (Telegram, Signal, Discord, Slack, Teams, Google Chat, Viber, LINE, KakaoTalk, WeChat, Element, Threema, LinkedIn, Zoom) | Your messaging preset (by default Enter sends, Shift+Enter is a new line), only in fields whose IME action is Send. Post composers, bios and note fields don't declare Send, so Enter stays a new line there. |
| Email | Gmail, Outlook | Enter is a new line, Ctrl+Enter sends (the send shortcut both document). |
| App default | Everything else, including Chrome, Contacts, Truecaller and the dialer (listed under Communication but not chats) | The app's own Enter: new line, search, next field. |

The native defaults the standards follow:

- WhatsApp: Settings > Chats > "Enter is send".
- Telegram: Settings > Chat settings > "Send by Enter".
- Signal: Settings > Chats > "Send with enter".
- Google Messages: Settings > "Press Enter to send".
- Discord: Enter is a new line on Android; the send button sends.
- Gmail and Outlook: Ctrl+Enter sends from compose.

The curated messengers (WhatsApp, Telegram, Signal, Discord, Element, Google Messages, Threema, Instagram and Messenger) keep their tested strategies. Installed apps under a standard are listed on the screen; tapping one turns it into an override you can change.

## Research: which apps document Android keyboard shortcuts

Only shortcuts an app documents for Android, or ships in its own source code,
went into the presets. Desktop and web shortcut lists were not used, because
they often don't work in the Android apps.

✔ documented (preset included) · ≈ same Chromium shortcut code as Chrome
(preset included) · ✗ the maker says there are none, or no Android
documentation was found

### Browsers
| App | Status | Source |
|---|---|---|
| Chrome | ✔ | [Chromium `KeyboardShortcuts.java`](https://chromium.googlesource.com/chromium/src/+/main/chrome/android/java/src/org/chromium/chrome/browser/KeyboardShortcuts.java). Google's help page lists only F7 for Android |
| Brave | ≈ | Built on Chrome for Android's UI code |
| Vivaldi | ≈ | Built on Chrome for Android's UI code |
| Samsung Internet | ✗ | None found (Samsung documents only DeX system shortcuts) |
| Firefox | ✗ | [Mozilla Connect](https://connect.mozilla.org/t5/discussions/keyboard-shortcuts-for-android/m-p/26896): not implemented on Android |
| Microsoft Edge | ✗ | No Android documentation found |
| Opera, Opera GX, DuckDuckGo, Tor Browser | ✗ | No Android documentation found |

### Email
| App | Status | Source |
|---|---|---|
| Gmail | ✔ | [Keyboard shortcuts for Gmail – Android](https://support.google.com/mail/answer/6594?co=GENIE.Platform%3DAndroid) |
| Outlook | ✔ | [Android keyboard shortcuts – Microsoft Support](https://support.microsoft.com/en-us/office/android-keyboard-shortcuts-3ac2d1f9-3843-461f-bc89-0e1d143cb2e1) |
| Proton Mail | ✗ | [Web only](https://proton.me/support/keyboard-shortcuts) |
| Thunderbird (K-9 Mail) | ✗ | [Requested, not documented](https://connect.mozilla.org/t5/ideas/physical-keyboard-shortcuts-for-thunderbird-android-app/idi-p/135445) |
| Samsung Email, Yahoo Mail, Spark, Blue Mail, Aqua Mail, GMX | ✗ | No Android documentation found |

### Messaging
| App | Status | Source |
|---|---|---|
| Google Chat | ✔ | [Google Chat keyboard shortcuts – Android](https://support.google.com/chat/answer/7649271?co=GENIE.Platform%3DAndroid) |
| WhatsApp | ✗ | [Help Center](https://faq.whatsapp.com/6204576529560565/?cms_platform=web): shortcuts for Web, Windows and Mac only |
| Telegram | ✗ | Desktop and Web only |
| Signal | ✗ | [Desktop only](https://support.signal.org/hc/en-us/articles/360036517511-Signal-Desktop-Keyboard-Shortcuts) |
| Discord | ✗ | [Keyboard navigation is desktop and browser only](https://support.discord.com/hc/en-us/articles/1500000056121-Keyboard-Navigation-FAQ) |
| Slack | ✗ | [Desktop and web](https://slack.com/help/articles/201374536-Slack-keyboard-shortcuts) |
| Microsoft Teams | ✗ | [Desktop, web and iPad](https://support.microsoft.com/en-us/office/keyboard-shortcuts-for-microsoft-teams-2e8e2a70-e8d8-4a19-949b-4c36dd5292d2) |
| Google Messages, Messenger, Snapchat | ✗ | No Android documentation found |

### Documents
| App | Status | Source |
|---|---|---|
| Google Docs | ✔ | [Keyboard shortcuts for Google Docs – Android](https://support.google.com/docs/answer/179738?co=GENIE.Platform%3DAndroid) |
| Google Sheets | ✔ | [Keyboard shortcuts for Google Sheets – Android](https://support.google.com/docs/answer/181110?co=GENIE.Platform%3DAndroid) |
| Google Slides | ✔ | [Keyboard shortcuts for Google Slides – Android](https://support.google.com/docs/answer/1696717?co=GENIE.Platform%3DAndroid) |
| Word | ✔ | [Use an external keyboard with Word for Android](https://support.microsoft.com/en-us/office/use-an-external-keyboard-with-word-for-android-515129a8-2f5e-410a-87aa-78b65504c244) |
| Excel | ✔ | [Use an external keyboard with Excel for Android](https://support.microsoft.com/en-us/office/use-an-external-keyboard-with-excel-for-android-efe053d5-4b50-4e8b-b63f-e8a70c80974f) |
| PowerPoint | ✗ | [Only editing and formatting keys](https://support.microsoft.com/en-us/office/use-an-external-keyboard-with-powerpoint-for-android-8f4abc4e-afb2-4eb7-be9f-73b7f928b336), none of the standard actions |
| Google Drive | ✗ | [Web only](https://support.google.com/drive/answer/2563044) |
| OneNote, Notion, Evernote | ✗ | No Android documentation found |

### Notes & tasks
| App | Status | Source |
|---|---|---|
| Google Keep | ✔ | [Keyboard shortcuts for Google Keep – Android](https://support.google.com/keep/answer/12862970?co=GENIE.Platform%3DAndroid) |
| Todoist | ✔ | [Use keyboard shortcuts in Todoist](https://www.todoist.com/help/todoist/features/use-keyboard-shortcuts-in-todoist-Wyovn2) (iOS/Android section) |
| Obsidian | ✗ | [Hotkeys not supported on mobile](https://forum.obsidian.md/t/is-there-a-hot-key-equivalent-for-mobile/51327) |
| Microsoft To Do, Google Tasks, TickTick, Samsung Notes | ✗ | No Android documentation found |

### Calendar
| App | Status | Source |
|---|---|---|
| Google Calendar | ✔ | [Use keyboard shortcuts in Google Calendar – Android](https://support.google.com/calendar/answer/37034?co=GENIE.Platform%3DAndroid) |
| Samsung Calendar, Outlook's calendar, Proton Calendar | ✗ | No Android documentation found |

### Terminal
| App | Status | Source |
|---|---|---|
| Termux | ✔ | [`TermuxTerminalViewClient.java`](https://github.com/termux/termux-app/blob/master/app/src/main/java/com/termux/app/terminal/TermuxTerminalViewClient.java): Ctrl+Alt+↓/N, ↑/P, C, R, M, U, V, 1–9 |
| JuiceSSH, ConnectBot | ✗ | No documented session shortcuts found |

### Media, maps, photos
YouTube ([web only](https://support.google.com/youtube/answer/7631406)),
Spotify, Netflix, VLC, Google Maps and Google Photos document no Android
keyboard shortcuts, so they have no presets.

## Preset mappings

| App | New | Search | Open | Refresh | Send | Close | Reply | Reply all | Forward | Archive | Delete | Unread | Star | Undo | Next / Prev | Menu | Help |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Chrome, Brave, Vivaldi | Ctrl+T | = | Ctrl+L | = | | = | | | | | | | Ctrl+D | | Ctrl+Tab / Ctrl+Shift+Tab | Alt+F | |
| Gmail | C | | | Ctrl+U | = | = | R | A | F | E | # | Shift+U | S | Z | J / K | M | ? |
| Outlook | | | | | = | | | | Ctrl+J | Ctrl+A | | Ctrl+U | Ctrl+F | = | | | |
| Google Chat | Ctrl+Shift+K | = | | | Enter | | R | | | | | | | | ↓ / ↑ | Ctrl+G | ? |
| Google Docs | | = | = | | | | | | | | | | | | | | = |
| Google Sheets | Ctrl+T | = | = | | | | | | | | | | | | = | | = |
| Google Slides | Ctrl+M | = | | | | | | | | | | | | | | | = |
| Word, Excel | | = | | | | | | | | | | | | | | | |
| Google Keep | = | / | | | | Esc | | | | E | D | | F | | J / K | = | ? |
| Todoist | Q | / | | | | | | | | | | | | | | M | ? |
| Google Calendar | = | = | Ctrl+G | = | | Esc | | | | | Delete | | | | Ctrl+J / Ctrl+K | Ctrl+Shift+T | |
| Termux | | | | | | | | | | | | | | | Ctrl+Alt+↓ / Ctrl+Alt+↑ | | |

`=` means the app already uses the standard combo, so nothing is changed.
