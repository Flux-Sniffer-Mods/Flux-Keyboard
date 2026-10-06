#!/usr/bin/env bash

set -euo pipefail

usage() {
  printf 'Usage: %s <base-version> <output-apk> [--timestamp YYYYMMDD.HHMMSS] [--fdroid]\n' "$0" >&2
  exit 2
}

fail() {
  printf 'ERROR: %s\n' "$1" >&2
  exit 1
}

[[ $# -ge 2 ]] || usage
base_version=$1
output_apk=$2
shift 2

timestamp=
fdroid=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --timestamp)
      [[ $# -ge 2 ]] || usage
      timestamp=$2
      shift 2
      ;;
    --fdroid)
      fdroid=true
      shift
      ;;
    *) usage ;;
  esac
done

if [[ -z "$timestamp" ]]; then
  timestamp=$(TZ=Europe/Brussels date +'%Y%m%d.%H%M%S')
fi
[[ "$timestamp" =~ ^[0-9]{8}\.[0-9]{6}$ ]] || fail "invalid timestamp: $timestamp"

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
sdk_root=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}
apksigner="$sdk_root/build-tools/36.0.0/apksigner"
apkanalyzer="$sdk_root/cmdline-tools/latest/bin/apkanalyzer"
gradle_apk="$root_dir/app/build/outputs/apk/nightly/release/app-nightly-release-unsigned.apk"

[[ -x "$apksigner" && -x "$apkanalyzer" ]] || fail "required Android SDK tools are unavailable"
mkdir -p "$(dirname -- "$output_apk")"
[[ ! -e "$output_apk" ]] || fail "output already exists: $output_apk"

version_info=$(PASTIERA_NIGHTLY_TIMESTAMP="$timestamp" "$root_dir/scripts/nightly-version.sh" "$base_version")
version_code=$(printf '%s\n' "$version_info" | awk -F= '/^version_code=/{print $2}')
full_version=$(printf '%s\n' "$version_info" | awk -F= '/^full_version=/{print $2}')
gradle_args=(
  -PPASTIERA_UNSIGNED_RELEASE_BUILD=true
  -PPASTIERA_VERSION_NAME="$base_version"
  -PPASTIERA_NIGHTLY_VERSION_CODE="$version_code"
  -PPASTIERA_NIGHTLY_VERSION_SUFFIX="-nightly.${timestamp}"
)
if [[ "$fdroid" == true ]]; then
  gradle_args+=(-PPASTIERA_FDROID_BUILD=true)
fi

cd "$root_dir"
./gradlew :app:testNightlyReleaseUnitTest "${gradle_args[@]}"
./gradlew :app:assembleNightlyRelease "${gradle_args[@]}"

[[ -f "$gradle_apk" ]] || fail "Gradle did not create the expected unsigned APK: $gradle_apk"
if "$apksigner" verify "$gradle_apk" >/dev/null 2>&1; then
  fail "Gradle output is already signed"
fi

actual_app_id=$("$apkanalyzer" manifest application-id "$gradle_apk")
actual_version_code=$("$apkanalyzer" manifest version-code "$gradle_apk")
actual_version_name=$("$apkanalyzer" manifest version-name "$gradle_apk")
actual_debuggable=$("$apkanalyzer" manifest debuggable "$gradle_apk")
[[ "$actual_app_id" == "it.palsoftware.pastiera.nightly" ]] || fail "unexpected application ID: $actual_app_id"
[[ "$actual_version_code" == "$version_code" ]] || fail "unexpected version code: $actual_version_code"
[[ "$actual_version_name" == "$full_version" ]] || fail "unexpected version name: $actual_version_name"
[[ "$actual_debuggable" == "false" ]] || fail "release APK is debuggable"

cp -- "$gradle_apk" "$output_apk"
shasum -a 256 "$output_apk" >"${output_apk}.sha256"

printf 'Success: unsigned Pastiera Nightly release built and verified.\n'
printf 'delivery=%s\n' "$([[ "$fdroid" == true ]] && printf fdroid || printf github)"
printf 'application_id=%s\n' "$actual_app_id"
printf 'version_name=%s\n' "$actual_version_name"
printf 'version_code=%s\n' "$actual_version_code"
printf 'apk=%s\n' "$output_apk"
printf 'sha256=%s\n' "${output_apk}.sha256"
