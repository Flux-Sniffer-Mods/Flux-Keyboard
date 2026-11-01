# Flux Titan Hub's Linux desktop: Debian or Ubuntu, with Xfce or KDE Plasma, seen through VNC.
# Run by Android's sh, as the built-in shell (proot: no root) or as root through su (chroot).
#   flux-linux.sh install | start | stop | status | remove | apps | icons NAME... | launch | notifications
# From the app, in the environment:
#   MODE     proot or chroot
#   BASE     where it lives (/data/local/tmp/flux-linux, or /data/local/flux-linux with root)
#   TARBALL  the distribution's root file system (a .tar.gz), for install
#   DISTRO   debian or ubuntu, DESKTOP xfce or kde, for install and start
#   LIBDIR   the app's native libraries: libproot.so and libproot-loader.so (proot), libvirgl.so (GPU)
#   GUEST    the script that sets Linux up (flux-guest.sh), for install and start
#   GEOMETRY the desktop's size (1920x1080), PASSWORD its VNC password, SCALE its size-up (1.5), for start
#   GPU      1 to lend Linux's programs the phone's GPU, for start
#   LOCALE   the phone's language (de_DE), TZNAME its time zone (Europe/Berlin), for install and start
#   EXEC     a program's command line, TERMINAL 1 to run it in a terminal, for launch

set -u
ROOTFS="$BASE/rootfs"
PIDS="$BASE/run"
mkdir -p "$BASE" "$PIDS"

say() { echo "flux: $*"; }

# PRoot copied beside the system (the shell may run what's here, not from the app's folder)
proot_ready() {
    mkdir -p "$BASE/bin" "$BASE/tmp"
    cp "$LIBDIR/libproot.so" "$BASE/bin/proot" && cp "$LIBDIR/libproot-loader.so" "$BASE/bin/loader" || return 1
    chmod 755 "$BASE/bin/proot" "$BASE/bin/loader"
    export PROOT_LOADER="$BASE/bin/loader" PROOT_TMP_DIR="$BASE/tmp"
}

# A command inside Linux, as root
guest() {
    if [ "$MODE" = proot ]; then
        "$BASE/bin/proot" --kill-on-exit --link2symlink -0 -r "$ROOTFS" \
            -b /dev -b /proc -b /sys -b /sdcard -b /storage -b "$ROOTFS/tmp:/dev/shm" \
            -w /root /usr/bin/env -i HOME=/root USER=root TERM=xterm-256color LANG=C.UTF-8 \
            PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin "$@"
    else
        chroot "$ROOTFS" /usr/bin/env -i HOME=/root USER=root TERM=xterm-256color LANG=C.UTF-8 \
            PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin "$@"
    fi
}

# What a chroot needs from Android (after Titan-2-Elite-Debian-Chroot): the chroot on a mount
# of its own that allows setuid and devices (Android mounts /data without them, so sudo and
# device nodes fail; this way /data itself is left as it is), its own /dev with only the
# usual devices (and the GPU's), processes, a fresh /run, shared memory and storage
chroot_mounts() {
    C="$ROOTFS"
    if ! grep -q " $C " /proc/mounts; then
        mount --bind "$C" "$C" && mount -o remount,bind,suid,dev,exec "$C" "$C" \
            || say "the chroot couldn't allow setuid: sudo inside may fail"
    fi
    grep -q " $C/proc " /proc/mounts && return 0
    mkdir -p "$C/proc" "$C/sys" "$C/dev" "$C/tmp" "$C/run" "$C/sdcard"
    chmod 1777 "$C/tmp"
    mount -t proc proc "$C/proc"
    mount -t sysfs sysfs "$C/sys"
    mount -t tmpfs -o mode=755,dev,suid,exec tmpfs "$C/dev"
    mkdir -p "$C/dev/pts" "$C/dev/shm" "$C/dev/dri"
    mount -t devpts -o newinstance,gid=5,mode=620,ptmxmode=0666 devpts "$C/dev/pts"
    mount -t tmpfs -o mode=1777 tmpfs "$C/dev/shm"
    for spec in "null 1 3" "zero 1 5" "full 1 7" "random 1 8" "urandom 1 9" "tty 5 0"; do
        set -- $spec
        rm -f "$C/dev/$1"
        # Where making the device is refused, Android's own is lent instead
        mknod -m 666 "$C/dev/$1" c "$2" "$3" 2>/dev/null \
            || { : > "$C/dev/$1"; mount --bind "/dev/$1" "$C/dev/$1" 2>/dev/null || say "no /dev/$1 inside"; }
    done
    ln -sf pts/ptmx "$C/dev/ptmx"
    ln -sf /proc/self/fd "$C/dev/fd"
    ln -sf /proc/self/fd/0 "$C/dev/stdin"
    ln -sf /proc/self/fd/1 "$C/dev/stdout"
    ln -sf /proc/self/fd/2 "$C/dev/stderr"
    [ -d /dev/dri ] && mount --bind /dev/dri "$C/dev/dri"
    mount -t tmpfs -o mode=755 tmpfs "$C/run"
    mkdir -p "$C/run/dbus" "$C/run/lock"
    [ -L "$C/var/run" ] || { rm -rf "$C/var/run"; ln -s /run "$C/var/run"; }
    mount --bind /data/media/0 "$C/sdcard"
}

