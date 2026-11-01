# Sets Linux up for Flux Titan Hub's desktop: Xfce or KDE Plasma, TigerVNC's server, sound, a
# user called flux, tuned for a phone. Run inside Linux as root by flux-linux.sh: proot or
# chroot, debian or ubuntu, xfce or kde. FLUX_LOCALE (de_DE…) is the phone's language.
# Much of the tuning comes from Titan-2-Elite-Debian-Chroot (same author, GPL-3.0-or-later).
set -e
MODE=${1:-proot}
DISTRO=${2:-debian}
DESKTOP=${3:-xfce}
LOCALE=$(printf '%s' "${FLUX_LOCALE:-en_US}" | tr -cd 'A-Za-z_@')
[ -n "$LOCALE" ] || LOCALE=en_US
LANGUAGE_CODE=${LOCALE%%_*}
# Raised whenever this script changes: flux-linux.sh runs it again on start. Its packages are
# installed again only when FLUX_PACKAGES_VERSION is raised too
FLUX_GUEST_VERSION=14
FLUX_PACKAGES_VERSION=6
export DEBIAN_FRONTEND=noninteractive

# ---- apt and dpkg, quicker and smaller on a phone ----
mkdir -p /etc/apt/apt.conf.d /etc/dpkg/dpkg.cfg.d
# apt stays root throughout: PRoot can't switch users
echo 'APT::Sandbox::User "root";' > /etc/apt/apt.conf.d/01flux-sandbox
printf '%s\n' 'Acquire::Languages "none";' 'Acquire::Retries "3";' 'Acquire::Queue-Mode "access";' \
    'Acquire::http::Pipeline-Depth "10";' 'APT::Install-Suggests "0";' 'Dpkg::Use-Pty "0";' \
    > /etc/apt/apt.conf.d/90-flux-speed
# No fsync after every file: flash storage is slow at it, and a crash mid-install is rare
echo force-unsafe-io > /etc/dpkg/dpkg.cfg.d/90-flux-unsafe-io
# No manuals or other languages' messages (the phone's own language kept), licences kept
printf '%s\n' 'path-exclude=/usr/share/doc/*' 'path-include=/usr/share/doc/*/copyright' \
    'path-exclude=/usr/share/man/*' 'path-exclude=/usr/share/info/*' \
    'path-exclude=/usr/share/locale/*' 'path-include=/usr/share/locale/en*' \
    "path-include=/usr/share/locale/$LANGUAGE_CODE*" 'path-include=/usr/share/locale/locale.alias' \
    > /etc/dpkg/dpkg.cfg.d/91-flux-nodoc
# Services never start from package scripts: nothing here runs them
printf '#!/bin/sh\nexit 101\n' > /usr/sbin/policy-rc.d
chmod 755 /usr/sbin/policy-rc.d
echo 'man-db man-db/auto-update boolean false' | debconf-set-selections 2>/dev/null || true

if [ "$MODE" = chroot ]; then
    # Android lets only these groups use the network
    groupadd -g 3003 aid_inet 2>/dev/null || true
    groupadd -g 3004 aid_net_raw 2>/dev/null || true
    usermod -a -G aid_inet,aid_net_raw root || true
fi
# Set up before with these packages: apt left alone, only this script's own files renewed
had=$(cat /etc/flux-packages-version 2>/dev/null || cat /etc/flux-guest-version 2>/dev/null || true)
if [ "$had" != "$FLUX_PACKAGES_VERSION" ] || [ "$(cat /etc/flux-desktop 2>/dev/null)" != "$DESKTOP" ]; then
    apt-get update
    apt-get -y upgrade || true

    # systemd isn't running here: package scripts calling systemctl get a quiet stand-in
    # (questions answered "not active", the rest done as nothing) instead of failing
    if [ -e /usr/bin/systemctl ] && [ ! -f /usr/bin/systemctl.real ]; then
        dpkg-divert --local --rename --divert /usr/bin/systemctl.real --add /usr/bin/systemctl >/dev/null
        printf '%s\n' '#!/bin/sh' '# Stand-in: systemd is not running here. The real one: systemctl.real' \
            'for a in "$@"; do case "$a" in is-active|is-enabled|is-failed|is-system-running) exit 1 ;; esac; done' \
            'exit 0' > /usr/bin/systemctl
        chmod 755 /usr/bin/systemctl
    fi

    # Packages one release has and another doesn't (or has renamed) are left out, not fatal
    pick() {
        out=""
        for p in "$@"; do
            if apt-cache show "$p" >/dev/null 2>&1; then out="$out $p"; else echo "flux: no package $p here (skipped)" >&2; fi
        done
        echo "$out"
    }
    if [ "$DESKTOP" = kde ]; then
        desktop=$(pick plasma-desktop plasma-workspace plasma-workspace-x11 plasma-session-x11 kwin-x11 \
            konsole dolphin kate systemsettings breeze breeze-icon-theme breeze-gtk-theme kde-config-gtk-style \
            plasma-pa plasma-integration kde-cli-tools kio-extras fonts-noto-core fonts-noto-mono \
            kdialog qml6-module-org-kde-plasma-plasma5support)
    else
        desktop=$(pick xfce4 xfce4-terminal thunar mousepad adwaita-icon-theme xfce4-whiskermenu-plugin)
    fi
    # A browser that runs here: Ubuntu's Firefox is a Snap, which can't
    if [ "$DISTRO" = ubuntu ]; then browser=$(pick falkon); else browser=$(pick firefox-esr); fi
    extras=$(pick tigervnc-tools dbus-bin pulseaudio pulseaudio-utils gvfs libglib2.0-bin libgl1-mesa-dri \
        libnotify-bin xdg-user-dirs fonts-dejavu-core)
    apt-get install -y --no-install-recommends $desktop $browser $extras \
        dbus dbus-x11 tigervnc-standalone-server xfonts-base sudo ca-certificates locales tzdata python3
    apt-get clean
    echo "$DESKTOP" > /etc/flux-desktop
fi

# ---- KDE's window add-ons, built for the Titan 2 Elite by Titan-2-Elite-Debian-Chroot: Klassy's
# decoration and style, and KDE Rounded Corners. Unpacked only onto the KWin they were built for
# (Debian 13's, arm64), which is then held there so they keep working ----
addons=/usr/local/share/flux-addons
if [ "$DESKTOP" = kde ] && [ "$(dpkg --print-architecture)" = arm64 ]; then
    kwin=$(dpkg-query -W -f '${Version}' kwin-x11 2>/dev/null || true)
    if [ -n "$kwin" ] && [ "$(cat "$addons/installed" 2>/dev/null)" != "$kwin" ]; then
        mkdir -p "$addons"
        bundle=https://raw.githubusercontent.com/Flux-Sniffer-Mods/Titan-2-Elite-Debian-Chroot/main/titan-addons-bundle
        if python3 - "$bundle" "$addons/bundle.tar.gz" "$kwin" <<'PY'
import sys, urllib.request
url, into, kwin = sys.argv[1:4]
key = urllib.request.urlopen(url + ".key", timeout=60).read().decode()
if ("kwin=" + kwin + " ") not in key + " ":
    print("flux: the window add-ons are for " + key.strip() + ", this KWin is " + kwin + " (left out)")
    sys.exit(1)
urllib.request.urlretrieve(url + ".tar.gz", into)
PY
        then
            if tar -C / -xzf "$addons/bundle.tar.gz"; then
                apt-mark hold $(dpkg-query -W -f '${Package}\n' 'kwin*' 'libkwin*' 2>/dev/null) >/dev/null 2>&1 || true
                echo "$kwin" > "$addons/installed"
                echo "flux: window add-ons in place (Klassy, rounded corners)"
            fi
        fi
        rm -f "$addons/bundle.tar.gz"
    fi
fi

# ---- The phone's language, English beside it ----
full="$LOCALE.UTF-8"
if [ -f /etc/locale.gen ]; then
    sed -i "s/^# *\(en_US.UTF-8 UTF-8\)/\1/; s/^# *\($full UTF-8\)/\1/" /etc/locale.gen
    locale-gen >/dev/null 2>&1 || true
else
    locale-gen en_US.UTF-8 "$full" >/dev/null 2>&1 || true
fi
locale -a 2>/dev/null | grep -qi "^$(echo "$LOCALE" | tr 'A-Z' 'a-z').utf-\?8$" || full=en_US.UTF-8
echo "LANG=$full" > /etc/default/locale

# ---- The user ----
id flux >/dev/null 2>&1 || useradd -m -s /bin/bash flux
for g in sudo audio video input; do getent group "$g" >/dev/null && usermod -a -G "$g" flux; done
[ "$MODE" = chroot ] && usermod -a -G aid_inet,aid_net_raw flux
echo 'flux ALL=(ALL) NOPASSWD:ALL' > /etc/sudoers.d/flux
chmod 440 /etc/sudoers.d/flux
# A machine ID: D-Bus wants one, and an image ships none
dbus-uuidgen --ensure 2>/dev/null || true

# ---- The session's bus: callers proven by a cookie in the user's home (DBUS_COOKIE_SHA1), as
# it's reached over 127.0.0.1. Without root Android won't let Linux make socket files, and under
# PRoot the kernel's account of who's calling never matches whom the bus believes it is ----
base=/usr/share/dbus-1/session.conf
[ -f "$base" ] || base=/etc/dbus-1/session.conf
cat > /etc/flux-dbus-session.conf <<EOF
<!DOCTYPE busconfig PUBLIC "-//freedesktop//DTD D-Bus Bus Configuration 1.0//EN"
 "http://www.freedesktop.org/standards/dbus/1.0/busconfig.dtd">
