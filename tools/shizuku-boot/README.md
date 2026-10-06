# Start Shizuku at boot with Termux:Boot

Shizuku's own Start on boot turns on wireless debugging the moment the phone starts, and gives up
if Wi-Fi isn't connected yet. This script waits for Wi-Fi, then starts Shizuku over wireless
debugging, the same way as starting it from a computer. No root needed.

## Once

1. Install [Termux:Boot](https://f-droid.org/packages/com.termux.boot/) from F-Droid (the same
   source as your Termux) and open it once.
2. In Termux: `pkg install android-tools`
3. Pair Termux with wireless debugging. In Developer options, open Wireless debugging, turn it on
   and tap "Pair device with pairing code". With Termux beside it (split screen), run
   `adb pair <address:port> <code>` using the address, port and code it shows.
4. Let Termux turn wireless debugging on and off. While Shizuku is running, run this through
   Shizuku's `rish` (see `tools/find-scroll-assistant.sh` for setting rish up):
   `sh ~/rish -c "pm grant com.termux android.permission.WRITE_SECURE_SETTINGS"`
5. Put the script in Termux:Boot's folder:
   ```
   mkdir -p ~/.termux/boot
   curl -L -o ~/.termux/boot/start-shizuku.sh https://raw.githubusercontent.com/Flux-Sniffer-Mods/Flux-Keyboard/flux-dev/tools/shizuku-boot/start-shizuku.sh
   chmod +x ~/.termux/boot/start-shizuku.sh
   ```

## Every restart

Nothing: once Wi-Fi connects, Shizuku starts, and Flux Keyboard's swipes, keyboard light and
screen sizes come back. What happened is in `~/.termux/boot/start-shizuku.log`.