# Everything mounted in the chroot let go, deepest first, then the chroot's own mount
chroot_unmount() {
    for m in $(grep " $ROOTFS/" /proc/mounts | cut -d' ' -f2 | sort -r); do
        umount -l "$m" 2>/dev/null
    done
    grep -q " $ROOTFS " /proc/mounts && umount -l "$ROOTFS" 2>/dev/null
    return 0
}

# Anything at all still mounted in or on the chroot
mounted() { grep -q " $ROOTFS[ /]" /proc/mounts; }

# Every process whose root is inside the chroot, ended (the desktop and anything it left)
kill_chroot_procs() {
    for p in /proc/[0-9]*; do
        r=$(readlink "$p/root" 2>/dev/null) || continue
        case "$r" in "$ROOTFS"|"$ROOTFS"/*) kill -9 "${p#/proc/}" 2>/dev/null ;; esac
    done
    return 0
}

# The desktop's parts that draw better on the CPU while programs use the GPU (after
# Titan-2-Elite-Debian-Chroot: KWin, the Plasma shell, its splash and search flicker or lose
# compositing on virgl)
CPU_ONLY="kwin_x11 plasmashell ksplashqml krunner"

# The phone's GPU lent to Linux's programs: virglrenderer's server, outside Linux on Android's
# own GPU driver, its socket in Linux's /tmp where Mesa's virpipe driver finds it. Without it
# (turned off, or it won't start) everything draws on the CPU as before
gpu_start() {
    gpu_stop
    [ "${GPU:-0}" = 1 ] || return 0
    sock="$ROOTFS/tmp/.virgl_test"
    mkdir -p "$BASE/bin"
    if ! { cp "$LIBDIR/libvirgl.so" "$BASE/bin/virgl" && chmod 755 "$BASE/bin/virgl"; }; then
        say "no GPU server here: drawing on the CPU"
        return 0
    fi
    "$BASE/bin/virgl" --use-egl-surfaceless --use-gles --socket-path "$sock" > "$ROOTFS/tmp/flux-gpu.log" 2>&1 &
    echo $! > "$PIDS/virgl.pid"
    i=0
    while [ $i -lt 20 ] && [ ! -S "$sock" ]; do sleep 0.25; i=$((i + 1)); done
    if [ ! -S "$sock" ]; then
        say "the GPU server didn't start: drawing on the CPU"
        gpu_stop
        return 0
    fi
    # Reachable by Linux's own user too (chroot)
    chmod 666 "$sock"
    for prog in $CPU_ONLY; do
        [ -x "$ROOTFS/usr/bin/$prog" ] || continue
        printf '%s\n' '#!/bin/sh' "# $prog on the CPU while programs draw on the GPU (flux-linux.sh)" \
            'export GALLIUM_DRIVER=llvmpipe; unset VTEST_SOCKET_NAME' "exec /usr/bin/$prog \"\$@\"" > "$ROOTFS/usr/local/bin/$prog"
        chmod 755 "$ROOTFS/usr/local/bin/$prog"
    done
    touch "$ROOTFS/tmp/flux-gpu"
    say "programs draw on the phone's GPU"
}

gpu_stop() {
    [ -f "$PIDS/virgl.pid" ] && kill "$(cat "$PIDS/virgl.pid")" 2>/dev/null
    rm -f "$PIDS/virgl.pid" "$ROOTFS/tmp/.virgl_test" "$ROOTFS/tmp/flux-gpu"
    for prog in $CPU_ONLY; do
        grep -q 'flux-linux.sh' "$ROOTFS/usr/local/bin/$prog" 2>/dev/null && rm -f "$ROOTFS/usr/local/bin/$prog"
    done
    return 0
}

# The phone's time zone (and language, for the setup) carried into Linux
phone_settings() {
    zone=$(printf '%s' "${TZNAME:-}" | tr -cd 'A-Za-z0-9_/+-')
    if [ -n "$zone" ] && [ -f "$ROOTFS/usr/share/zoneinfo/$zone" ]; then
        ln -sf "/usr/share/zoneinfo/$zone" "$ROOTFS/etc/localtime"
        echo "$zone" > "$ROOTFS/etc/timezone"
    fi
}

install() {
    if [ ! -x "$ROOTFS/usr/bin/env" ]; then
        say "unpacking Linux"
        [ "$MODE" = chroot ] && { kill_chroot_procs; chroot_unmount; }
        # Never through a mount still in place: the phone's own files would go with it
        if mounted; then say "still mounted, not unpacked"; exit 6; fi
        rm -rf "$ROOTFS"
        mkdir -p "$ROOTFS"
        if [ "$MODE" = proot ]; then
            proot_ready || { say "proot missing"; exit 2; }
            # Under PRoot, so links Android won't make are kept as PRoot's own
            "$BASE/bin/proot" --link2symlink -0 /system/bin/tar -xzf "$TARBALL" -C "$ROOTFS" || { say "unpack failed"; exit 3; }
        else
            tar -xzf "$TARBALL" -C "$ROOTFS" || { say "unpack failed"; exit 3; }
        fi
    fi
    [ "$MODE" = proot ] && { proot_ready || exit 2; }
    # Names resolved through public DNS: Android has no resolv.conf to share
    rm -f "$ROOTFS/etc/resolv.conf"
    printf 'nameserver 1.1.1.1\nnameserver 8.8.8.8\n' > "$ROOTFS/etc/resolv.conf"
    printf '127.0.0.1 localhost\n::1 localhost\n127.0.1.1 flux\n' > "$ROOTFS/etc/hosts"
    echo flux > "$ROOTFS/etc/hostname"
    cp "$GUEST" "$ROOTFS/root/flux-guest.sh"
    [ "$MODE" = chroot ] && chroot_mounts
    say "installing the desktop (a few minutes)"
    phone_settings
    guest /usr/bin/env FLUX_LOCALE="${LOCALE:-en_US}" /bin/sh /root/flux-guest.sh "$MODE" "${DISTRO:-debian}" "${DESKTOP:-xfce}"
    result=$?
    [ "$MODE" = chroot ] && chroot_unmount
    [ $result -eq 0 ] || { say "install failed"; exit 4; }
    touch "$BASE/installed"
    say "installed"
}

start() {
    [ -f "$BASE/installed" ] || { say "not installed"; exit 5; }
    stop >/dev/null 2>&1
    [ "$MODE" = proot ] && { proot_ready || exit 2; }
    [ "$MODE" = chroot ] && chroot_mounts
    echo $$ > "$PIDS/start.pid"
    # Set up by an older version: brought up to date first (new packages, the session script)
    wanted=$(sed -n 's/^FLUX_GUEST_VERSION=//p' "$GUEST")
    if [ "$(cat "$ROOTFS/etc/flux-guest-version" 2>/dev/null)" != "$wanted" ]; then
        say "updating the desktop"
        cp "$GUEST" "$ROOTFS/root/flux-guest.sh"
        guest /usr/bin/env FLUX_LOCALE="${LOCALE:-en_US}" /bin/sh /root/flux-guest.sh "$MODE" "${DISTRO:-debian}" "${DESKTOP:-xfce}" || say "update failed, starting anyway"
    fi
    phone_settings
    gpu_start
    say "starting"
    if [ "$MODE" = proot ]; then
        guest /usr/bin/env FLUX_GEOMETRY="$GEOMETRY" FLUX_PASSWORD="$PASSWORD" FLUX_MODE=proot FLUX_SCALE="${SCALE:-1.5}" /usr/local/bin/flux-session
    else
        guest /usr/bin/env FLUX_GEOMETRY="$GEOMETRY" FLUX_PASSWORD="$PASSWORD" FLUX_MODE=chroot FLUX_SCALE="${SCALE:-1.5}" \
            /bin/su - flux -w FLUX_GEOMETRY,FLUX_PASSWORD,FLUX_MODE,FLUX_SCALE -c /usr/local/bin/flux-session
        kill_chroot_procs
        chroot_unmount
    fi
    gpu_stop
    rm -f "$PIDS/start.pid"
    say "stopped"
}

# Ends the desktop: the VNC server first (the session follows it), then whatever's left
stop() {
    for f in "$ROOTFS/tmp/flux-xvnc.pid" "$ROOTFS/tmp/flux-session.pid" "$ROOTFS/tmp/flux-pulse.pid"; do
        [ -f "$f" ] && kill "$(cat "$f")" 2>/dev/null
    done
    sleep 1
    [ -f "$PIDS/start.pid" ] && kill "$(cat "$PIDS/start.pid")" 2>/dev/null
    rm -f "$ROOTFS/tmp/flux-xvnc.pid" "$ROOTFS/tmp/flux-session.pid" "$ROOTFS/tmp/flux-pulse.pid" "$ROOTFS/tmp/flux-env" "$PIDS/start.pid"
    gpu_stop
    [ "$MODE" = chroot ] && { kill_chroot_procs; chroot_unmount; }
    say "stopped"
}

status() {
    [ -f "$BASE/installed" ] && echo "installed" || echo "not-installed"
    if [ -f "$ROOTFS/tmp/flux-xvnc.pid" ] && kill -0 "$(cat "$ROOTFS/tmp/flux-xvnc.pid")" 2>/dev/null; then
        echo running
    else
        echo stopped
    fi
}

remove() {
    stop >/dev/null 2>&1
    [ "$MODE" = chroot ] && { kill_chroot_procs; chroot_unmount; }
    # Never through a mount still in place: the phone's own files would go with it
    if mounted; then say "still mounted, not removed"; exit 6; fi
    rm -rf "$BASE"
    say "removed"
}

# Every program's .desktop file, each after a line "@@app NAME" (the app reads them)
apps() {
    for dir in "$ROOTFS/usr/share/applications" "$ROOTFS/usr/local/share/applications" \
        "$ROOTFS/root/.local/share/applications" "$ROOTFS/home/flux/.local/share/applications"; do
        for f in "$dir"/*.desktop; do
            [ -f "$f" ] || continue
            echo "@@app ${f##*/}"
            cat "$f"
            echo
        done
    done
}