<busconfig>
  <include>$base</include>
  <auth>DBUS_COOKIE_SHA1</auth>
</busconfig>
EOF

# ---- KDE: an applications menu (Plasma 6 on Debian ships none, leaving the launcher empty) ----
if [ "$DESKTOP" = kde ] && [ ! -s /etc/xdg/menus/applications.menu ]; then
    mkdir -p /etc/xdg/menus
    rm -f /etc/xdg/menus/applications.menu
    if [ -f /etc/xdg/menus/plasma-applications.menu ]; then
        ln -s plasma-applications.menu /etc/xdg/menus/applications.menu
    else
        cat > /etc/xdg/menus/applications.menu <<'MENU'
<!DOCTYPE Menu PUBLIC "-//freedesktop//DTD Menu 1.0//EN" "http://www.freedesktop.org/standards/menu-spec/1.0/menu.dtd">
<Menu>
  <Name>Applications</Name>
  <Directory>kde-main.directory</Directory>
  <DefaultAppDirs/>
  <DefaultDirectoryDirs/>
  <DefaultMergeDirs/>
  <Include><Category>Core</Category></Include>
  <Menu><Name>Development</Name><Directory>kde-development.directory</Directory><Include><Category>Development</Category></Include></Menu>
  <Menu><Name>Education</Name><Directory>kde-education.directory</Directory><Include><Category>Education</Category></Include></Menu>
  <Menu><Name>Games</Name><Directory>kde-games.directory</Directory><Include><Category>Game</Category></Include></Menu>
  <Menu><Name>Graphics</Name><Directory>kde-graphics.directory</Directory><Include><Category>Graphics</Category></Include></Menu>
  <Menu><Name>Internet</Name><Directory>kde-internet.directory</Directory><Include><Category>Network</Category></Include></Menu>
  <Menu><Name>Multimedia</Name><Directory>kde-multimedia.directory</Directory><Include><Or><Category>AudioVideo</Category><Category>Audio</Category><Category>Video</Category></Or></Include></Menu>
  <Menu><Name>Office</Name><Directory>kde-office.directory</Directory><Include><Category>Office</Category></Include></Menu>
  <Menu><Name>Settings</Name><Directory>kde-settingsmenu.directory</Directory><Include><Category>Settings</Category></Include></Menu>
  <Menu><Name>System</Name><Directory>kde-system.directory</Directory><Include><Category>System</Category></Include></Menu>
  <Menu><Name>Utilities</Name><Directory>kde-utilities.directory</Directory><Include><Category>Utility</Category></Include></Menu>
  <Menu><Name>Lost &amp; Found</Name><Directory>kde-unknown.directory</Directory><OnlyUnallocated/><Include><All/></Include></Menu>
</Menu>
MENU
    fi
fi

# ---- KDE tuning for a small touch screen drawn by the CPU, once per version, as the user ----
cat > /usr/local/bin/flux-tune-kde <<'TUNE'
#!/bin/sh
version=2
stamp="$HOME/.config/.flux_tuned_kde"
[ "$(cat "$stamp" 2>/dev/null)" = "$version" ] && exit 0
K=$(command -v kwriteconfig6 || command -v kwriteconfig5) || exit 0
mkdir -p "$HOME/.config/autostart" "$HOME/.config/fontconfig" "$HOME/.local/share/konsole"
# Dark from the first frame (only when nothing is chosen yet), Klassy's where it's in place
if [ ! -f "$HOME/.config/kdeglobals" ]; then
    for c in KlassyDark BreezeDark; do
        if [ -f "/usr/share/color-schemes/$c.colors" ]; then
            cp "/usr/share/color-schemes/$c.colors" "$HOME/.config/kdeglobals"
            $K --file kdeglobals --group General --key ColorScheme "$c"
            break
        fi
    done
fi
# Smooth text, only when none is set (KDE's own font settings go in the same file)
[ -f "$HOME/.config/fontconfig/fonts.conf" ] || printf '%s\n' '<?xml version="1.0"?>' \
    '<!DOCTYPE fontconfig SYSTEM "fonts.dtd">' '<fontconfig>' '  <match target="font">' \
    '    <edit mode="assign" name="rgba"><const>rgb</const></edit>' \
    '    <edit mode="assign" name="hinting"><bool>true</bool></edit>' \
    '    <edit mode="assign" name="hintstyle"><const>hintslight</const></edit>' \
    '    <edit mode="assign" name="antialias"><bool>true</bool></edit>' \
    '    <edit mode="assign" name="lcdfilter"><const>lcddefault</const></edit>' \
    '  </match>' '</fontconfig>' > "$HOME/.config/fontconfig/fonts.conf"
$K --file baloofilerc     --group "Basic Settings" --key "Indexing-Enabled" false
$K --file ksmserverrc     --group General --key loginMode emptySession
$K --file ksmserverrc     --group General --key confirmLogout true
$K --file ksmserverrc     --group General --key offerShutdown false
$K --file kscreenlockerrc --group Daemon  --key Autolock false
$K --file kscreenlockerrc --group Daemon  --key LockOnResume false
$K --file kwinrc          --group Plugins --key blurEnabled false
$K --file kwinrc          --group Plugins --key contrastEnabled false
# Straight to the desktop, no splash
$K --file ksplashrc       --group KSplash --key Engine none
$K --file ksplashrc       --group KSplash --key Theme None
# Touch: tablet mode (bigger controls), a single tap opens, new windows maximised
$K --file kdeglobals      --group KDE     --key TabletMode on
$K --file kdeglobals      --group KDE     --key SingleClick true
$K --file kwinrc          --group Windows --key Placement Maximizing
$K --file kwinrc          --group Windows --key TitlebarDoubleClickCommand Maximize
$K --file kwinrc          --group MouseBindings --key CommandTitlebarWheel Nothing
for g in org.kde.kdecoration2 org.kde.kdecoration3; do
    $K --file kwinrc --group $g --key BorderSize None
    $K --file kwinrc --group $g --key BorderSizeAuto false
done
# Bigger text and icons to touch
$K --file kdeglobals --group General      --key font "Noto Sans,11,-1,5,50,0,0,0,0,0"
$K --file kdeglobals --group General      --key menuFont "Noto Sans,11,-1,5,50,0,0,0,0,0"
$K --file kdeglobals --group General      --key toolBarFont "Noto Sans,11,-1,5,50,0,0,0,0,0"
$K --file kdeglobals --group General      --key smallestReadableFont "Noto Sans,9,-1,5,50,0,0,0,0,0"
$K --file kdeglobals --group WM           --key activeFont "Noto Sans,11,-1,5,63,0,0,0,0,0"
$K --file kdeglobals --group SmallIcons   --key Size 22
$K --file kdeglobals --group ToolbarIcons --key Size 32
$K --file kdeglobals --group MainToolbarIcons --key Size 32
$K --file kdeglobals --group DialogIcons  --key Size 48
$K --file kdeglobals --group DesktopIcons --key Size 64
$K --file kdeglobals --group PanelIcons   --key Size 32
$K --file kdeglobals --group KDE          --key ScrollbarLeftClickNavigatesByPage false
$K --file konsolerc  --group "Desktop Entry" --key DefaultProfile Flux.profile
printf '[Appearance]\nFont=Noto Sans Mono,12,-1,5,50,0,0,0,0,0\n[General]\nName=Flux\nParent=FALLBACK/\n[Scrolling]\nScrollBarPosition=2\n' \
    > "$HOME/.local/share/konsole/Flux.profile"
$K --file dolphinrc  --group General     --key ShowFullPath false
$K --file dolphinrc  --group IconsMode   --key PreviewSize 64
$K --file dolphinrc  --group DetailsMode --key PreviewSize 32
$K --file startkderc --group General     --key systemdBoot false
# Background helpers a phone has no use for
for a in org.kde.discover.notifier baloo_file org.kde.kalendarac geoclue-demo-agent org.kde.kgpg \
         kdeconnectd org.kde.kdeconnect.daemon; do
    printf '[Desktop Entry]\nHidden=true\n' > "$HOME/.config/autostart/$a.desktop"
done
echo "$version" > "$stamp"
TUNE
chmod 755 /usr/local/bin/flux-tune-kde

# ---- KDE fitted to the phone's screen and touch, after Titan-2-Elite-Debian-Chroot: the panel and
# title bars clear the camera cutout and the rounded corners (sizes from /tmp/flux-display.conf,
# which the session writes from what the app measured), the Phone widget, the phone's lock ----
if [ "$DESKTOP" = kde ]; then

