#!/bin/sh
# Builds virglrenderer's vtest server (with libepoxy) for arm64 Android, into the app's native
# libraries as libvirgl.so: Linux's programs draw through it on the phone's own GPU (Mesa's
# virpipe driver in Linux sends it their OpenGL). The sources are downloaded at fixed versions
# and patched from third_party/virglrenderer/patches. Needs the Android NDK, meson and ninja.
#   NDK=/path/to/ndk scripts/build-virgl.sh
set -eu
here=$(cd "$(dirname "$0")/.." && pwd)
ndk=${NDK:-${ANDROID_NDK_HOME:-}}
[ -n "$ndk" ] || { echo "Set NDK to the Android NDK" >&2; exit 1; }
bin="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin"
virgl_version=1.3.0
virgl_sha256=56170f8caa1bb642a2624b649e3bcca095ec2834814e5c308efc8a85a709e4ce
epoxy_version=1.5.10
epoxy_commit=c84bc9459357a40e46e2fec0408d04fbdde2c973
work=$(mktemp -d)
[ -n "${KEEP:-}" ] || trap 'rm -rf "$work"' EXIT
cd "$work"
curl -sSLo virgl.tar.gz "https://gitlab.freedesktop.org/virgl/virglrenderer/-/archive/virglrenderer-$virgl_version/virglrenderer-virglrenderer-$virgl_version.tar.gz"
echo "$virgl_sha256  virgl.tar.gz" | sha256sum -c -
tar xzf virgl.tar.gz
mv "virglrenderer-virglrenderer-$virgl_version" virgl
git clone -q --depth 1 -b "$epoxy_version" https://github.com/anholt/libepoxy epoxy
[ "$(git -C epoxy rev-parse HEAD)" = "$epoxy_commit" ] || { echo "libepoxy $epoxy_version isn't the commit expected" >&2; exit 1; }
for p in "$here"/third_party/virglrenderer/patches/*.patch; do
    patch -s -p1 -d virgl < "$p"
done
cat > cross.txt <<EOF
[binaries]
c = '$bin/aarch64-linux-android24-clang'
cpp = '$bin/aarch64-linux-android24-clang++'
ar = '$bin/llvm-ar'
strip = '$bin/llvm-strip'
pkg-config = 'pkg-config'

[host_machine]
system = 'android'
cpu_family = 'aarch64'
cpu = 'armv8-a'
endian = 'little'
EOF
prefix="$work/prefix"
export PKG_CONFIG_LIBDIR="$prefix/lib/pkgconfig" PKG_CONFIG_PATH=
# libepoxy built in, so the server is one file; it opens Android's libEGL and libGLESv2 itself
meson setup epoxy-build epoxy --cross-file cross.txt --prefix="$prefix" --libdir lib \
    --default-library=static -Degl=yes -Dglx=no -Dx11=false -Dtests=false >/dev/null
ninja -C epoxy-build install > build.log || { tail -n 40 build.log >&2; exit 1; }
meson setup virgl-build virgl --cross-file cross.txt --prefix="$prefix" --libdir lib \
    --default-library=static -Dplatforms=egl -Dvenus=false -Dtests=false -Dcheck-gl-errors=false >/dev/null
ninja -C virgl-build vtest/virgl_test_server > build.log || { tail -n 40 build.log >&2; exit 1; }
out="$here/app/src/main/jniLibs/arm64-v8a"
mkdir -p "$out"
"$bin/llvm-strip" -o "$out/libvirgl.so" virgl-build/vtest/virgl_test_server
echo "Built $out/libvirgl.so"