# Each named icon as a PNG in base64, after a line "@@icon NAME"; nothing for one not found
icons() {
    for name in "$@"; do
        case "$name" in *..*) continue ;; esac
        found=""
        case "$name" in
            /*) [ -f "$ROOTFS$name" ] && found="$ROOTFS$name" ;;
            *)
                for size in 64x64 48x48 96x96 128x128 256x256 32x32; do
                    for theme in hicolor Adwaita; do
                        f="$ROOTFS/usr/share/icons/$theme/$size/apps/$name.png"
                        [ -z "$found" ] && [ -f "$f" ] && found="$f"
                    done
                done
                [ -z "$found" ] && [ -f "$ROOTFS/usr/share/pixmaps/$name.png" ] && found="$ROOTFS/usr/share/pixmaps/$name.png"
                ;;
        esac
        case "$found" in *.png) echo "@@icon $name"; base64 "$found"; echo ;; esac
    done
}

# A program opened in the running desktop, left running after this returns
launch() {
    [ -f "$ROOTFS/tmp/flux-env" ] || { say "not running"; exit 7; }
    setsid sh "$0" run-app </dev/null >/dev/null 2>&1 &
    say "launched"
}

run_app() {
    if [ "$MODE" = proot ]; then
        proot_ready || exit 2
        guest /usr/bin/env FLUX_EXEC="$EXEC" FLUX_TERMINAL="${TERMINAL:-0}" /bin/sh /usr/local/bin/flux-run
    else
        guest /usr/bin/env FLUX_EXEC="$EXEC" FLUX_TERMINAL="${TERMINAL:-0}" /bin/su - flux -w FLUX_EXEC,FLUX_TERMINAL -c /usr/local/bin/flux-run
    fi
}

# The ends of Linux's own logs: the VNC server's, the session's, sound's, the tuning's, the GPU's
logs() {
    for f in flux-xvnc.log flux-session.log flux-pulse.log flux-tune.log flux-gpu.log; do
        [ -s "$ROOTFS/tmp/$f" ] && { echo "== $f"; tail -n 12 "$ROOTFS/tmp/$f"; }
    done
    return 0
}

# Linux's notifications since last asked, one per line (tag, title, body, app), then forgotten
notifications() {
    q="$ROOTFS/tmp/flux-notify.queue"
    [ -s "$q" ] || return 0
    mv "$q" "$q.read" 2>/dev/null || return 0
    cat "$q.read"
    rm -f "$q.read"
}

case "${1:-}" in
    install) install ;;
    start) start ;;
    stop) stop ;;
    status) status ;;
    remove) remove ;;
    apps) apps ;;
    icons) shift; icons "$@" ;;
    launch) launch ;;
    run-app) run_app ;;
    notifications) notifications ;;
    logs) logs ;;
    *) say "install | start | stop | status | remove | apps | icons | launch"; exit 1 ;;
esac