# The window look, once per setting: title bars whose buttons clear the corner's curve and the
# cutout, big buttons, Klassy's decoration and KDE Rounded Corners where they're in place (the
# margins and buttons work with any decoration). As the desktop's user, before KWin starts
cat > /usr/local/bin/flux-window-look <<'LOOK'
#!/bin/sh
[ -f /tmp/flux-display.conf ] && . /tmp/flux-display.conf
R=${CORNER_RADIUS:-0}; S=${DISPLAY_SCALE:-1.5}; HOLE=${CUTOUT_RIGHT:-0}
RADIUS=${WINDOW_RADIUS:-24}; SPACER=${SPACER_PX:-20}; PAD=${CORNER_PAD:-32}
[ "$R" -gt 0 ] 2>/dev/null || PAD=0
klassy=$(ls /usr/lib/*/qt6/plugins/org.kde.kdecoration3/org.kde.klassy.so 2>/dev/null | head -n 1)
rounded=$(ls /usr/lib/*/qt6/plugins/kwin/effects/plugins/kwin4_effect_shapecorners.so 2>/dev/null | head -n 1)
key="r=$RADIUS c=$R s=$S h=$HOLE p=$PAD sp=$SPACER klassy=${klassy:+1} rounded=${rounded:+1} look=1"
stamp="$HOME/.config/.flux_window_look"
[ "$(cat "$stamp" 2>/dev/null)" = "$key" ] && exit 0
K=$(command -v kwriteconfig6 || command -v kwriteconfig5) || exit 0
# How far in the corner's curve reaches at the buttons' height, and a gap (logical px); the left
# clears the cutout as well
SIDE=$(awk -v R="$R" -v s="$S" 'BEGIN { y = R - 21; if (y < 0) y = 0; d = R*R - y*y; if (d < 0) d = 0; printf "%d", (R - sqrt(d)) / s + 3 }')
SIDE=$((SIDE + PAD))
LEFT=$(awk -v h="$HOLE" -v s="$S" -v m="$SIDE" 'BEGIN { c = int(h / s + 4); printf "%d", (c > m) ? c : m }')
# Klassy's margins stop at 60% of the title bar's height, nowhere near a phone's corner: the rest
# is made of KWin's spacer buttons ("_"), about SPACER px each
spacers() { awk -v p="$1" -v w="$SPACER" 'BEGIN { n = int(p / w + 0.5); if (n > 12) n = 12; for (i = 0; i < n; i++) printf "_" }'; }
deco() { for g in org.kde.kdecoration2 org.kde.kdecoration3; do $K --file kwinrc --group $g --key "$1" "$2"; done; }
if [ -n "$klassy" ]; then deco library org.kde.klassy; deco theme Klassy; fi
deco BorderSize None
deco BorderSizeAuto false
# Buttons on the right, inside the corner; the left only spacers, so nothing starts under the cutout
deco ButtonsOnLeft "$(spacers "$LEFT")"
deco ButtonsOnRight "IAX$(spacers "$SIDE")"
if [ -n "$klassy" ]; then
    # Each in the group Klassy's own settings file has it in
    kl() { $K --file klassy/klassyrc --group "$1" --key "$2" "$3"; }
    kl Windeco WindowCornerRadius "$RADIUS"
    kl Windeco DrawBorderOnMaximizedWindows false
    # The whole title bar's height to tap, big icons
    kl Windeco ButtonShape ShapeFullHeightRectangle
    kl Windeco IconSize IconVeryLarge
    kl Windeco SystemIconSize SystemIcon32
    kl TitleBarSpacing TitleAlignment AlignCenterFullWidth
    kl TitleBarSpacing LockTitleBarLeftRightMargins false
    kl TitleBarSpacing LockTitleBarTopBottomMargins true
    kl TitleBarSpacing TitleBarLeftMargin "$LEFT"
    kl TitleBarSpacing TitleBarRightMargin "$SIDE"
    kl TitleBarSpacing TitleBarTopMargin 6
    kl TitleBarSpacing TitleBarBottomMargin 6
    kl ButtonSizing FullHeightButtonWidthMarginLeft 10
    kl ButtonSizing FullHeightButtonWidthMarginRight 10
    kl ButtonSizing LockFullHeightButtonWidthMargins true
    kl ButtonSizing FullHeightButtonSpacingLeft 4
    kl ButtonSizing FullHeightButtonSpacingRight 4
    kl ButtonSizing ButtonSpacingLeft 8
    kl ButtonSizing ButtonSpacingRight 8
    # Panels, popups and programs matching the decoration
    if [ -f /usr/share/color-schemes/KlassyDark.colors ]; then
        plasma-apply-colorscheme KlassyDark >/dev/null 2>&1 || $K --file kdeglobals --group General --key ColorScheme KlassyDark
    fi
    [ -n "$(ls /usr/lib/*/qt6/plugins/styles/klassy*.so 2>/dev/null | head -n 1)" ] && $K --file kdeglobals --group KDE --key widgetStyle klassy
fi
if [ -n "$rounded" ]; then
    # Floating windows rounded; a maximised one stays square, running into the screen's own corners
    rc() { $K --file kwinrc --group Round-Corners --key "$1" "$2"; }
    rc Size "$RADIUS"
    rc InactiveCornerRadius "$RADIUS"
    rc DisableRoundMaximize true
    rc DisableRoundTile true
    rc OutlineThickness 0
    rc InactiveOutlineThickness 0
    rc ShadowSize 0
fi
mkdir -p "$HOME/.config"
echo "$key" > "$stamp"
LOOK
chmod 755 /usr/local/bin/flux-window-look

# KDE each time it starts: its effects as the app has them (KWin's compositing and Qt's
# animations, which smooth scrolling rides on), the window look, Firefox's tab bar clear of the
# cutout and corners, and KDE's list of programs rebuilt after this setup changed
cat > /usr/local/bin/flux-kde-session <<'KSESSION'
#!/bin/sh
[ -f /tmp/flux-display.conf ] && . /tmp/flux-display.conf
K=$(command -v kwriteconfig6 || command -v kwriteconfig5) || exit 0
if [ "${EFFECTS:-1}" = 1 ]; then
    $K --file kwinrc --group Compositing --key Enabled true
    $K --file kwinrc --group Compositing --key Backend OpenGL
    $K --file kwinrc --group Compositing --key GLCore false
    # Given another chance each start (KWin turns compositing off for good after one failure)
    $K --file kwinrc --group Compositing --key OpenGLIsUnsafe false
    $K --file kwinrc --group Compositing --key WindowsBlockCompositing false
    $K --file kdeglobals --group KDE --key AnimationDurationFactor 1
else
    $K --file kwinrc --group Compositing --key Enabled false
    $K --file kdeglobals --group KDE --key AnimationDurationFactor 0
fi
/usr/local/bin/flux-window-look
R=${CORNER_RADIUS:-0}; S=${DISPLAY_SCALE:-1.5}; HOLE=${CUTOUT_RIGHT:-0}
if [ -d /usr/lib/firefox-esr ] || [ -d /usr/lib/firefox ]; then
    side=$(awk -v R="$R" -v s="$S" 'BEGIN { y = R - 21; if (y < 0) y = 0; d = R*R - y*y; if (d < 0) d = 0; printf "%d", (R - sqrt(d)) / s + 3 }')
    left=$(awk -v h="$HOLE" -v s="$S" -v m="$side" 'BEGIN { c = int(h / s + 4); printf "%d", (c > m) ? c : m }')
    ff="$HOME/.mozilla/firefox"
    # A profile up front, so the margins are there the first time Firefox opens
    if [ ! -f "$ff/profiles.ini" ]; then
        mkdir -p "$ff/flux.default"
        printf '[Profile0]\nName=default\nIsRelative=1\nPath=flux.default\nDefault=1\n\n[General]\nStartWithLastProfile=1\n' > "$ff/profiles.ini"
        : > "$ff/flux.default/prefs.js"
    fi
    for prof in "$ff"/*/; do
        case "${prof%/}" in *"Crash Reports"|*"Pending Pings") continue ;; esac
        css="${prof}chrome/userChrome.css"
        # Only ours: a userChrome.css of your own is left alone
        [ -f "$css" ] && ! grep -q '^/\* Flux' "$css" && continue
        mkdir -p "${prof}chrome"
        printf '/* Flux: the tab bar, maximised, clear of the camera cutout and the corner */\n:root[sizemode="maximized"] #TabsToolbar { padding-left: %spx !important; padding-right: %spx !important; }\n' \
            "$left" "$side" > "$css"
    done
fi
v=$(cat /etc/flux-guest-version 2>/dev/null)
if [ "$(cat "$HOME/.cache/flux-sycoca" 2>/dev/null)" != "$v" ]; then
    kbuildsycoca6 --noincremental >/dev/null 2>&1
    mkdir -p "$HOME/.cache"
    echo "$v" > "$HOME/.cache/flux-sycoca"
fi
exit 0
KSESSION
chmod 755 /usr/local/bin/flux-kde-session

# The panel and launcher, laid out once Plasma is up
cat > /usr/local/bin/flux-display <<'PANEL'
#!/usr/bin/python3
"""Plasma's panel and launcher for a phone's touch screen, from Titan-2-Elite-Debian-Chroot's
titan_display.py (same author, GPL-3.0-or-later). Run as the desktop starts (KDE autostart).

The panel is laid out once: along the bottom, full width, with spacers at both ends sized to the
screen's rounded corners, the launcher (Kickoff as a grid of big icons), the running and pinned
programs, the tray (only what matters here), the Phone widget and the clock; hidden under windows,
the bottom edge showing it. Afterwards it's yours: only its sizes follow the settings, every widget
you add or pin is kept. /tmp/flux-panel-rebuild lays it out afresh. Settings: /tmp/flux-display.conf
(from the app, each start). Log: /tmp/flux-display.log
"""
import hashlib
import math
import os
import subprocess
import sys
import time

LOG = open("/tmp/flux-display.log", "w", buffering=1)
STAMP = os.path.expanduser("~/.config/.flux_panel_applied")
REBUILD = "/tmp/flux-panel-rebuild"
PHONE = "org.flux.phonestatus"


def log(msg):
    print(time.strftime("%H:%M:%S"), msg, file=LOG)


def load_conf():
    conf = {"SCREEN_W": "1080", "SCREEN_H": "1200", "CORNER_RADIUS": "0", "DISPLAY_SCALE": "1.5",
            "PANEL_HEIGHT": "52", "PANEL_HIDING": "dodgewindows", "CORNER_PAD": "32"}
    try:
        with open("/tmp/flux-display.conf") as f:
            for line in f:
                if "=" in line:
                    k, v = line.strip().split("=", 1)
                    conf[k] = v
    except OSError:
        log("no /tmp/flux-display.conf, using defaults")
    return conf


