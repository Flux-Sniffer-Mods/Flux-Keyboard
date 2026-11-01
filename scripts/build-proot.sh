#!/bin/sh
# Builds PRoot (third_party/proot) with talloc (third_party/talloc) for arm64 Android, into the
# app's native libraries: libproot.so and libproot-loader.so. Needs the Android NDK and GNU awk.
#   NDK=/path/to/ndk scripts/build-proot.sh
set -eu
here=$(cd "$(dirname "$0")/.." && pwd)
ndk=${NDK:-${ANDROID_NDK_HOME:-}}
[ -n "$ndk" ] || { echo "Set NDK to the Android NDK" >&2; exit 1; }
bin="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin"
cc="$bin/aarch64-linux-android24-clang"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
# The loader's symbol table is read with awk's strtonum: GNU awk
mkdir -p "$work/bin"
ln -s "$(command -v gawk)" "$work/bin/awk"
"$cc" -O2 -c "$here/third_party/talloc/talloc.c" -I"$here/third_party/talloc" \
    -DTALLOC_BUILD_VERSION_MAJOR=2 -DTALLOC_BUILD_VERSION_MINOR=4 -DTALLOC_BUILD_VERSION_RELEASE=2 -o "$work/talloc.o"
"$bin/llvm-ar" rcs "$work/libtalloc.a" "$work/talloc.o"
cp -r "$here/third_party/proot/src" "$work/src"
cd "$work/src"
PATH="$work/bin:$PATH" CPPFLAGS="-I$here/third_party/talloc" LDFLAGS="-L$work" make -s \
    CC="$cc" LD="$cc" STRIP="$bin/llvm-strip" OBJCOPY="$bin/llvm-objcopy" OBJDUMP="$bin/llvm-objdump" \
    PROOT_UNBUNDLE_LOADER=/unused proot loader/loader
out="$here/app/src/main/jniLibs/arm64-v8a"
mkdir -p "$out"
"$bin/llvm-strip" -o "$out/libproot.so" proot
"$bin/llvm-strip" -o "$out/libproot-loader.so" loader/loader
echo "Built $out/libproot.so and $out/libproot-loader.so"
