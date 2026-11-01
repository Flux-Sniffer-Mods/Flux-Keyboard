# virglrenderer for the Linux desktop's GPU

virglrenderer 1.3.0's vtest server, with libepoxy 1.5.10 linked in, built for arm64 Android by
`scripts/build-virgl.sh`, which downloads both at those versions (checked by hash and commit) and
applies `patches/`. Linux's programs draw through it on the phone's GPU: Mesa's virpipe driver in
Linux sends their OpenGL over a socket, and the server draws it with Android's own OpenGL ES.

The patches numbered 00xx come from Termux's virglrenderer-android package
(termux/termux-packages at `278a573d82e9b762b1ccdc8d99bcf9589f1236a8`): building for Android
without GBM, ashmem where memfd isn't there, no dual-source blending, S3TC textures decoded
(gl4es's code, `COPYING-gl4es`), and Android 7's libc. `0101-use-android-egl.patch` is ours: Android's
own EGL rather than ANGLE.

The script puts the result in `app/src/main/jniLibs/arm64-v8a/` as `libvirgl.so` (Android unpacks
files named that way, so it can be run).