def dbus(*args):
    return subprocess.run(["dbus-send", "--session", "--print-reply", *args], capture_output=True, text=True)


def plasma_running():
    r = dbus("--dest=org.freedesktop.DBus", "/org/freedesktop/DBus",
             "org.freedesktop.DBus.NameHasOwner", "string:org.kde.plasmashell")
    return "boolean true" in r.stdout


def corner_inset(radius, panel_px):
    """How far in from the side the rounded corner cuts, a quarter of the way up the panel (where
    its icons start). Physical px."""
    y = radius - panel_px * 0.25
    if y <= 0:
        return 0
    return max(0.0, radius - math.sqrt(max(0.0, radius * radius - y * y)))


def fingerprint():
    """This file's own: a change to the layout here lays the panel out again (keeping what's yours)."""
    try:
        with open(__file__, "rb") as f:
            return hashlib.md5(f.read()).hexdigest()[:8]
    except OSError:
        return "v0"


def launcher_js(conf):
    """Kickoff as a grid of big icons across the screen, written only where a setting differs (so
    it isn't reloaded for nothing)."""
    scale = float(conf["DISPLAY_SCALE"])
    full = round(int(conf["SCREEN_W"]) / scale)
    popup_h = round(int(conf["SCREEN_H"]) / scale) - int(conf["PANEL_HEIGHT"]) - 60
    return f"""
function launcherConfig(l) {{
    l.currentConfigGroup = ["General"];
    var n = 0;
    function setIf(k, v) {{
        var cur = String(l.readConfig(k, "\\u0000"));
        if (cur != String(v)) {{ l.writeConfig(k, v); n++; }}
    }}
    setIf("icon", "start-here-kde-plasma");
    setIf("applicationsDisplay", 0);
    setIf("favoritesDisplay", 0);
    setIf("compactMode", false);
    setIf("showActionButtonCaptions", false);
    setIf("primaryActions", 3);
    setIf("systemFavorites", "lock-screen,logout");
    setIf("alphaSort", true);
    setIf("pin", false);
    setIf("popupWidth", {full - 60});
    setIf("popupHeight", {popup_h});
    if (n) {{
        try {{ l.reloadConfig(); }} catch (e) {{ out.push("launcher reload: " + e); }}
        out.push("launcher: " + n + " settings written");
    }}
}}
var LAUNCHERS = ["org.kde.plasma.kickoff", "org.kde.plasma.kickerdash", "org.kde.plasma.kicker"];
"""


def evaluate(script):
    return dbus("--dest=org.kde.plasmashell", "/PlasmaShell", "org.kde.PlasmaShell.evaluateScript", f"string:{script}")


def fit_panel(conf):
    key = "|".join(conf.get(k, "") for k in ("DISPLAY_SCALE", "CORNER_RADIUS", "PANEL_HEIGHT", "PANEL_HIDING",
                                              "CORNER_PAD", "SCREEN_W", "SCREEN_H")) + "|" + fingerprint()
    rebuild = os.path.exists(REBUILD)
    for _ in range(120):
        if plasma_running():
            break
        time.sleep(1)
    else:
        log("Plasma never came up; panel left as it is")
        return
    time.sleep(3)  # its panels loading
    try:
        with open(STAMP) as f:
            if f.read().strip() == key and not rebuild:
                r = evaluate(launcher_js(conf) + """
var out = [];
panels().forEach(function (p) { p.widgets().forEach(function (w) { if (LAUNCHERS.indexOf(w.type) >= 0) launcherConfig(w); }); });
print(out.join("; "));
""")
                log(f"panel already laid out, yours as it is; launcher: rc={r.returncode} {r.stdout.strip()[-200:]}")
                return
    except OSError:
        pass
    scale = float(conf["DISPLAY_SCALE"])
    width, radius = int(conf["SCREEN_W"]), int(conf["CORNER_RADIUS"])
    height = int(conf["PANEL_HEIGHT"])                       # logical px
    full = round(width / scale)                              # logical px
    pad = int(conf["CORNER_PAD"]) if radius > 0 else 0
    spacer = round((corner_inset(radius, height * scale) + 6) / scale) + pad
    hiding = conf["PANEL_HIDING"]
    if rebuild:
        log("panel laid out afresh, as asked")
    fresh = "true" if rebuild else "false"
    # A camera cutout on the left: the desktop's icons start from the right instead
    hole = int(conf.get("CUTOUT_RIGHT", "0") or 0)
    icons_right = "true" if 0 < hole < width / 2 else "false"
    # Every property in its own try: Plasma versions differ in what they have.
    #   [corner][launcher][tasks ......][expanding][tray][phone][clock][corner]
    script = launcher_js(conf) + f"""
var out = [];
function set(o, k, v) {{ try {{ o[k] = v; }} catch (e) {{ out.push(k + ": " + e); }} }}
function ours(w) {{ w.currentConfigGroup = ["General"]; return w.type == "org.kde.plasma.panelspacer" && String(w.readConfig("fluxCorner", "")) == "true"; }}
function trayConfig(s) {{
    s.currentConfigGroup = ["General"];
    s.writeConfig("scaleIconsToFit", true);
    s.writeConfig("extraItems", ["org.kde.plasma.volume", "org.kde.plasma.notifications", "org.kde.plasma.clipboard", "org.kde.plasma.devicenotifier"]);
    s.writeConfig("knownItems", ["org.kde.plasma.volume", "org.kde.plasma.notifications", "org.kde.plasma.clipboard", "org.kde.plasma.devicenotifier"]);
    s.writeConfig("hiddenItems", ["org.kde.plasma.clipboard", "org.kde.plasma.devicenotifier"]);
}}
function clockConfig(c) {{
    c.currentConfigGroup = ["Appearance"];
    c.writeConfig("showDate", false);
    c.writeConfig("use24hFormat", 2);
    c.writeConfig("autoFontAndSize", false);
    c.writeConfig("fontSize", 16);
}}
// Ours from before: only its sizes and its corner spacers follow, every widget added since kept
var mine = {fresh} ? [] : panels().filter(function (p) {{ return p.location == "bottom" && p.widgets().filter(ours).length == 2; }});
if (mine.length) {{
    mine.forEach(function (p) {{
        set(p, "floating", false); set(p, "opacity", "opaque"); set(p, "height", {height}); set(p, "lengthMode", "fill");
        set(p, "maximumLength", {full}); set(p, "minimumLength", {full});
        set(p, "hiding", "{hiding}"); if (p.hiding != "{hiding}") set(p, "hiding", "autohide");
        p.widgets().filter(ours).forEach(function (w) {{ w.writeConfig("length", {spacer}); }});
        p.widgets().forEach(function (w) {{
            if (LAUNCHERS.indexOf(w.type) >= 0) launcherConfig(w);
            if (w.type == "org.kde.plasma.systemtray") trayConfig(w);
            if (w.type == "org.kde.plasma.digitalclock") {{ w.currentConfigGroup = ["Appearance"]; w.writeConfig("autoFontAndSize", false); w.writeConfig("fontSize", 16); }}
        }});
        out.push("panel adjusted, its widgets kept");
    }});
}} else {{
    function corner(p) {{
        var w = p.addWidget("org.kde.plasma.panelspacer");
        w.currentConfigGroup = ["General"];
        w.writeConfig("expanding", false); w.writeConfig("length", {spacer}); w.writeConfig("fluxCorner", "true");
    }}
    var bottoms = panels().filter(function (p) {{ return p.location == "bottom"; }});
    if (bottoms.length == 0) {{ var np = new Panel; np.location = "bottom"; bottoms = [np]; }}
    bottoms.forEach(function (p, i) {{
        if (i > 0) {{ p.remove(); return; }}
        p.widgets().forEach(function (w) {{ w.remove(); }});
        set(p, "floating", false);
        set(p, "opacity", "opaque");
        set(p, "height", {height});
        set(p, "lengthMode", "fill");
        set(p, "alignment", "center");
        set(p, "offset", 0);
        set(p, "maximumLength", {full});
        set(p, "minimumLength", {full});
        set(p, "hiding", "{hiding}");
        if (p.hiding != "{hiding}") set(p, "hiding", "autohide");
        corner(p);
        launcherConfig(p.addWidget("org.kde.plasma.kickoff"));
        var t = p.addWidget("org.kde.plasma.icontasks");
        t.currentConfigGroup = ["General"];
        t.writeConfig("iconSpacing", 1);
        t.writeConfig("showOnlyCurrentDesktop", false);
        // A few pinned on a small panel; running programs show up as they open
        t.writeConfig("launchers", ["preferred://browser", "preferred://filemanager", "applications:org.kde.konsole.desktop"]);
        var x = p.addWidget("org.kde.plasma.panelspacer");
        x.currentConfigGroup = ["General"]; x.writeConfig("expanding", true);
        trayConfig(p.addWidget("org.kde.plasma.systemtray"));
        try {{ p.addWidget("{PHONE}"); }} catch (e) {{ out.push("phone widget: " + e); }}
        clockConfig(p.addWidget("org.kde.plasma.digitalclock"));
        corner(p);
        out.push("panel laid out: hiding=" + p.hiding + " widgets=" + p.widgets().length);
    }});
    // A top panel has no place on a small screen
    panels().forEach(function (p) {{ if (p.location == "top") p.remove(); }});
    if ({icons_right}) desktops().forEach(function (d) {{ d.currentConfigGroup = ["General"]; d.writeConfig("alignment", 1); }});
}}
print(out.join("; "));
"""
    r = evaluate(script)
    log(f"panel: full width {full}, corner spacers {spacer}, height {height} (logical px), hiding {hiding}; "
        f"rc={r.returncode} out={r.stdout.strip()} err={r.stderr.strip()}")
    if r.returncode == 0:
        with open(STAMP, "w") as f:
            f.write(key + "\n")
        try:
            os.remove(REBUILD)
        except OSError:
            pass


