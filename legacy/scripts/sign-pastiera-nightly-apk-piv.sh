#!/usr/bin/env bash

set -euo pipefail
set +x
umask 077

usage() {
  printf 'Usage: %s UNSIGNED_APK [SIGNED_APK] [--yes]\n' "$0" >&2
  exit 2
}

fail() {
  printf 'ERROR: %s\n' "$1" >&2
  exit 1
}

[[ $# -ge 1 && $# -le 3 ]] || usage
[[ -t 0 && -t 1 ]] || fail "run this script interactively in your own Terminal"
input_apk=$1
assume_yes=false
if [[ ${3:-} == "--yes" ]]; then
  assume_yes=true
elif [[ $# -eq 3 ]]; then
  usage
fi
if [[ ${2:-} == "--yes" ]]; then
  assume_yes=true
  output_apk=
elif [[ $# -ge 2 ]]; then
  output_apk=$2
elif [[ "$input_apk" == *-unsigned.apk ]]; then
  output_apk=${input_apk%-unsigned.apk}-nightly-a-signed.apk
else
  output_apk=${input_apk%.apk}-nightly-a-signed.apk
fi
if [[ -z "$output_apk" ]]; then
  output_apk=${input_apk%-unsigned.apk}-nightly-a-signed.apk
fi

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
sdk_root=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}
apksigner="$sdk_root/build-tools/36.0.0/apksigner"
apkanalyzer="$sdk_root/cmdline-tools/latest/bin/apkanalyzer"
lineage="$root_dir/signing/lineages/pastiera-nightly-v1.lineage"
provider_library=/opt/homebrew/opt/yubico-piv-tool/lib/libykcs11.dylib
ceremony_dir="$root_dir/.local-work/signing/ceremony-2026-09-05"
expected_serial_file="$ceremony_dir/token-a/serial.private.txt"
expected_certificate="$ceremony_dir/token-a/nightly-82-imported-final-certificate.private.pem"
expected_certificate_sha256=9866536b5a6da5152b0ba30b12b3cffae54990c5200647f352f40edb0f04f51e
expected_lineage_sha256=d6a425ecb433c2104cf2109f9992f2282909f56e88146f108a875a5a64d1b1e9
key_alias='X.509 Certificate for Retired Key 1'

[[ -f "$input_apk" ]] || fail "unsigned APK does not exist: $input_apk"
[[ "$input_apk" != "$output_apk" ]] || fail "input and output APK paths must differ"
[[ ! -e "$output_apk" ]] || fail "output already exists: $output_apk"
[[ -d "$(dirname -- "$output_apk")" ]] || fail "output directory does not exist: $(dirname -- "$output_apk")"
[[ -x "$apksigner" && -x "$apkanalyzer" ]] || fail "required Android SDK tools are unavailable"
[[ -f "$lineage" && -f "$expected_serial_file" && -f "$expected_certificate" ]] || fail "signing ceremony files are incomplete"
[[ -f "$provider_library" ]] || fail "YubiKey PKCS#11 library is unavailable"
command -v ykman >/dev/null 2>&1 || fail "ykman is unavailable"
command -v pkcs11-tool >/dev/null 2>&1 || fail "pkcs11-tool is unavailable"

if "$apksigner" verify "$input_apk" >/dev/null 2>&1; then
  fail "input APK is already signed"
fi
actual_app_id=$("$apkanalyzer" manifest application-id "$input_apk")
actual_version_code=$("$apkanalyzer" manifest version-code "$input_apk")
actual_min_sdk=$("$apkanalyzer" manifest min-sdk "$input_apk")
actual_debuggable=$("$apkanalyzer" manifest debuggable "$input_apk")
[[ "$actual_app_id" == "it.palsoftware.pastiera.nightly" ]] || fail "unexpected application ID: $actual_app_id"
[[ "$actual_min_sdk" == "29" ]] || fail "unexpected minimum SDK: $actual_min_sdk"
[[ "$actual_debuggable" == "false" ]] || fail "input APK is debuggable"

actual_lineage_sha256=$(shasum -a 256 "$lineage" | awk '{print $1}')
[[ "$actual_lineage_sha256" == "$expected_lineage_sha256" ]] || fail "Pastiera Nightly lineage hash does not match"
actual_certificate_sha256=$(openssl x509 -in "$expected_certificate" -outform DER | shasum -a 256 | awk '{print $1}')
[[ "$actual_certificate_sha256" == "$expected_certificate_sha256" ]] || fail "Nightly A certificate hash does not match"

serials=$(ykman list --serials)
serial_count=$(printf '%s\n' "$serials" | awk 'NF { count++ } END { print count+0 }')
[[ "$serial_count" -eq 1 ]] || fail "connect exactly one YubiKey (found $serial_count)"
current_serial=$(printf '%s\n' "$serials" | awk 'NF { print; exit }')
expected_serial=$(tr -d '\r\n' <"$expected_serial_file")
[[ "$current_serial" == "$expected_serial" ]] || fail "connected YubiKey is not YK1"

slots=$(pkcs11-tool --module "$provider_library" --list-slots 2>&1)
slot_index=$(printf '%s\n' "$slots" | awk -v expected="$expected_serial" '
  /^Slot [0-9]+ / { slot_no = $2 }
  /token label[[:space:]]*:/ && slot_no != "" && index($0, expected) { print slot_no; exit }
')
[[ "$slot_index" =~ ^[0-9]+$ ]] || fail "could not resolve the YK1 PKCS#11 slot"

temp_dir=$(mktemp -d "${TMPDIR:-/tmp}/pastiera-nightly-piv-signing.XXXXXX")
trap 'rm -rf -- "$temp_dir"' EXIT
provider_config="$temp_dir/sunpkcs11-yubikey.cfg"
temporary_output="$temp_dir/pastiera-nightly-a-signed.apk"
printf 'name = PastieraNightlySigning\nlibrary = %s\nslotListIndex = %s\n' \
  "$provider_library" "$slot_index" >"$provider_config"

printf 'Ready to sign Pastiera Nightly with YK1 Nightly A, slot 82.\n'
printf 'application_id=%s\n' "$actual_app_id"
printf 'version_code=%s\n' "$actual_version_code"
printf 'The PIN is read by apksigner and is not stored by this script.\n'
printf 'Touch YK1 when it flashes.\n'
if [[ "$assume_yes" == false ]]; then
  read -r -p 'Sign the APK now? [y/N] ' confirmation
  [[ "$confirmation" == "y" || "$confirmation" == "Y" ]] || fail "signing cancelled"
fi

"$apksigner" -J-add-exports=jdk.crypto.cryptoki/sun.security.pkcs11=ALL-UNNAMED sign \
  --in "$input_apk" \
  --out "$temporary_output" \
  --provider-class sun.security.pkcs11.SunPKCS11 \
  --provider-arg "$provider_config" \
  --ks NONE \
  --ks-type PKCS11 \
  --ks-key-alias "$key_alias" \
  --lineage "$lineage" \
  --rotation-min-sdk-version 28 \
  --v1-signing-enabled false \
  --v2-signing-enabled false \
  --v3-signing-enabled true \
  --v4-signing-enabled false

"$root_dir/scripts/verify-pastiera-nightly-rotation-apk.sh" "$temporary_output" "$actual_version_code"
mv -- "$temporary_output" "$output_apk"
printf 'Success: PIV-signed Pastiera Nightly A APK created and verified.\n'
printf 'apk=%s\n' "$output_apk"
