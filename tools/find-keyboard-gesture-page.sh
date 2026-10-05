#!/system/bin/sh
PATH=/system/bin:/system/xbin:$PATH
# Needs root: run it with su (plain Termux can't see other apps' screens).
# Finds exactly which screen is the phone's "Keyboard gesture" page, so Flux Keyboard can open it
# directly on phones without root. Run it as root from Termux:
#
#   su -c sh find-keyboard-gesture-page.sh
#
# then switch to Settings and open the page before the countdown ends: Keyboard gesture, or
# Scroll assistant (Settings > Gestures > Keyboard gestures > Scroll assistant), or its
# Swipe mode page. Give the page's name as the second argument to label the output:
#
#   su -c "sh find-keyboard-gesture-page.sh 15 'Scroll assistant'"
# Everything is written to /sdcard/Download/flux-settings-page.txt as well.

OUT=/sdcard/Download/flux-settings-page.txt
WAIT=${1:-15}
PAGE=${2:-Keyboard gesture}

echo "Open Settings > Gestures > $PAGE now (you have $WAIT seconds)..."
i=$WAIT
while [ "$i" -gt 0 ]; do printf "\r%2d " "$i"; sleep 1; i=$((i - 1)); done
echo

{
  echo "== Page: $PAGE"
  echo "== Device"
  getprop ro.product.model; getprop ro.build.fingerprint

  echo; echo "== Screen in front"
  dumpsys activity activities | grep -E "topResumedActivity|mResumedActivity|ResumedActivity:" | head -5

  TOP=$(dumpsys activity activities | grep -E "topResumedActivity|mResumedActivity" | head -1 | grep -oE "[a-zA-Z0-9_.]+/[a-zA-Z0-9_.$]+" | head -1)
  PKG=${TOP%%/*}
  CLS=${TOP#*/}
  case "$CLS" in .*) CLS="$PKG$CLS" ;; esac
  echo "component: $PKG/$CLS"

  echo; echo "== How it was opened (intent, extras)"
  dumpsys activity activities | grep -E "Intent \{|intent=|extras|:settings:show_fragment|fragment" | grep -iE "$PKG" | head -10
  dumpsys activity top | grep -iE "^ *ACTIVITY|Intent \{|show_fragment|mFragmentId|Fragment\{|#[0-9]+: [A-Za-z]" | head -40

  echo; echo "== Is it exported? (other apps may only open exported screens)"
  dumpsys package "$PKG" | grep -A4 -F "$CLS" | grep -iE "exported|permission|Action:|Category:" | head -10

  echo; echo "== Other screens in $PKG named like it"
  dumpsys package "$PKG" | grep -iE "(gesture|scroll|cursor|touchpad|keyboard)[A-Za-z]*(Activity|Settings)" | sort -u | head -30

  echo; echo "== Unihertz packages"
  pm list packages | grep -iE "unihertz|gesture|touch" | head -20
} 2>&1 | tee "$OUT"

echo
echo "Saved to $OUT - paste it back into the chat."