if __name__ == "__main__":
    try:
        c = load_conf()
        log(f"settings: {c}")
        fit_panel(c)
    except Exception as ex:
        log(f"failed: {ex!r}")
        sys.exit(1)
PANEL
chmod 755 /usr/local/bin/flux-display
mkdir -p /etc/xdg/autostart
printf '[Desktop Entry]\nType=Application\nName=Flux panel\nExec=/usr/bin/python3 /usr/local/bin/flux-display\nOnlyShowIn=KDE;\nNoDisplay=true\n' \
    > /etc/xdg/autostart/flux-display.desktop

# The Phone widget, and its way of asking the phone (the app's shell does it)
cat > /usr/local/bin/flux-android <<'ASK'
#!/bin/sh
# flux-android <request> [args...]: one tab-separated line in /tmp/flux-android-cmd
printf '%s\t' "$@" >> /tmp/flux-android-cmd
printf '\n' >> /tmp/flux-android-cmd
ASK
chmod 755 /usr/local/bin/flux-android
widget=/usr/share/plasma/plasmoids/org.flux.phonestatus
mkdir -p "$widget/contents/ui"
cat > "$widget/metadata.json" <<'META'
{
    "KPackageStructure": "Plasma/Applet",
    "KPlugin": {
        "Id": "org.flux.phonestatus",
        "Name": "Phone",
        "Description": "The phone's battery, Wi-Fi, signal and notifications, and its switches",
        "Icon": "phone",
        "Category": "System Information",
        "Version": "1.0",
        "Authors": [{ "Name": "Flux Titan Hub" }]
    },
    "X-Plasma-API-Minimum-Version": "6.0"
}
META
cat > "$widget/contents/ui/main.qml" <<'PHONEQML'
import QtQuick
import QtQuick.Window
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import org.kde.plasma.plasmoid
import org.kde.plasma.components as PC3
import org.kde.plasma.plasma5support as P5Support
import org.kde.kirigami as Kirigami

