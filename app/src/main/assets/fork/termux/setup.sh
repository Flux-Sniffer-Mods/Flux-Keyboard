# Flux Keyboard: sets Termux up for the keyboard. Pasting it again changes nothing that's set.
f=~/.termux/termux.properties
mkdir -p ~/.termux; touch "$f"
# Other apps may run commands (the quick launcher's Termux tasks); no extra-keys row with real keys
for setting in "allow-external-apps = true" "extra-keys = []"; do
  key=${setting%% =*}
  sed -i "/^[#[:space:]]*$key[[:space:]]*=/d" "$f"
  echo "$setting" >> "$f"
done
termux-reload-settings
echo "Termux is set up for Flux Keyboard."
