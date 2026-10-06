#!/usr/bin/env bash

set -euo pipefail

usage() {
  printf 'Usage: %s APK [expected-version-code]\n' "$0" >&2
  exit 2
}

fail() {
  printf 'ERROR: %s\n' "$1" >&2
  exit 1
}

[[ $# -ge 1 && $# -le 2 ]] || usage

apk=$1
expected_version_code=${2:-}
root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
sdk_root=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}
apksigner="$sdk_root/build-tools/36.0.0/apksigner"
apkanalyzer="$sdk_root/cmdline-tools/latest/bin/apkanalyzer"
zipalign="$sdk_root/build-tools/36.0.0/zipalign"
apksigner_jar="$sdk_root/build-tools/36.0.0/lib/apksigner.jar"
java_source="$root_dir/scripts/VerifyApkSigningLineage.java"
legacy_sha256=d5c018b9c33e0cda7cef0b006ed739d4c6304c4ef4a0c4d9454a5302e7c4c3e7
stable_a_sha256=b28ac03130088bb27a356e1dd704b35f2701268474a386c348d07ed92f3ef71a

[[ -f "$apk" ]] || fail "APK does not exist: $apk"
[[ -x "$apksigner" && -x "$apkanalyzer" && -x "$zipalign" ]] || fail "required Android SDK tools are unavailable"
[[ -f "$apksigner_jar" && -f "$java_source" ]] || fail "APK verifier toolchain is incomplete"

"$apksigner" verify --verbose --print-certs "$apk"
"$zipalign" -c -P 16 4 "$apk" >/dev/null

actual_app_id=$("$apkanalyzer" manifest application-id "$apk")
actual_version_code=$("$apkanalyzer" manifest version-code "$apk")
actual_version_name=$("$apkanalyzer" manifest version-name "$apk")
actual_min_sdk=$("$apkanalyzer" manifest min-sdk "$apk")
actual_debuggable=$("$apkanalyzer" manifest debuggable "$apk")

[[ "$actual_app_id" == "it.palsoftware.pastiera" ]] || fail "unexpected application ID: $actual_app_id"
[[ "$actual_min_sdk" == "29" ]] || fail "unexpected minimum SDK: $actual_min_sdk"
[[ "$actual_debuggable" == "false" ]] || fail "release APK is debuggable"
if [[ -n "$expected_version_code" ]]; then
  [[ "$actual_version_code" == "$expected_version_code" ]] || fail "unexpected version code: $actual_version_code"
fi

classes_dir=$(mktemp -d "${TMPDIR:-/tmp}/pastiera-apk-verifier.XXXXXX")
trap 'rm -rf -- "$classes_dir"' EXIT
javac -cp "$apksigner_jar" -d "$classes_dir" "$java_source"
java -cp "$classes_dir:$apksigner_jar" VerifyApkSigningLineage \
  "$apk" "$stable_a_sha256" "$legacy_sha256" "$stable_a_sha256"

printf 'Success: Pastiera Stable A rotation APK verified.\n'
printf 'application_id=%s\n' "$actual_app_id"
printf 'version_name=%s\n' "$actual_version_name"
printf 'version_code=%s\n' "$actual_version_code"
printf 'apk_sha256=%s\n' "$(shasum -a 256 "$apk" | awk '{print $1}')"
