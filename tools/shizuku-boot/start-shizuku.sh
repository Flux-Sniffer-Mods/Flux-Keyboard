#!/data/data/com.termux/files/usr/bin/sh
# Starts Shizuku after a restart, without root, the way you'd start it from a computer: through
# wireless debugging, once Wi-Fi is up (Shizuku's own Start on boot gives up when Wi-Fi isn't
# connected yet at boot). Runs from Termux:Boot: put it in ~/.termux/boot/. Setup: README.md.
LOG="$HOME/.termux/boot/start-shizuku.log"
exec >"$LOG" 2>&1
date

# Keep Termux awake while this runs
command -v termux-wake-lock >/dev/null && termux-wake-lock

# Wait for Wi-Fi, for up to five minutes
i=0
until ip route 2>/dev/null | grep -q wlan; do
  i=$((i + 1)); [ "$i" -gt 60 ] && { echo "No Wi-Fi: Shizuku not started"; exit 1; }
  sleep 5
done
echo "Wi-Fi is up"

# Wireless debugging on (Termux was given permission to change it, once: README.md)
settings put global adb_wifi_enabled 1
sleep 5

# Its address, as the phone announces it on the network
address=""
for _ in 1 2 3 4 5 6; do
  address=$(adb mdns services 2>/dev/null | awk '/_adb-tls-connect/ {print $NF}' | head -1)
  [ -n "$address" ] && break
  sleep 5
done
[ -z "$address" ] && { echo "Wireless debugging didn't start"; exit 1; }
echo "Wireless debugging at $address"
adb connect "$address"

# Shizuku's own starter, inside its app
apk=$(adb -s "$address" shell pm path moe.shizuku.privileged.api | head -1 | cut -d: -f2 | tr -d '\r')
starter="$(dirname "$apk")/lib/arm64/libshizuku.so"
# Already running (started some other way): left as it is
if adb -s "$address" shell pidof shizuku_server >/dev/null 2>&1; then
  echo "Shizuku is already running"
else
  adb -s "$address" shell "$starter"
  sleep 3
fi
running=$(adb -s "$address" shell pidof shizuku_server 2>/dev/null)

# Wireless debugging off again: Shizuku doesn't need it once running
adb disconnect "$address"
settings put global adb_wifi_enabled 0
[ -n "$running" ] && echo "Shizuku is running" || echo "Shizuku didn't start: see above"
