#!/usr/bin/env bash

set -euo pipefail

usage() {
  printf 'Usage: %s SIGNED_APK [adb-serial] [--yes]\n' "$0" >&2
  exit 2
}

fail() {
  printf 'ERROR: %s\n' "$1" >&2
  exit 1
}

[[ $# -ge 1 && $# -le 3 ]] || usage

apk=$1
requested_serial=${2:-}
assume_yes=false
if [[ "$requested_serial" == "--yes" ]]; then
  assume_yes=true
  requested_serial=
elif [[ ${3:-} == "--yes" ]]; then
  assume_yes=true
elif [[ $# -eq 3 ]]; then
  usage
fi
if [[ "$assume_yes" == false ]]; then
  [[ -t 0 && -t 1 ]] || fail "run interactively or pass --yes"
fi
root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
sdk_root=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}
apksigner="$sdk_root/build-tools/36.0.0/apksigner"
apkanalyzer="$sdk_root/cmdline-tools/latest/bin/apkanalyzer"
package=it.palsoftware.pastiera
legacy_sha256=d5c018b9c33e0cda7cef0b006ed739d4c6304c4ef4a0c4d9454a5302e7c4c3e7

[[ -f "$apk" ]] || fail "APK does not exist: $apk"
command -v adb >/dev/null 2>&1 || fail "adb is unavailable"
[[ -x "$apksigner" && -x "$apkanalyzer" ]] || fail "required Android SDK tools are unavailable"

"$root_dir/scripts/verify-pastiera-stable-rotation-apk.sh" "$apk"
candidate_version_code=$("$apkanalyzer" manifest version-code "$apk")

if [[ -n "$requested_serial" ]]; then
  serial=$requested_serial
  adb -s "$serial" get-state >/dev/null
else
  devices=$(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
  device_count=$(printf '%s\n' "$devices" | awk 'NF { count++ } END { print count+0 }')
  [[ "$device_count" -eq 1 ]] || fail "connect exactly one authorized Android device (found $device_count)"
  serial=$(printf '%s\n' "$devices" | awk 'NF { print; exit }')
fi

sdk=$(adb -s "$serial" shell getprop ro.build.version.sdk | tr -d '\r')
[[ "$sdk" =~ ^[0-9]+$ && "$sdk" -ge 29 ]] || fail "device API level is below 29"

temp_dir=$(mktemp -d "${TMPDIR:-/tmp}/pastiera-update-test.XXXXXX")
trap 'rm -rf -- "$temp_dir"' EXIT

installed_path=$(adb -s "$serial" shell pm path "$package" | sed -n 's/^package://p' | tr -d '\r' | head -n 1)
[[ -n "$installed_path" ]] || fail "Pastiera stable is not installed"
adb -s "$serial" pull "$installed_path" "$temp_dir/before.apk" >/dev/null

before_signer=$("$apksigner" verify --print-certs "$temp_dir/before.apk" 2>/dev/null \
  | awk -F': ' '/Signer #1 certificate SHA-256 digest:/ { print tolower($2); exit }')
[[ "$before_signer" == "$legacy_sha256" ]] || fail "installed Pastiera is not signed by Legacy Stable"

before_version_code=$("$apkanalyzer" manifest version-code "$temp_dir/before.apk")
[[ "$candidate_version_code" -gt "$before_version_code" ]] || fail "candidate version code is not greater than installed version code"

before_dump=$(adb -s "$serial" shell dumpsys package "$package")
before_app_id=$(printf '%s\n' "$before_dump" | awk -F= '/^[[:space:]]*(appId|userId)=/ && !found {gsub(/[[:space:]\r]/, "", $2); print $2; found=1}')
before_first_install=$(printf '%s\n' "$before_dump" | awk -F= '/^[[:space:]]*firstInstallTime=/ && !found {gsub(/\r/, "", $2); print $2; found=1}')
before_default_ime=$(adb -s "$serial" shell settings get secure default_input_method | tr -d '\r')

printf 'Ready to test the signing-key rotation on Android API %s.\n' "$sdk"
printf 'installed_version_code=%s\n' "$before_version_code"
printf 'candidate_version_code=%s\n' "$candidate_version_code"
printf 'default_ime=%s\n' "$before_default_ime"
if [[ "$assume_yes" == false ]]; then
  read -r -p 'Install the APK as an update now? [y/N] ' confirmation
  [[ "$confirmation" == "y" || "$confirmation" == "Y" ]] || fail "update test cancelled"
fi

install_output=$(adb -s "$serial" install -r "$apk")
printf '%s\n' "$install_output"
printf '%s\n' "$install_output" | rg -q '^Success$' || fail "adb did not report a successful update"

after_path=$(adb -s "$serial" shell pm path "$package" | sed -n 's/^package://p' | tr -d '\r' | head -n 1)
[[ -n "$after_path" ]] || fail "Pastiera stable is absent after the update"
adb -s "$serial" pull "$after_path" "$temp_dir/after.apk" >/dev/null
"$root_dir/scripts/verify-pastiera-stable-rotation-apk.sh" "$temp_dir/after.apk" "$candidate_version_code"

after_dump=$(adb -s "$serial" shell dumpsys package "$package")
after_app_id=$(printf '%s\n' "$after_dump" | awk -F= '/^[[:space:]]*(appId|userId)=/ && !found {gsub(/[[:space:]\r]/, "", $2); print $2; found=1}')
after_first_install=$(printf '%s\n' "$after_dump" | awk -F= '/^[[:space:]]*firstInstallTime=/ && !found {gsub(/\r/, "", $2); print $2; found=1}')
after_default_ime=$(adb -s "$serial" shell settings get secure default_input_method | tr -d '\r')

[[ -n "$before_app_id" && "$after_app_id" == "$before_app_id" ]] || fail "package app ID changed"
[[ -n "$before_first_install" && "$after_first_install" == "$before_first_install" ]] || fail "first install time changed"
[[ "$after_default_ime" == "$before_default_ime" ]] || fail "default input method changed"

printf 'Success: Android accepted the Legacy Stable to Stable A update.\n'
printf 'device_api=%s\n' "$sdk"
printf 'version_code_before=%s\n' "$before_version_code"
printf 'version_code_after=%s\n' "$candidate_version_code"
printf 'package_app_id=%s\n' "$after_app_id"
printf 'default_ime=%s\n' "$after_default_ime"
printf 'Manual check: open Pastiera and confirm that its settings and user data remain present.\n'
