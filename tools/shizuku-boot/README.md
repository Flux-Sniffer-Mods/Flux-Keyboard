# Start Shizuku at boot with Termux:Boot

Shizuku's own Start on boot turns on wireless debugging the moment the phone starts, and gives up
if Wi-Fi isn't connected yet. Flux Keyboard's boot script
([app/src/main/assets/fork/termux/start-shizuku.sh](../../app/src/main/assets/fork/termux/start-shizuku.sh))
waits for Wi-Fi, then starts Shizuku over wireless debugging, the same way as starting it from a
computer. No root needed. It finds what it needs by itself: adb, wireless debugging's address,
and Shizuku's starter inside its installed app.

## Setting it up

1. Install [Termux:Boot](https://f-droid.org/packages/com.termux.boot/) from the same source as
   your Termux, and open it once.
2. Start Shizuku, then in Flux Keyboard tap Set up Termux (Settings > Apps > Terminal mode, or the
   tutorial). That copies the setup command and lets Termux switch wireless debugging on and off.
3. Paste the command into Termux, answer `y` to starting Shizuku after a restart, and follow the
   pairing steps it shows (Wireless debugging > Pair device with pairing code, once).

After a restart, Shizuku starts once Wi-Fi connects. `~/.termux/boot/start-shizuku.log` says how
it went.
