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
# Raised whenever this script changes what's installed: flux-linux.sh runs it again on start
FLUX_GUEST_VERSION=5
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
        plasma-pa plasma-integration kde-cli-tools kio-extras fonts-noto-core fonts-noto-mono)
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
version=1
stamp="$HOME/.config/.flux_tuned_kde"
[ "$(cat "$stamp" 2>/dev/null)" = "$version" ] && exit 0
K=$(command -v kwriteconfig6 || command -v kwriteconfig5) || exit 0
mkdir -p "$HOME/.config" "$HOME/.config/autostart" "$HOME/.local/share/konsole"
# Dark from the first frame (only when nothing is chosen yet)
if [ ! -f "$HOME/.config/kdeglobals" ] && [ -f /usr/share/color-schemes/BreezeDark.colors ]; then
    cp /usr/share/color-schemes/BreezeDark.colors "$HOME/.config/kdeglobals"
    $K --file kdeglobals --group General --key ColorScheme BreezeDark
fi
$K --file baloofilerc     --group "Basic Settings" --key "Indexing-Enabled" false
$K --file ksmserverrc     --group General --key loginMode emptySession
$K --file ksmserverrc     --group General --key offerShutdown false
$K --file kscreenlockerrc --group Daemon  --key Autolock false
$K --file kscreenlockerrc --group Daemon  --key LockOnResume false
$K --file kwinrc          --group Plugins --key blurEnabled false
$K --file kwinrc          --group Plugins --key contrastEnabled false
$K --file kwinrc          --group Compositing --key Enabled false
# Touch: tablet mode (bigger controls), a single tap opens, new windows maximised
$K --file kdeglobals      --group KDE     --key TabletMode on
$K --file kdeglobals      --group KDE     --key SingleClick true
$K --file kdeglobals      --group KDE     --key AnimationDurationFactor 0.5
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
$K --file kdeglobals --group SmallIcons   --key Size 22
$K --file kdeglobals --group ToolbarIcons --key Size 32
$K --file kdeglobals --group MainToolbarIcons --key Size 32
$K --file kdeglobals --group DialogIcons  --key Size 48
$K --file kdeglobals --group PanelIcons   --key Size 32
$K --file kdeglobals --group KDE          --key ScrollbarLeftClickNavigatesByPage false
$K --file konsolerc  --group "Desktop Entry" --key DefaultProfile Flux.profile
printf '[Appearance]\nFont=Noto Sans Mono,12,-1,5,50,0,0,0,0,0\n[General]\nName=Flux\nParent=FALLBACK/\n[Scrolling]\nScrollBarPosition=2\n' \
    > "$HOME/.local/share/konsole/Flux.profile"
$K --file dolphinrc  --group General   --key ShowFullPath false
$K --file dolphinrc  --group IconsMode --key PreviewSize 64
$K --file startkderc --group General   --key systemdBoot false
# Background helpers a phone has no use for
for a in org.kde.discover.notifier baloo_file org.kde.kalendarac geoclue-demo-agent org.kde.kgpg \
         kdeconnectd org.kde.kdeconnect.daemon; do
    printf '[Desktop Entry]\nHidden=true\n' > "$HOME/.config/autostart/$a.desktop"
done
echo "$version" > "$stamp"
TUNE
chmod 755 /usr/local/bin/flux-tune-kde

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
Xvnc :1 -geometry "$geometry" -depth 24 -dpi "$dpi" -rfbport 5901 -localhost -SecurityTypes VncAuth \
    -PasswordFile "$HOME/.vnc/passwd" -AlwaysShared -AcceptSetDesktopSize >/tmp/flux-xvnc.log 2>&1 &
echo $! > /tmp/flux-xvnc.pid
# Sound: everything plays into a silent sink whose monitor goes out as raw 48 kHz stereo
if command -v pulseaudio >/dev/null 2>&1; then
    pulseaudio --kill >/dev/null 2>&1
    pulseaudio --daemonize=no --exit-idle-time=-1 --disallow-exit -n --disable-shm=yes \
        -L "module-null-sink sink_name=flux rate=48000 channels=2" \
        -L "module-native-protocol-unix" \
        -L "module-simple-protocol-tcp listen=127.0.0.1 port=5911 record=true playback=false source=flux.monitor format=s16le rate=48000 channels=2" \
        >/tmp/flux-pulse.log 2>&1 &
    echo $! > /tmp/flux-pulse.pid
    sleep 1
    pactl set-default-sink flux >/dev/null 2>&1
fi
sleep 1
export DISPLAY=:1
# The session's bus, started here so the phone's shortcuts can open programs in this session
eval "$(dbus-launch --sh-syntax)"
gdk_int=$(awk -v s="$scale" 'BEGIN { i = int(s); if (i < 1) i = 1; print i }')
gdk_dpi=$(awk -v s="$scale" -v i="$gdk_int" 'BEGIN { printf "%.3f", s / i }')
cursor=$(awk -v s="$scale" 'BEGIN { printf "%d", 24 * s + 0.5 }')
# Everything the desktop and the programs opened from the phone share: the scale (Qt in full,
# GTK whole steps plus its fonts), drawing on the CPU the quick way, no client-side decorations
{
    echo "export DISPLAY=:1 LANG='$LANG' XDG_SESSION_TYPE=x11 QT_QPA_PLATFORM=xcb"
    echo "export XDG_RUNTIME_DIR='$XDG_RUNTIME_DIR'"
    echo "export DBUS_SESSION_BUS_ADDRESS='$DBUS_SESSION_BUS_ADDRESS'"
    echo "export QT_SCALE_FACTOR=$scale GDK_SCALE=$gdk_int GDK_DPI_SCALE=$gdk_dpi XCURSOR_SIZE=$cursor FLUX_SCALE=$scale"
    echo "export QT_QUICK_BACKEND=software QSG_RENDER_LOOP=basic LIBGL_ALWAYS_SOFTWARE=1 MESA_NO_ERROR=1"
    echo "export QT_LOGGING_RULES='kpipewire_logging=false;kf.kirigami.platform.warning=false'"
    echo "export GTK_CSD=0 MOZ_USE_XINPUT2=0"
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
# The desktop chosen at install: KDE Plasma (tuned once, no compositing: no GPU behind VNC), or Xfce
if [ "$(cat /etc/flux-desktop 2>/dev/null)" = kde ]; then
    /usr/local/bin/flux-tune-kde >/tmp/flux-tune.log 2>&1 || true
    KWIN_COMPOSE=N startplasma-x11 >/tmp/flux-session.log 2>&1 &
else
    (/usr/local/bin/flux-tune-xfce >/tmp/flux-tune.log 2>&1 &)
    startxfce4 >/tmp/flux-session.log 2>&1 &
fi
echo $! > /tmp/flux-session.pid
wait $!
kill "$DBUS_SESSION_BUS_PID" "$(cat /tmp/flux-xvnc.pid)" "$(cat /tmp/flux-pulse.pid 2>/dev/null)" \
    "$(cat /tmp/flux-notify.pid 2>/dev/null)" 2>/dev/null
rm -f /tmp/flux-xvnc.pid /tmp/flux-session.pid /tmp/flux-pulse.pid /tmp/flux-notify.pid /tmp/flux-env
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
echo "$FLUX_GUEST_VERSION" > /etc/flux-guest-version
echo "flux: guest ready"
