# Flux Keyboard: starts Shizuku by itself after a restart, with Termux:Boot. Pasting it again changes nothing that's set.
command -v adb >/dev/null || pkg install -y android-tools
mkdir -p ~/.termux/boot
cp "$FLUX_START_SHIZUKU" ~/.termux/boot/start-shizuku.sh
chmod +x ~/.termux/boot/start-shizuku.sh
echo "The boot script is in ~/.termux/boot/."

# Termux needs to switch wireless debugging on and off at boot (Flux Keyboard grants that)
if ! settings put global adb_wifi_enabled "$(settings get global adb_wifi_enabled)" 2>/dev/null; then
  echo "Termux can't switch wireless debugging yet: start Shizuku, then copy the command from Flux Keyboard again."
fi

# Pairing, once: Termux's adb has to be allowed by the phone
if [ -f ~/.termux/boot/.flux-paired ]; then
  echo "Termux is already paired with wireless debugging."
else
  echo
  echo "Pair Termux with wireless debugging, once:"
  echo "  Settings > System > Developer options > Wireless debugging: turn it on,"
  echo "  then tap 'Pair device with pairing code' (keep it open beside Termux)."
  printf "Pairing address and port (like 192.168.1.5:37099): "; read -r pair_address
  printf "Pairing code: "; read -r pair_code
  if adb pair "$pair_address" "$pair_code"; then
    touch ~/.termux/boot/.flux-paired
    echo "Paired."
  else
    echo "Pairing didn't work: paste the command again to retry."
    exit 1
  fi
fi
echo
echo "Done. Open the Termux:Boot app once if you haven't (it won't run scripts until then)."
echo "After a restart, Shizuku starts once Wi-Fi is up: ~/.termux/boot/start-shizuku.log says how it went."
