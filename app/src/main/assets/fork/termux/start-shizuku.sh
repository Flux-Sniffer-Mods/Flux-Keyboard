#!/data/data/com.termux/files/usr/bin/sh
# Flux Keyboard: starts Shizuku after a restart, without root, the way you'd start it from a
# computer: through wireless debugging, once Wi-Fi is up (Shizuku's own Start on boot gives up when
# Wi-Fi isn't connected yet at boot). Runs from Termux:Boot (~/.termux/boot/); installed and set up
# by the Termux setup command in Flux Keyboard (Settings > Apps > Terminal mode > Set up Termux).
export PATH="/data/data/com.termux/files/usr/bin:$PATH"
LOG="$HOME/.termux/boot/start-shizuku.log"
exec >"$LOG" 2>&1
date

command -v termux-wake-lock >/dev/null && termux-wake-lock
command -v adb >/dev/null || { echo "adb isn't installed: run the setup command from Flux Keyboard again"; exit 1; }

# Wait for Wi-Fi, for up to five minutes
i=0
until ip route 2>/dev/null | grep -q wlan; do
  i=$((i + 1)); [ "$i" -gt 60 ] && { echo "No Wi-Fi: Shizuku not started"; exit 1; }
  sleep 5
done
echo "Wi-Fi is up"

# Wireless debugging on (Flux Keyboard gave Termux permission to change it)
was_on=$(settings get global adb_wifi_enabled 2>/dev/null)
settings put global adb_wifi_enabled 1 || { echo "Termux can't turn wireless debugging on: tap Set up Termux in Flux Keyboard while Shizuku is running"; exit 1; }

# Its address, as the phone announces it on the network
address=""
for _ in 1 2 3 4 5 6 7 8; do
  sleep 5
  address=$(adb mdns services 2>/dev/null | awk '/_adb-tls-connect/ {print $NF}' | head -1)
  [ -n "$address" ] && break
done
[ -z "$address" ] && { echo "Wireless debugging didn't start"; exit 1; }
echo "Wireless debugging at $address"
adb connect "$address" || { echo "Couldn't connect: pair Termux again (run the setup command)"; exit 1; }

if adb -s "$address" shell pidof shizuku_server >/dev/null 2>&1; then
  echo "Shizuku is already running"
else
  # Shizuku's own starter, found inside its installed app
  apk=$(adb -s "$address" shell pm path moe.shizuku.privileged.api | head -1 | cut -d: -f2 | tr -d '\r')
  [ -z "$apk" ] && { echo "Shizuku isn't installed"; exit 1; }
  starter=$(adb -s "$address" shell "ls $(dirname "$apk")/lib/*/libshizuku.so" 2>/dev/null | head -1 | tr -d '\r')
  [ -z "$starter" ] && { echo "Shizuku's starter wasn't found"; exit 1; }
  adb -s "$address" shell "$starter"
  sleep 3
fi
running=$(adb -s "$address" shell pidof shizuku_server 2>/dev/null)

# Wireless debugging back as it was: Shizuku doesn't need it once running
adb disconnect "$address" >/dev/null
[ "$was_on" = 1 ] || settings put global adb_wifi_enabled 0
[ -n "$running" ] && echo "Shizuku is running" || echo "Shizuku didn't start: see above"
