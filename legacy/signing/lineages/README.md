# Android signing certificate lineages

Ceremony: 2026-09-05. These replace the 2026-09-01 YubiKey lineages.

Pastiera: Legacy → A → B → C. Plektra: A → B → C.

Capabilities for every node: installed-data=true, permission=true, shared-uid=false, rollback=false, auth=false.

## Stable

```text
Legacy d5c018b9c33e0cda7cef0b006ed739d4c6304c4ef4a0c4d9454a5302e7c4c3e7
A      b28ac03130088bb27a356e1dd704b35f2701268474a386c348d07ed92f3ef71a
B      0af4f6b9f964886eef11400926a388904388b1f6608d88eeba90d539f74d9099
C      c9207e47369e421ae69d0e64ba1ba3e67e9a739d94e444a4ba83c1085cc09a56
```

## Nightly

```text
Legacy 8c5dce860a65a7a3c3befcb7f7f35a1f3523c1d01462271d6ae03f4df402e685
A      9866536b5a6da5152b0ba30b12b3cffae54990c5200647f352f40edb0f04f51e
B      fe1d6f5c377843ef8875d598169c71afdb2913193681428d1446b261db678213
C      6b0b93663a562cd575b2696d5905a80d4ab68420b28d06980fa703d6e572ede1
```

Verify file hashes from the signing/lineages directory with `shasum -a 256 -c SHA256SUMS`. Complete evidence is in the matching documents under docs.
