# PRoot for the Linux desktop

termux/proot at `fa47780ce5576eb22d2f779d700a44640d957529` (PRoot with Termux's Android patches), built
for arm64 against Android's own libraries by `scripts/build-proot.sh`, with talloc 2.4.2 (`../talloc`)
linked in. Two changes: `src/extension/ashmem_memfd/ashmem_memfd.c` includes `<string.h>`, which
newer compilers need, and a comment in `src/syscall/enter.c` is shortened.

The script puts the results in `app/src/main/jniLibs/arm64-v8a/` as `libproot.so` and
`libproot-loader.so` (Android unpacks files named that way, so they can be run).