// The Phone widget, from Titan-2-Elite-Debian-Chroot's titan_phonestatus.qml (same author,
// GPL-3.0-or-later).
//   Panel: battery, Wi-Fi, mobile signal, the phone's notification count.
//   Popup (tap): Wi-Fi networks (connect, forget), mobile data, airplane mode, Bluetooth,
//   do not disturb, battery and its saver, brightness, volume, the phone's lock and shade.
// Read from /tmp/flux-status (the app's shell writes it), asked of the phone through
// /usr/local/bin/flux-android (one line each in /tmp/flux-android-cmd).
PlasmoidItem {
    id: root
    property var st: ({})
    property var scan: []
    property string pendingSsid: ""
    property bool pendingSecure: true
    property int volLocal: -1        // what was just set, till the phone says so too
    property int brightLocal: -1
    Timer { id: settle; interval: 2500; onTriggered: { root.volLocal = -1; root.brightLocal = -1; } }

    // The phone's language where there's a translation, else English
    readonly property var words: ({})
    function t(s) {
        var lang = Qt.locale().name.split("_")[0];
        var w = words[lang];
        return (w && w[s]) ? w[s] : s;
    }

    // Networks nearby looked for afresh each time it opens
    onExpandedChanged: if (expanded) android("wifi_scan")

    switchWidth: Kirigami.Units.gridUnit * 14
    switchHeight: Kirigami.Units.gridUnit * 10
    toolTipMainText: t("Phone")
    toolTipSubText: t("Battery") + " " + (st.BAT || "?") + "%" + (st.CHG == "1" ? " (" + t("charging") + ")" : "")
        + (st.WIFI ? "\nWi-Fi: " + st.WIFI + " (" + (st.RSSI || "?") + " dBm)" : "")
        + (st.OPERATOR ? "\n" + st.OPERATOR : "")

    function on(v) { return v == "1"; }
    function level(l) { return ["none", "weak", "ok", "good", "excellent"][Math.max(0, Math.min(4, parseInt(l || 0)))]; }
    function rssiLevel(r) {
        var v = parseInt(r);
        return isNaN(v) ? 0 : v >= -55 ? 4 : v >= -65 ? 3 : v >= -75 ? 2 : v >= -85 ? 1 : 0;
    }
    function cellLevel() { return Math.max(0, Math.min(4, parseInt(st.CELL || 0))); }
    function batteryIcon() {
        var l = Math.round(parseInt(st.BAT || 0) / 10) * 10;
        return "battery-" + String(l).padStart(3, "0") + (st.CHG == "1" ? "-charging" : "");
    }
    // flux-android <request> [args...]: one tab-separated line for the phone to act on
    function android() {
        var parts = ["/usr/local/bin/flux-android"];
        for (var i = 0; i < arguments.length; i++) parts.push("'" + String(arguments[i]).replace(/'/g, "'\\''") + "'");
        runner.connectSource(parts.join(" "));
    }
    // Android's own list (cmd wifi list-scan-results): BSSID, frequency, RSSI, age, the name
    // (spaces and all) and its flags; the strongest of each name first
    function parseScan(text) {
        var seen = {}, list = [];
        (text || "").split("\n").slice(1).forEach(function (l) {
            var p = l.trim().split(/\s+/);
            if (p.length < 5) return;
            var name = [];
            for (var i = 4; i < p.length && p[i].charAt(0) != "["; i++) name.push(p[i]);
            var ssid = name.join(" ");
            if (ssid === "" || seen[ssid]) return;
            seen[ssid] = true;
            list.push({ rssi: p[2], ssid: ssid, flags: p[p.length - 1] });
        });
        list.sort(function (a, b) { return parseInt(b.rssi) - parseInt(a.rssi); });
        return list;
    }

    P5Support.DataSource {
        id: runner
        engine: "executable"
        connectedSources: []
        onNewData: (source, data) => disconnectSource(source)
    }
    P5Support.DataSource {
        engine: "executable"
        connectedSources: ["cat /tmp/flux-status 2>/dev/null"]
        interval: 1000
        onNewData: (source, data) => {
            var o = {};
            (data.stdout || "").split("\n").forEach(function (l) {
                var i = l.indexOf("="); if (i > 0) o[l.slice(0, i)] = l.slice(i + 1);
            });
            root.st = o;
        }
    }
    P5Support.DataSource {
        engine: "executable"
        connectedSources: ["cat /tmp/flux-wifi-scan 2>/dev/null"]
        interval: 5000
        onNewData: (source, data) => { root.scan = root.parseScan(data.stdout); }
    }

    // ------------------------------------------------------------ panel
    compactRepresentation: MouseArea {
        id: compact
        // Sized from the panel, like the launcher's, the tasks' and the tray's
        property real iconPx: Math.max(Kirigami.Units.iconSizes.smallMedium, Math.round(height * 0.6))
        Layout.preferredWidth: row.implicitWidth + Kirigami.Units.smallSpacing * 2
        Layout.minimumWidth: Layout.preferredWidth
        onClicked: root.expanded = !root.expanded
        RowLayout {
            id: row
            anchors.centerIn: parent
            spacing: Kirigami.Units.smallSpacing
            Kirigami.Icon { source: root.batteryIcon(); visible: st.BAT !== undefined && st.BAT !== ""; Layout.preferredWidth: compact.iconPx; Layout.preferredHeight: width }
            PC3.Label { text: (st.BAT || "?") + "%"; font.pointSize: Kirigami.Theme.defaultFont.pointSize * 1.2 }
            Kirigami.Icon { source: on(st.WIFI_ON) ? "network-wireless-signal-" + level(st.WIFI_LVL) : "network-wireless-off"; Layout.preferredWidth: compact.iconPx; Layout.preferredHeight: width }
            Kirigami.Icon { source: on(st.AIRPLANE) ? "network-flightmode-on" : "network-mobile-" + [0, 20, 60, 80, 100][cellLevel()]; color: Kirigami.Theme.textColor; isMask: true; Layout.preferredWidth: compact.iconPx; Layout.preferredHeight: width }
            Kirigami.Icon { source: "notifications"; visible: parseInt(st.NOTIF || 0) > 0; Layout.preferredWidth: compact.iconPx; Layout.preferredHeight: width }
            PC3.Label { text: st.NOTIF || ""; visible: parseInt(st.NOTIF || 0) > 0; font.pointSize: Kirigami.Theme.defaultFont.pointSize * 1.2 }
        }
    }

    // ------------------------------------------------------------ popup
    fullRepresentation: PC3.ScrollView {
        id: popup
        // Never wider than the screen, nor taller than the room above the panel (the rest scrolls)
        Layout.preferredWidth: Math.min(Kirigami.Units.gridUnit * 24, Screen.width - Kirigami.Units.gridUnit * 2)
        Layout.minimumWidth: Layout.preferredWidth
        Layout.maximumWidth: Layout.preferredWidth
        Layout.preferredHeight: Math.min(Kirigami.Units.gridUnit * 30, Screen.desktopAvailableHeight - Kirigami.Units.gridUnit * 5)
        Layout.maximumHeight: Layout.preferredHeight
        QQC2.ScrollBar.horizontal.policy: QQC2.ScrollBar.AlwaysOff
        ColumnLayout {
            width: popup.availableWidth
            spacing: Kirigami.Units.smallSpacing

            // ---- Wi-Fi
            RowLayout {
                Kirigami.Heading { text: "Wi-Fi"; level: 3; Layout.fillWidth: true }
                PC3.Switch { checked: on(st.WIFI_ON); onToggled: root.android(checked ? "wifi_on" : "wifi_off") }
                PC3.ToolButton { icon.name: "view-refresh"; onClicked: root.android("wifi_scan") }
            }
            PC3.Label { visible: on(st.WIFI_ON); text: st.WIFI ? t("Connected to") + " " + st.WIFI + " (" + (st.RSSI || "?") + " dBm)" : t("Not connected"); opacity: 0.8 }
            Repeater {
                model: on(st.WIFI_ON) ? root.scan : []
                delegate: PC3.ItemDelegate {
                    required property var modelData
                    Layout.fillWidth: true
                    icon.name: "network-wireless-signal-" + level(rssiLevel(modelData.rssi))
                    text: modelData.ssid + (modelData.ssid == st.WIFI ? "  (" + t("connected") + ")" : "")
                    onClicked: {
                        if (modelData.ssid == st.WIFI) return;
                        root.pendingSsid = modelData.ssid;
                        root.pendingSecure = /WPA|WEP|SAE|PSK|EAP/.test(modelData.flags);
                        if (!root.pendingSecure) { root.android("wifi_connect", modelData.ssid, ""); root.pendingSsid = ""; }
                    }
                }
            }
            RowLayout {
                visible: root.pendingSsid !== "" && root.pendingSecure
                PC3.TextField { id: pw; Layout.fillWidth: true; placeholderText: t("Password for") + " " + root.pendingSsid; echoMode: TextInput.Password; onAccepted: connectButton.clicked() }
                PC3.Button { id: connectButton; text: t("Connect"); onClicked: { root.android("wifi_connect", root.pendingSsid, pw.text); pw.text = ""; root.pendingSsid = ""; } }
                PC3.Button { text: t("Forget"); onClicked: { root.android("wifi_forget", root.pendingSsid); root.pendingSsid = ""; } }
            }

            Kirigami.Separator { Layout.fillWidth: true }
            // ---- Mobile
            RowLayout {
                Kirigami.Heading { text: t("Mobile") + (st.OPERATOR ? " · " + st.OPERATOR : ""); level: 3; Layout.fillWidth: true }
                Kirigami.Icon { source: "network-mobile-" + [0, 20, 60, 80, 100][cellLevel()]; color: Kirigami.Theme.textColor; isMask: true; Layout.preferredWidth: Kirigami.Units.iconSizes.small; Layout.preferredHeight: width }
            }
            RowLayout { PC3.Label { text: t("Mobile data"); Layout.fillWidth: true } PC3.Switch { checked: on(st.DATA_ON); onToggled: root.android(checked ? "data_on" : "data_off") } }
            RowLayout { PC3.Label { text: t("Airplane mode"); Layout.fillWidth: true } PC3.Switch { checked: on(st.AIRPLANE); onToggled: root.android(checked ? "airplane_on" : "airplane_off") } }
            RowLayout { PC3.Label { text: "Bluetooth"; Layout.fillWidth: true } PC3.Switch { checked: on(st.BT_ON); onToggled: root.android(checked ? "bt_on" : "bt_off") } }
            RowLayout { PC3.Label { text: t("Do not disturb"); Layout.fillWidth: true } PC3.Switch { checked: on(st.DND); onToggled: root.android(checked ? "dnd_on" : "dnd_off") } }

            Kirigami.Separator { Layout.fillWidth: true }
            // ---- Battery
            Kirigami.Heading { text: t("Battery"); level: 3 }
            PC3.Label { text: (st.BAT || "?") + "%" + (st.CHG == "1" ? ", " + t("charging") : "") + (st.BAT_TEMP ? " · " + st.BAT_TEMP + " °C" : "") + (st.BAT_VOLT ? " · " + st.BAT_VOLT + " V" : ""); opacity: 0.8 }
            RowLayout { PC3.Label { text: t("Battery saver"); Layout.fillWidth: true } PC3.Switch { checked: on(st.SAVER); onToggled: root.android(checked ? "saver_on" : "saver_off") } }

            Kirigami.Separator { Layout.fillWidth: true }
            // ---- Display and sound
            Kirigami.Heading { text: t("Display and sound"); level: 3 }
            RowLayout {
                Kirigami.Icon { source: "brightness-high"; Layout.preferredWidth: Kirigami.Units.iconSizes.small; Layout.preferredHeight: width }
                PC3.Slider { id: bright; Layout.fillWidth: true; from: 1; to: 255; stepSize: 1
                    Binding on value { value: root.brightLocal >= 0 ? root.brightLocal : parseInt(st.BRIGHT || 128); when: !bright.pressed }
                    onMoved: { root.brightLocal = Math.round(value); settle.restart(); root.android("brightness", Math.round(value)); } }
            }
            RowLayout {
                Kirigami.Icon { source: "audio-volume-high"; Layout.preferredWidth: Kirigami.Units.iconSizes.small; Layout.preferredHeight: width }
                PC3.Slider { id: vol; Layout.fillWidth: true; from: 0; to: parseInt(st.VOL_MAX || 15); stepSize: 1
                    Binding on value { value: root.volLocal >= 0 ? root.volLocal : parseInt(st.VOL || 0); when: !vol.pressed }
                    onMoved: { root.volLocal = Math.round(value); settle.restart(); root.android("volume", Math.round(value)); } }
                PC3.Label { text: (root.volLocal >= 0 ? root.volLocal : (st.VOL || "?")) + "/" + (st.VOL_MAX || 15) }
            }

            Kirigami.Separator { Layout.fillWidth: true }
            Flow {
                Layout.fillWidth: true
                spacing: Kirigami.Units.smallSpacing
                PC3.Button { text: t("Lock"); icon.name: "system-lock-screen"; onClicked: root.android("lock") }
                PC3.Button { text: t("Notifications"); icon.name: "notifications"; onClicked: root.android("notifications") }
                PC3.Button { text: t("Android settings"); icon.name: "configure"; onClicked: root.android("settings") }
            }
        }
    }
}
PHONEQML

# KDE's lock is the phone's: Android locks (its fingerprint or PIN) and KDE never does
greet=$(ls /usr/lib/*/libexec/kscreenlocker_greet /usr/libexec/kscreenlocker_greet 2>/dev/null | head -n 1)
if [ -n "$greet" ] && [ ! -e "$greet.real" ] && dpkg-divert --local --rename --divert "$greet.real" --add "$greet" >/dev/null; then
    printf '%s\n' '#!/bin/sh' "# Flux: the phone locks instead (KDE's own greeter: $greet.real)" \
        '/usr/local/bin/flux-android lock' 'exit 0' > "$greet"
    chmod 755 "$greet"
fi

# Signing out of Plasma ends the desktop as a stop, so the app closes its picture
mkdir -p /etc/xdg/plasma-workspace/shutdown
printf '#!/bin/sh\n: > /tmp/flux-stopping\n' > /etc/xdg/plasma-workspace/shutdown/flux-stopping.sh
chmod 755 /etc/xdg/plasma-workspace/shutdown/flux-stopping.sh

# Settings put back from the desktop itself: the panel's layout, the window look, KDE's tuning
cat > /usr/local/bin/flux-reset <<'RESET'
#!/bin/sh
# flux-reset [panel|windows|tuning|session ...]: with none, ticked in a list
[ -f /tmp/flux-display.conf ] && . /tmp/flux-display.conf
S=${DISPLAY_SCALE:-1.5}; W=${SCREEN_W:-1080}; H=${SCREEN_H:-1200}
sel="$*"
if [ -z "$sel" ]; then
    # Most of the screen (kdialog opens tiny otherwise), below the cutout, in logical px
    dw=$(awk -v w="$W" -v s="$S" 'BEGIN { printf "%d", w / s * 0.94 }')
    dh=$(awk -v h="$H" -v s="$S" 'BEGIN { printf "%d", h / s * 0.62 }')
    dx=$(awk -v w="$W" -v s="$S" -v d="$dw" 'BEGIN { x = (w / s - d) / 2; if (x < 0) x = 0; printf "%d", x }')
    dy=$(awk -v i="${TOP_INSET:-0}" -v s="$S" 'BEGIN { printf "%d", i / s + 24 }')
    sel=$(kdialog --geometry "${dw}x${dh}+${dx}+${dy}" --title "Reset desktop settings" --checklist "What should be put back?" \
        panel "Panel layout" off windows "Window look and buttons" off tuning "Touch tuning (next start)" off \
        session "Restart Plasma" off 2>/dev/null) || exit 0
fi
restart=
for w in $(printf '%s' "$sel" | tr -d '"'); do
    case "$w" in
        panel) rm -f "$HOME/.config/.flux_panel_applied"; : > /tmp/flux-panel-rebuild
               (setsid /usr/bin/python3 /usr/local/bin/flux-display >/dev/null 2>&1 &) ;;
        windows) rm -f "$HOME/.config/.flux_window_look"; /usr/local/bin/flux-window-look
                 dbus-send --session --dest=org.kde.KWin /KWin org.kde.KWin.reconfigure ;;
        tuning) rm -f "$HOME/.config/.flux_tuned_kde" ;;
        session) restart=1 ;;
    esac
done
[ -n "$restart" ] && (setsid plasmashell --replace >/dev/null 2>&1 &)
exit 0
RESET
chmod 755 /usr/local/bin/flux-reset
printf '[Desktop Entry]\nType=Application\nName=Reset desktop settings\nComment=Panel layout, window look, touch tuning\nExec=/usr/local/bin/flux-reset\nIcon=view-refresh\nCategories=System;Settings;\nOnlyShowIn=KDE;\n' \
    > /usr/share/applications/flux-reset.desktop

# Firefox: touch scrolling arrives as wheel steps, so each moves a fair way, smoothly; and
# userChrome.css allowed (the tab bar's margins)
for d in /usr/lib/firefox-esr /usr/lib/firefox; do
    [ -d "$d" ] || continue
    mkdir -p "$d/defaults/pref"
    printf '%s\n' '// Flux Titan Hub: touch scrolling, the tab bar clear of the cutout' \
        'pref("toolkit.legacyUserProfileCustomizations.stylesheets", true);' \
        'pref("mousewheel.default.delta_multiplier_y", 150);' \
        'pref("mousewheel.default.delta_multiplier_x", 150);' \
        'pref("general.smoothScroll", true);' \
        'pref("apz.gtk.kinetic_scroll.enabled", false);' > "$d/defaults/pref/flux.js"
done

fi

# Close desktop, from its own menu: ended as a stop, so the app closes its picture
cat > /usr/local/bin/flux-close <<'CLOSE'
#!/bin/sh
: > /tmp/flux-stopping
kill "$(cat /tmp/flux-xvnc.pid 2>/dev/null)" 2>/dev/null
exit 0
CLOSE
chmod 755 /usr/local/bin/flux-close
printf '[Desktop Entry]\nType=Application\nName=Close desktop\nComment=End the desktop and go back to the phone\nExec=/usr/local/bin/flux-close\nIcon=system-log-out\nCategories=System;\n' \
    > /usr/share/applications/flux-close.desktop

# ---- Xfce tuning: window borders and panel to touch, once, as the session starts ----
cat > /usr/local/bin/flux-tune-xfce <<'TUNE'
#!/bin/sh
stamp="$HOME/.config/.flux_tuned_xfce"
[ -f "$stamp" ] && exit 0
command -v xfconf-query >/dev/null 2>&1 || exit 0
sleep 3
scale=${FLUX_SCALE:-1.5}
case "$scale" in 1|1.0|1.00) theme=Default ;; 2|2.0|2.00) theme=Default-xhdpi ;; *) theme=Default-hdpi ;; esac
[ -d "/usr/share/themes/$theme" ] && xfconf-query -c xfwm4 -p /general/theme -n -t string -s "$theme"
xfconf-query -c xfce4-panel -p /panels/panel-1/size -n -t uint -s 40
xfconf-query -c xfce4-panel -p /panels/panel-1/icon-size -n -t uint -s 0
xfconf-query -c xfce4-desktop -p /desktop-icons/icon-size -n -t uint -s 64
xfconf-query -c xfce4-desktop -p /desktop-icons/single-click -n -t bool -s true
mkdir -p "$HOME/.config"
touch "$stamp"
TUNE
chmod 755 /usr/local/bin/flux-tune-xfce

# ---- Linux notifications to the phone's: each one queued for the app to post ----
cat > /usr/local/bin/flux-notify-bridge <<'BRIDGE'
#!/usr/bin/env python3
"""Every org.freedesktop.Notifications.Notify on the session bus, one line each in
/tmp/flux-notify.queue: tag, title, body, app (tab-separated). The app posts them."""
import re, subprocess, sys, time
QUEUE = "/tmp/flux-notify.queue"
STRING = re.compile(r'^\s+string "(.*)"$')
UINT = re.compile(r'^\s+uint32 (\d+)$')
def unescape(s):
    return s.encode("utf-8").decode("unicode_escape", errors="replace").encode("latin-1", errors="replace").decode("utf-8", errors="replace")
def clean(s):
    return re.sub(r"<[^>]+>", "", s).replace("\t", " ").replace("\r", " ").replace("\n", " ").strip()
def main():
    counter = int(time.time()) % 100000
    proc = subprocess.Popen(["dbus-monitor", "--session", "interface='org.freedesktop.Notifications',member='Notify'"],
                            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True, errors="replace")
    fields = None
    for line in proc.stdout:
        if "member=Notify" in line and "method call" in line:
            fields = []
            continue
        if fields is None:
            continue
        m = STRING.match(line) or UINT.match(line)
        if m:
            fields.append(m.group(1))
        if len(fields) == 5:
            app, rid, _icon, summary, body = fields
            fields = None
            summary, body, app = clean(unescape(summary)), clean(unescape(body)), clean(unescape(app))
            if not summary and not body:
                continue
            if rid.isdigit() and int(rid) > 0:
                tag = f"{app or 'linux'}-{rid}"
            else:
                counter += 1
                tag = f"linux-{counter}"
            with open(QUEUE, "a") as f:
                f.write(f"{tag}\t{summary or app}\t{body}\t{app}\n")
    sys.exit(proc.wait())
if __name__ == "__main__":
    main()
BRIDGE
chmod 755 /usr/local/bin/flux-notify-bridge

# ---- The session ----
cat > /usr/local/bin/flux-session <<'SESSION'
#!/bin/sh
# The desktop: TigerVNC's X server on 127.0.0.1:5901 (with the app's password), its sound from
# PulseAudio as plain PCM on 127.0.0.1:5911 (the app plays it), the desktop on both, sized up
# by FLUX_SCALE for a phone's screen
geometry=${FLUX_GEOMETRY:-1920x1080}
scale=$(printf '%s' "${FLUX_SCALE:-1.5}" | tr -cd '0-9.')
[ -n "$scale" ] || scale=1.5
export XDG_RUNTIME_DIR="/tmp/runtime-$(id -u)"
mkdir -p "$XDG_RUNTIME_DIR"
chmod 700 "$XDG_RUNTIME_DIR"
[ -f /etc/default/locale ] && . /etc/default/locale
export LANG="${LANG:-C.UTF-8}"
mkdir -p "$HOME/.vnc"
printf '%s\n' "$FLUX_PASSWORD" | vncpasswd -f > "$HOME/.vnc/passwd"
chmod 600 "$HOME/.vnc/passwd"
rm -f /tmp/.X1-lock /tmp/.X11-unix/X1
mkdir -p /tmp/.X11-unix
chmod 1777 /tmp/.X11-unix 2>/dev/null
dpi=$(awk -v s="$scale" 'BEGIN { printf "%d", 96 * s + 0.5 }')
# The phone screen's shape, as the app measured it (size, corners' radius, the cutout's band and
# how far right it reaches, in pixels; none on a TV), and the settings KDE lays itself out by
set -- $(printf '%s' "${FLUX_SCREEN:-}" | tr -cd '0-9 ') 0 0 0 0 0
screen_w=$1; screen_h=$2; radius=$3; band=$4; hole=$5
[ "$screen_w" -gt 0 ] 2>/dev/null || { screen_w=${geometry%x*}; screen_h=${geometry#*x}; }
printf '%s\n' "SCREEN_W=$screen_w" "SCREEN_H=$screen_h" "CORNER_RADIUS=$radius" "TOP_INSET=$band" "CUTOUT_RIGHT=$hole" \
    "DISPLAY_SCALE=$scale" "PANEL_HEIGHT=52" "PANEL_HIDING=dodgewindows" "CORNER_PAD=32" "SPACER_PX=20" "WINDOW_RADIUS=24" \
    "EFFECTS=${FLUX_EFFECTS:-1}" > /tmp/flux-display.conf
# Never turns an address away after failed sign-ins: it answers only this phone, with a new
# password each run, and the one address it would shut out is the app's own viewer
Xvnc :1 -geometry "$geometry" -depth 24 -dpi "$dpi" -rfbport 5901 -localhost -SecurityTypes VncAuth \
    -PasswordFile "$HOME/.vnc/passwd" -AlwaysShared -AcceptSetDesktopSize -BlacklistThreshold 1000000 \
    >/tmp/flux-xvnc.log 2>&1 &
echo $! > /tmp/flux-xvnc.pid
# Sound: everything plays into a silent sink whose monitor goes out as raw 48 kHz stereo
# Programs reach it over 127.0.0.1 too (no socket files without root), proven by its cookie
export PULSE_SERVER=tcp:127.0.0.1:4713
if command -v pulseaudio >/dev/null 2>&1; then
    pulseaudio --kill >/dev/null 2>&1
    pulseaudio --daemonize=no --exit-idle-time=-1 --disallow-exit -n --disable-shm=yes \
        -L "module-null-sink sink_name=flux rate=48000 channels=2" \
        -L "module-native-protocol-tcp listen=127.0.0.1 port=4713" \
        -L "module-simple-protocol-tcp listen=127.0.0.1 port=5911 record=true playback=false source=flux.monitor format=s16le rate=48000 channels=2" \
        >/tmp/flux-pulse.log 2>&1 &
    echo $! > /tmp/flux-pulse.pid
    sleep 1
    pactl set-default-sink flux >/dev/null 2>&1
fi
sleep 1
export DISPLAY=:1
# The session's bus on 127.0.0.1 (a free port), callers proven by the cookie only this user can
# read: without root Android won't let Linux make socket files. Started here so the phone's
# shortcuts can open programs in this session; dbus-launch the second try. Never an empty
# address: every program refuses one, where with none the desktop starts a bus of its own
conf=/etc/flux-dbus-session.conf
if [ -f "$conf" ]; then
    bus=$(dbus-daemon --config-file="$conf" --address="tcp:host=127.0.0.1,bind=127.0.0.1,port=0" \
        --fork --print-address=1 --print-pid=1 2>/tmp/flux-dbus.log)
    DBUS_SESSION_BUS_ADDRESS=$(printf '%s\n' "$bus" | sed -n 1p)
    DBUS_SESSION_BUS_PID=$(printf '%s\n' "$bus" | sed -n 2p)
fi
if [ -n "${DBUS_SESSION_BUS_ADDRESS:-}" ]; then
    export DBUS_SESSION_BUS_ADDRESS
else
    eval "$(dbus-launch --sh-syntax 2>>/tmp/flux-dbus.log)"
fi
[ -n "${DBUS_SESSION_BUS_ADDRESS:-}" ] || { unset DBUS_SESSION_BUS_ADDRESS; echo "flux: no session bus" >> /tmp/flux-dbus.log; }
gdk_int=$(awk -v s="$scale" 'BEGIN { i = int(s); if (i < 1) i = 1; print i }')
gdk_dpi=$(awk -v s="$scale" -v i="$gdk_int" 'BEGIN { printf "%.3f", s / i }')
cursor=$(awk -v s="$scale" 'BEGIN { printf "%d", 24 * s + 0.5 }')
# Everything the desktop and the programs opened from the phone share: the scale (Qt in full,
# GTK whole steps plus its fonts), drawing on the CPU the quick way, no client-side decorations
{
    echo "export DISPLAY=:1 LANG='$LANG' XDG_SESSION_TYPE=x11 QT_QPA_PLATFORM=xcb"
    echo "export XDG_RUNTIME_DIR='$XDG_RUNTIME_DIR'"
    [ -n "${DBUS_SESSION_BUS_ADDRESS:-}" ] && echo "export DBUS_SESSION_BUS_ADDRESS='$DBUS_SESSION_BUS_ADDRESS'"
    echo "export QT_SCALE_FACTOR=$scale GDK_SCALE=$gdk_int GDK_DPI_SCALE=$gdk_dpi XCURSOR_SIZE=$cursor FLUX_SCALE=$scale"
    echo "export QT_QUICK_BACKEND=software QSG_RENDER_LOOP=basic LIBGL_ALWAYS_SOFTWARE=1 MESA_NO_ERROR=1"
    echo "export QT_LOGGING_RULES='kpipewire_logging=false;kf.kirigami.platform.warning=false'"
    echo "export GTK_CSD=0 MOZ_USE_XINPUT2=0"
    # Which desktop it is, so a program opened from the phone takes on its look (KDE's theme and
    # colours load only for a KDE session)
    if [ "$(cat /etc/flux-desktop 2>/dev/null)" = kde ]; then
        echo "export XDG_CURRENT_DESKTOP=KDE XDG_SESSION_DESKTOP=KDE DESKTOP_SESSION=plasma KDE_FULL_SESSION=true KDE_SESSION_VERSION=6"
    else
        echo "export XDG_CURRENT_DESKTOP=XFCE XDG_SESSION_DESKTOP=xfce DESKTOP_SESSION=xfce"
    fi
    # Sound over 127.0.0.1; no accessibility bus (it would want a socket file)
    echo "export PULSE_SERVER=$PULSE_SERVER NO_AT_BRIDGE=1"
    # The phone's GPU, when the app started its server: Mesa's virpipe driver sends OpenGL there
    # (the desktop's own parts kept on the CPU by flux-linux.sh)
    [ -S /tmp/.virgl_test ] && [ -e /tmp/flux-gpu ] && echo "export GALLIUM_DRIVER=virpipe VTEST_SOCKET_NAME=/tmp/.virgl_test MESA_GL_VERSION_OVERRIDE=4.3 MESA_GLSL_VERSION_OVERRIDE=430"
    # PRoot can't give Firefox its sandbox: without it, rather than not at all
    [ "$FLUX_MODE" = proot ] && echo "export MOZ_DISABLE_CONTENT_SANDBOX=1 MOZ_DISABLE_GMP_SANDBOX=1 MOZ_DISABLE_RDD_SANDBOX=1 MOZ_DISABLE_SOCKET_PROCESS_SANDBOX=1"
} > /tmp/flux-env
. /tmp/flux-env
# Linux's notifications, queued for the phone to show
: > /tmp/flux-notify.queue
if command -v dbus-monitor >/dev/null 2>&1 && command -v python3 >/dev/null 2>&1; then
    python3 /usr/local/bin/flux-notify-bridge >/dev/null 2>&1 &
    echo $! > /tmp/flux-notify.pid
fi
# The desktop's output, appended (programs writing at their own offsets would overwrite the
# lines added after them)
: > /tmp/flux-session.log
# The desktop chosen at install: KDE Plasma (tuned once, fitted each start, its effects on unless
# the app has them off), or Xfce
if [ "$(cat /etc/flux-desktop 2>/dev/null)" = kde ]; then
    /usr/local/bin/flux-tune-kde >/tmp/flux-tune.log 2>&1 || true
    /usr/local/bin/flux-kde-session >>/tmp/flux-tune.log 2>&1 || true
    if [ "${FLUX_EFFECTS:-1}" = 1 ]; then
        startplasma-x11 >>/tmp/flux-session.log 2>&1 &
    else
        KWIN_COMPOSE=N startplasma-x11 >>/tmp/flux-session.log 2>&1 &
    fi
else
    (/usr/local/bin/flux-tune-xfce >/tmp/flux-tune.log 2>&1 &)
    # The session manager's own account of itself (~/.xfce4-session.verbose-log), for when it ends
    XFSM_VERBOSE=1 startxfce4 >>/tmp/flux-session.log 2>&1 &
fi
echo $! > /tmp/flux-session.pid
rm -f /tmp/flux-stopping
# Ready once the desktop itself is drawing (its desktop or panel running), not merely the X
# server: Xfce or Plasma can take most of a minute under PRoot, a black screen till then
rm -f /tmp/flux-ready
(
    i=0
    while [ $i -lt 180 ]; do
        for c in /proc/[0-9]*/comm; do
            case "$(cat "$c" 2>/dev/null)" in xfdesktop|xfce4-panel|plasmashell) sleep 2; touch /tmp/flux-ready; exit 0 ;; esac
        done
        sleep 1; i=$((i + 1))
    done
    touch /tmp/flux-ready
) &
wait "$(cat /tmp/flux-session.pid)"
ended=$?
# What was still up when it ended: which part went first
alive() { kill -0 "$1" 2>/dev/null && echo up || echo gone; }
if [ -e /tmp/flux-stopping ]; then
    echo "flux: the desktop was stopped" >> /tmp/flux-session.log
else
    echo "flux: the desktop ended by itself ($ended); X server $(alive "$(cat /tmp/flux-xvnc.pid)"), bus $(alive "$DBUS_SESSION_BUS_PID")" >> /tmp/flux-session.log
fi
rm -f /tmp/flux-stoppingkill "$DBUS_SESSION_BUS_PID" "$(cat /tmp/flux-xvnc.pid)" "$(cat /tmp/flux-pulse.pid 2>/dev/null)" \
    "$(cat /tmp/flux-notify.pid 2>/dev/null)" 2>/dev/null
rm -f /tmp/flux-xvnc.pid /tmp/flux-session.pid /tmp/flux-pulse.pid /tmp/flux-notify.pid /tmp/flux-env /tmp/flux-ready
SESSION
chmod 755 /usr/local/bin/flux-session

# One program in the running desktop, from the phone's shortcuts
cat > /usr/local/bin/flux-run <<'RUN'
#!/bin/sh
[ -f /tmp/flux-env ] && . /tmp/flux-env
cd "$HOME"
if [ "$FLUX_TERMINAL" = 1 ]; then
    if command -v konsole >/dev/null 2>&1; then exec konsole -e sh -c "$FLUX_EXEC"; fi
    exec xfce4-terminal -e "$FLUX_EXEC"
fi
exec sh -c "$FLUX_EXEC"
RUN
chmod 755 /usr/local/bin/flux-run

# Each program's icon on the desktop (Xfce's or Plasma's), added once (one deleted stays deleted), as it signs in
cat > /usr/local/bin/flux-desktop-icons <<'ICONS'
#!/bin/sh
desk="$HOME/Desktop"
seen="$HOME/.config/flux-desktop-icons"
mkdir -p "$desk" "$HOME/.config"
touch "$seen"
for f in /usr/share/applications/*.desktop /usr/local/share/applications/*.desktop; do
    [ -f "$f" ] || continue
    name=${f##*/}
    grep -qxF "$name" "$seen" && continue
    echo "$name" >> "$seen"
    grep -q '^Type=Application' "$f" || continue
    grep -q '^NoDisplay=true' "$f" && continue
    grep -q '^OnlyShowIn=' "$f" && continue
    grep -q '^Categories=.*Settings' "$f" && continue
    cp "$f" "$desk/$name"
    chmod +x "$desk/$name"
    # Trusted, so the desktop opens it without asking first
    gio set -t string "$desk/$name" metadata::xfce-exe-checksum "$(sha256sum "$desk/$name" | cut -d' ' -f1)" 2>/dev/null
done
ICONS
chmod 755 /usr/local/bin/flux-desktop-icons
mkdir -p /etc/xdg/autostart
printf '[Desktop Entry]\nType=Application\nName=Flux desktop icons\nExec=/usr/local/bin/flux-desktop-icons\nNoDisplay=true\n' > /etc/xdg/autostart/flux-desktop-icons.desktop
echo "$FLUX_PACKAGES_VERSION" > /etc/flux-packages-version
echo "$FLUX_GUEST_VERSION" > /etc/flux-guest-version
echo "flux: guest ready"
