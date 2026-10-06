#!/system/bin/sh
PATH=/system/bin:/system/xbin:$PATH
# Finds out how the phone's Scroll assistant (Titan 2 Elite: Settings > Gestures > Keyboard
# gestures > Scroll assistant) scrolls apps while no keyboard is showing, and where it keeps its
# switch and app list, so Flux Keyboard can keep swipes from the apps you block.
#
# Runs as the shell user, through Shizuku's terminal helper (no root): in the Shizuku app, "Use
# Shizuku in terminal apps" > Export files, into a folder Termux can read; then in Termux
#
#   sh /path/to/rish -c "sh find-scroll-assistant.sh"
#
# or with root: su -c sh find-scroll-assistant.sh (root also compares the settings app's own
# saved files, which the shell user can't read).
#
# It asks you to do three things in turn, pressing Enter in Termux after each.
# Everything is written to /sdcard/Download/flux-scroll-assistant.txt as well.

OUT=/sdcard/Download/flux-scroll-assistant.txt
# Somewhere both the shell user and root can write
DIR=/data/local/tmp/flux-scroll-assistant
rm -rf "$DIR"; mkdir -p "$DIR"

# Unihertz's own packages, whose saved settings are compared
PACKAGES=$(pm list packages | grep -iE "agui|unihertz" | cut -d: -f2)

snapshot() {
  for table in system secure global; do
    settings list "$table" | sed "s/^/$table: /"
  done > "$DIR/$1.settings"
  for pkg in $PACKAGES; do
    for base in /data/data/$pkg /data/user_de/0/$pkg; do
      find "$base/shared_prefs" "$base/databases" "$base/files" -type f 2>/dev/null
    done
  done | while read -r f; do
    case "$f" in
      *.xml) sed "s|^|$f: |" "$f" ;;
      *) echo "$f: $(wc -c < "$f") bytes, $(md5sum "$f" | cut -d' ' -f1)" ;;
    esac
  done > "$DIR/$1.files"
}

# What differs between two snapshots ($2 and $3) of one kind ($1): lines gone, lines new
changed() {
  echo "-- before:"
  grep -vxFf "$DIR/$3.$1" "$DIR/$2.$1" | head -40
  echo "-- after:"
  grep -vxFf "$DIR/$2.$1" "$DIR/$3.$1" | head -40
}

pause() {
  echo
  echo "$1"
  echo "Then come back here and press Enter."
  read -r _
}

countdown() {
  i=$1
  while [ "$i" -gt 0 ]; do printf "\r%2d " "$i"; sleep 1; i=$((i - 1)); done
  echo
}

echo "Reading the current settings..."
snapshot start

pause "1. Open Scroll assistant and untick one app in its list (say which, when you paste the result)."
snapshot app_off

pause "2. In Scroll assistant, turn the main switch off, wait a second, then turn it back on. Tick the app again too."
snapshot switch

echo
echo "3. Open an app that Scroll assistant scrolls, with no keyboard showing (a feed or a long page)."
echo "   Recording starts in 10 seconds and lasts 8 seconds: swipe up and down on the keys the whole time."
echo "   Press Enter to start the countdown."
read -r _
countdown 10
logcat -c 2>/dev/null
timeout 8 getevent -lt > "$DIR/getevent.txt" 2>&1
dumpsys input > "$DIR/input.txt" 2>&1
logcat -d -v brief 2>/dev/null | grep -iE "agui|touchpad|scroll|inject" | tail -60 > "$DIR/logcat.txt"
FRONT=$(dumpsys activity activities | grep -E "topResumedActivity|mResumedActivity" | head -1)
echo "Recorded."

{
  echo "== Device"
  getprop ro.product.model; getprop ro.build.fingerprint
  echo "Unihertz packages: $PACKAGES"

  echo; echo "== 1. One app unticked: settings that changed"
  changed settings start app_off
  echo; echo "== 1. One app unticked: saved files that changed"
  changed files start app_off

  echo; echo "== 2. Main switch off and on: settings that changed"
  changed settings app_off switch
  echo; echo "== 2. Main switch off and on: saved files that changed"
  changed files app_off switch

  echo; echo "== 3. Swiping with no keyboard showing"
  echo "In front: $FRONT"
  echo "-- Input devices"
  grep -E "^ +[0-9]+: |Sources:|Identifier|Is external|Classes:" "$DIR/input.txt" | head -80
  echo "-- Devices that sent events while swiping"
  grep -oE "/dev/input/event[0-9]+" "$DIR/getevent.txt" | sort | uniq -c
  echo "-- First events"
  grep -vE "^add device|^  name:|^could not" "$DIR/getevent.txt" | head -40
  echo "-- Events Android dispatched last (source and device of each)"
  sed -n '/RecentQueue/,/PendingEvent\|Connections:/p' "$DIR/input.txt" | head -40
  echo "-- Vendor log lines"
  cat "$DIR/logcat.txt"
} 2>&1 | tee "$OUT"

rm -rf "$DIR"
echo
echo "Saved to $OUT - paste it back into the chat."
