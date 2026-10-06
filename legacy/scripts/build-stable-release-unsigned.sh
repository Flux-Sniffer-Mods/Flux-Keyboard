#!/usr/bin/env bash

set -euo pipefail

usage() {
  printf 'Usage: %s <version-name> <version-code> [successor-github-repository]\n' "$0" >&2
  exit 2
}

fail() {
  printf 'ERROR: %s\n' "$1" >&2
  exit 1
}

[[ $# -ge 2 && $# -le 3 ]] || usage

version_name=$1
version_code=$2
successor_repository=${3:-pkb-rocks/plektra}
[[ "$version_code" =~ ^[1-9][0-9]*$ ]] || fail "version-code must be a positive integer"
[[ "$successor_repository" =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ ]] || \
  fail "successor-github-repository must have the form owner/repository"

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
apk="$root_dir/app/build/outputs/apk/stable/release/app-stable-release-unsigned.apk"
sha_file="$apk.sha256"
sdk_root=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}
apksigner="$sdk_root/build-tools/36.0.0/apksigner"
apkanalyzer="$sdk_root/cmdline-tools/latest/bin/apkanalyzer"

[[ -x "$apksigner" ]] || fail "Android Build Tools 36.0.0 apksigner is unavailable"
[[ -x "$apkanalyzer" ]] || fail "Android SDK apkanalyzer is unavailable"

cd "$root_dir"

./gradlew :app:testStableDebugUnitTest \
  -PPASTIERA_VERSION_CODE="$version_code" \
  -PPASTIERA_VERSION_NAME="$version_name" \
  -PPASTIERA_SUCCESSOR_GITHUB_REPOSITORY="$successor_repository"

./gradlew :app:assembleStableRelease \
  -PPASTIERA_UNSIGNED_RELEASE_BUILD=true \
  -PPASTIERA_VERSION_CODE="$version_code" \
  -PPASTIERA_VERSION_NAME="$version_name" \
  -PPASTIERA_SUCCESSOR_GITHUB_REPOSITORY="$successor_repository"

[[ -f "$apk" ]] || fail "Gradle did not create the expected unsigned APK"

if "$apksigner" verify "$apk" >/dev/null 2>&1; then
  fail "Gradle output is already signed"
fi

actual_app_id=$("$apkanalyzer" manifest application-id "$apk")
actual_version_code=$("$apkanalyzer" manifest version-code "$apk")
actual_version_name=$("$apkanalyzer" manifest version-name "$apk")
actual_debuggable=$("$apkanalyzer" manifest debuggable "$apk")

[[ "$actual_app_id" == "it.palsoftware.pastiera" ]] || fail "unexpected application ID: $actual_app_id"
[[ "$actual_version_code" == "$version_code" ]] || fail "unexpected version code: $actual_version_code"
[[ "$actual_version_name" == "$version_name" ]] || fail "unexpected version name: $actual_version_name"
[[ "$actual_debuggable" == "false" ]] || fail "release APK is debuggable"

shasum -a 256 "$apk" >"$sha_file"

printf 'Success: unsigned Pastiera stable release built and verified.\n'
printf 'application_id=%s\n' "$actual_app_id"
printf 'version_name=%s\n' "$actual_version_name"
printf 'version_code=%s\n' "$actual_version_code"
printf 'successor_repository=%s\n' "$successor_repository"
printf 'apk=%s\n' "$apk"
printf 'sha256=%s\n' "$sha_file"
