---
title: "PKB.rocks Android App Signing Attestation"
subtitle: "Plektra and Pastiera – Stable"
author: "Patrick Zauner"
date: "2026-09-25"
documentclass: extarticle
geometry: "a4paper,margin=16mm"
fontsize: 9pt
header-includes:
  - \usepackage{fancyvrb}
  - \usepackage{qrcode}
  - \usepackage{xurl}
  - \newcommand{\certfont}{\fontsize{9pt}{9.3pt}\selectfont}
  - \newcommand{\apkfont}{\fontsize{7.8pt}{8.4pt}\selectfont}
  - \RecustomVerbatimEnvironment{Highlighting}{Verbatim}{commandchars=\\\{\},fontsize=\apkfont}
  - \setlength{\emergencystretch}{3em}
---

# Attestation

I attest that Certificates A, B, and C are the PKB.rocks stable Android app signing certificates for these application IDs:

- `rocks.pkb.plektra`
- `it.palsoftware.pastiera`

The authorized signing succession is A → B → C. A is the initial signer; releases may switch directly from A to C, skipping B. Once B is adopted for releases, A must no longer sign new releases; once C is adopted, only C may sign new releases. Earlier signatures remain valid. Introducing a new signing key after C requires an authorized extension of the signing lineage.

# Certificate identity

```text
Subject and issuer  CN=Patrick Zauner, O=PKB.rocks,
                    OU=Plektra (successor to Pastiera) - Stable Android App Signing
Public key          ECC P-256
Signature           ECDSA with SHA-256
Validity start      2026-09-05 00:00:00 UTC
Validity end        2126-09-05 00:00:00 UTC
```

## Certificate fingerprints

Each fingerprint is the SHA-256 digest of the DER-encoded X.509 certificate.

```text
Certificate  Role     SHA-256
A            Initial   b28ac03130088bb27a356e1dd704b35f2701268474a386c348d07ed92f3ef71a
B            Next     0af4f6b9f964886eef11400926a388904388b1f6608d88eeba90d539f74d9099
C            Last     c9207e47369e421ae69d0e64ba1ba3e67e9a739d94e444a4ba83c1085cc09a56
```

# Hardware attestation

```text
PIV slot       9C (Digital Signature)
Key origin     GENERATED
PIN policy     ALWAYS
Touch policy   ALWAYS
```

The Yubico PIV attestation for each signer verifies on-device key generation. Each attested public key matches its Android signing certificate. Each attestation chain verifies to Yubico Attestation Root 1 through the included intermediate certificates. Complete hardware attestations and manufacturer certificates follow on separate pages.

```text
Signer  Slot-attestation certificate (DER, SHA-256)
A       3ab6a3c29e398fca1720f42682f9e6abcaf61fcfac41cc59c67eef99f62b7030
B       d30b9cf41ae6730c6e5bbeda77ac3cb07bb0d4f65467e64f25496b0a9fb6ecfe
C       67ee9339415cccbff65354927615ac20f02c7e3d21cb3a33f08e28d3c6ae87bd

F9 attestation certificate (DER, SHA-256; A, B and C)
7ee706d267df53e5cc4505372d26e5daeadd8feaaf4b58562afde8cc392192c1

Yubico Attestation Root 1 (DER, SHA-256)
62760c6a6ef91679f454c8902b80fd009825b3f25da90f1fbace2ec6586cd5a8
```

# Android signing continuity

```text
rocks.pkb.plektra
Stable A -> Stable B -> Stable C
signing/lineages/plektra-stable-v1.lineage
SHA-256 12e9535b2897d10a61e5715555876397628d4d4f2acb51ec73005c7d7db22b5b

it.palsoftware.pastiera
Legacy Stable -> Stable A -> Stable B -> Stable C
signing/lineages/pastiera-stable-v1.lineage
SHA-256 d29e813e9fc79bbd9db82ad457db1b6fadb37cb497b1676b0aff2c8db94fa12c
```

Capabilities: `installed-data=true`, `shared-uid=false`, `permission=true`, `rollback=false`, `auth=false`.

\clearpage

# Public certificates

\vspace{0.75\baselineskip}

```{=latex}
\noindent
\begin{minipage}[t]{2.1in}
\centering
\subsection*{Certificate A}
\qrcode[height=2.1in]{-----BEGIN CERTIFICATE-----MIICTTCCAfOgAwIBAgIVAJ3MCcBM4H6iPTkxPkgZa/ItJas5MAoGCCqGSM49BAMCMHQxFzAVBgNVBAMMDlBhdHJpY2sgWmF1bmVyMRIwEAYDVQQKDAlQS0Iucm9ja3MxRTBDBgNVBAsMPFBsZWt0cmEgKHN1Y2Nlc3NvciB0byBQYXN0aWVyYSkgLSBTdGFibGUgQW5kcm9pZCBBcHAgU2lnbmluZzAgFw0yNjA5MDUwMDAwMDBaGA8yMTI2MDkwNTAwMDAwMFowdDEXMBUGA1UEAwwOUGF0cmljayBaYXVuZXIxEjAQBgNVBAoMCVBLQi5yb2NrczFFMEMGA1UECww8UGxla3RyYSAoc3VjY2Vzc29yIHRvIFBhc3RpZXJhKSAtIFN0YWJsZSBBbmRyb2lkIEFwcCBTaWduaW5nMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEWCNPXSPKLWnPb2J3kIPIIe2oEAiyllEEGDDFA1BGlaHOgglq7Oao2jITzahrCgPjYEvlflVjmHo9chKex9fp16NgMF4wHwYDVR0jBBgwFoAUmsgr48Xcl1msD5O5orQBX5iTXh8wDAYDVR0TAQH/BAIwADAOBgNVHQ8BAf8EBAMCB4AwHQYDVR0OBBYEFJrIK+PF3JdZrA+TuaK0AV+Yk14fMAoGCCqGSM49BAMCA0gAMEUCIQD92wZ+eRnC1N8WrjcPuSgWEn+RA94W0jglPAAJnFr/oQIgNyY/is5So8TKktaIqPvTfb4gESUKI1+m6/lZ9eyQkwU=-----END CERTIFICATE-----}\par\vspace{\baselineskip}
\raggedright
\begin{Verbatim}[fontsize=\certfont]
-----BEGIN CERTIFICATE-----
MIICTTCCAfOgAwIBAgIVAJ3MCcBM4H6i
PTkxPkgZa/ItJas5MAoGCCqGSM49BAMC
MHQxFzAVBgNVBAMMDlBhdHJpY2sgWmF1
bmVyMRIwEAYDVQQKDAlQS0Iucm9ja3Mx
RTBDBgNVBAsMPFBsZWt0cmEgKHN1Y2Nl
c3NvciB0byBQYXN0aWVyYSkgLSBTdGFi
bGUgQW5kcm9pZCBBcHAgU2lnbmluZzAg
Fw0yNjA5MDUwMDAwMDBaGA8yMTI2MDkw
NTAwMDAwMFowdDEXMBUGA1UEAwwOUGF0
cmljayBaYXVuZXIxEjAQBgNVBAoMCVBL
Qi5yb2NrczFFMEMGA1UECww8UGxla3Ry
YSAoc3VjY2Vzc29yIHRvIFBhc3RpZXJh
KSAtIFN0YWJsZSBBbmRyb2lkIEFwcCBT
aWduaW5nMFkwEwYHKoZIzj0CAQYIKoZI
zj0DAQcDQgAEWCNPXSPKLWnPb2J3kIPI
Ie2oEAiyllEEGDDFA1BGlaHOgglq7Oao
2jITzahrCgPjYEvlflVjmHo9chKex9fp
16NgMF4wHwYDVR0jBBgwFoAUmsgr48Xc
l1msD5O5orQBX5iTXh8wDAYDVR0TAQH/
BAIwADAOBgNVHQ8BAf8EBAMCB4AwHQYD
VR0OBBYEFJrIK+PF3JdZrA+TuaK0AV+Y
k14fMAoGCCqGSM49BAMCA0gAMEUCIQD9
2wZ+eRnC1N8WrjcPuSgWEn+RA94W0jgl
PAAJnFr/oQIgNyY/is5So8TKktaIqPvT
fb4gESUKI1+m6/lZ9eyQkwU=
-----END CERTIFICATE-----
\end{Verbatim}
\end{minipage}\hfill\begin{minipage}[t]{2.1in}
\centering
\subsection*{Certificate B}
\qrcode[height=2.1in]{-----BEGIN CERTIFICATE-----MIICTTCCAfOgAwIBAgIVAPQ6MVR0bPCWbROo6LXBpxVejt9qMAoGCCqGSM49BAMCMHQxFzAVBgNVBAMMDlBhdHJpY2sgWmF1bmVyMRIwEAYDVQQKDAlQS0Iucm9ja3MxRTBDBgNVBAsMPFBsZWt0cmEgKHN1Y2Nlc3NvciB0byBQYXN0aWVyYSkgLSBTdGFibGUgQW5kcm9pZCBBcHAgU2lnbmluZzAgFw0yNjA5MDUwMDAwMDBaGA8yMTI2MDkwNTAwMDAwMFowdDEXMBUGA1UEAwwOUGF0cmljayBaYXVuZXIxEjAQBgNVBAoMCVBLQi5yb2NrczFFMEMGA1UECww8UGxla3RyYSAoc3VjY2Vzc29yIHRvIFBhc3RpZXJhKSAtIFN0YWJsZSBBbmRyb2lkIEFwcCBTaWduaW5nMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAESSyuzB9YkFh4BDeLM7qr8j/+JKgi8DdbzbF+Z5WQiMcEguPvH7/3BUAg4UYJA21AJAGPf+XJtppqp06T8atMwaNgMF4wHwYDVR0jBBgwFoAULZTKRHxqUC5PF/3pyCiJz7NB/howDAYDVR0TAQH/BAIwADAOBgNVHQ8BAf8EBAMCB4AwHQYDVR0OBBYEFC2UykR8alAuTxf96cgoic+zQf4aMAoGCCqGSM49BAMCA0gAMEUCIEHBIy60NEHNkL1sS3Xa5skwCmlDA7fIXWtSIkx2wH04AiEA4AlCXlhCtRmpwFLot9xkdopwgAm62g+yqyWVrLaRx5Y=-----END CERTIFICATE-----}\par\vspace{\baselineskip}
\raggedright
\begin{Verbatim}[fontsize=\certfont]
-----BEGIN CERTIFICATE-----
MIICTTCCAfOgAwIBAgIVAPQ6MVR0bPCW
bROo6LXBpxVejt9qMAoGCCqGSM49BAMC
MHQxFzAVBgNVBAMMDlBhdHJpY2sgWmF1
bmVyMRIwEAYDVQQKDAlQS0Iucm9ja3Mx
RTBDBgNVBAsMPFBsZWt0cmEgKHN1Y2Nl
c3NvciB0byBQYXN0aWVyYSkgLSBTdGFi
bGUgQW5kcm9pZCBBcHAgU2lnbmluZzAg
Fw0yNjA5MDUwMDAwMDBaGA8yMTI2MDkw
NTAwMDAwMFowdDEXMBUGA1UEAwwOUGF0
cmljayBaYXVuZXIxEjAQBgNVBAoMCVBL
Qi5yb2NrczFFMEMGA1UECww8UGxla3Ry
YSAoc3VjY2Vzc29yIHRvIFBhc3RpZXJh
KSAtIFN0YWJsZSBBbmRyb2lkIEFwcCBT
aWduaW5nMFkwEwYHKoZIzj0CAQYIKoZI
zj0DAQcDQgAESSyuzB9YkFh4BDeLM7qr
8j/+JKgi8DdbzbF+Z5WQiMcEguPvH7/3
BUAg4UYJA21AJAGPf+XJtppqp06T8atM
waNgMF4wHwYDVR0jBBgwFoAULZTKRHxq
UC5PF/3pyCiJz7NB/howDAYDVR0TAQH/
BAIwADAOBgNVHQ8BAf8EBAMCB4AwHQYD
VR0OBBYEFC2UykR8alAuTxf96cgoic+z
Qf4aMAoGCCqGSM49BAMCA0gAMEUCIEHB
Iy60NEHNkL1sS3Xa5skwCmlDA7fIXWtS
Ikx2wH04AiEA4AlCXlhCtRmpwFLot9xk
dopwgAm62g+yqyWVrLaRx5Y=
-----END CERTIFICATE-----
\end{Verbatim}
\end{minipage}\hfill\begin{minipage}[t]{2.1in}
\centering
\subsection*{Certificate C}
\qrcode[height=2.1in]{-----BEGIN CERTIFICATE-----MIICTDCCAfKgAwIBAgIUdie+WFDHZdm/QV7vdKOeebwGU74wCgYIKoZIzj0EAwIwdDEXMBUGA1UEAwwOUGF0cmljayBaYXVuZXIxEjAQBgNVBAoMCVBLQi5yb2NrczFFMEMGA1UECww8UGxla3RyYSAoc3VjY2Vzc29yIHRvIFBhc3RpZXJhKSAtIFN0YWJsZSBBbmRyb2lkIEFwcCBTaWduaW5nMCAXDTI2MDkwNTAwMDAwMFoYDzIxMjYwOTA1MDAwMDAwWjB0MRcwFQYDVQQDDA5QYXRyaWNrIFphdW5lcjESMBAGA1UECgwJUEtCLnJvY2tzMUUwQwYDVQQLDDxQbGVrdHJhIChzdWNjZXNzb3IgdG8gUGFzdGllcmEpIC0gU3RhYmxlIEFuZHJvaWQgQXBwIFNpZ25pbmcwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAR4/XUzP5KfyrH/vNwdoXTbBnGyy60RinKK9rCj0jQ9j5xLSZ5ZH0t5CqjD3Y4qJ39EYWdNW2tzf5sC6UZetSIgo2AwXjAfBgNVHSMEGDAWgBR0oIwzj0Ymgws9UA8oM4Sg15N7VTAMBgNVHRMBAf8EAjAAMA4GA1UdDwEB/wQEAwIHgDAdBgNVHQ4EFgQUdKCMM49GJoMLPVAPKDOEoNeTe1UwCgYIKoZIzj0EAwIDSAAwRQIgIe2wh/M+NJXS//AntDkws1EBfh87Z/W4sm3yFHaV+BECIQC+uX6iS7qSJ54+Piq2bugSXD1BO79ZCB5CwyWZsczJSQ==-----END CERTIFICATE-----}\par\vspace{\baselineskip}
\raggedright
\begin{Verbatim}[fontsize=\certfont]
-----BEGIN CERTIFICATE-----
MIICTDCCAfKgAwIBAgIUdie+WFDHZdm/
QV7vdKOeebwGU74wCgYIKoZIzj0EAwIw
dDEXMBUGA1UEAwwOUGF0cmljayBaYXVu
ZXIxEjAQBgNVBAoMCVBLQi5yb2NrczFF
MEMGA1UECww8UGxla3RyYSAoc3VjY2Vz
c29yIHRvIFBhc3RpZXJhKSAtIFN0YWJs
ZSBBbmRyb2lkIEFwcCBTaWduaW5nMCAX
DTI2MDkwNTAwMDAwMFoYDzIxMjYwOTA1
MDAwMDAwWjB0MRcwFQYDVQQDDA5QYXRy
aWNrIFphdW5lcjESMBAGA1UECgwJUEtC
LnJvY2tzMUUwQwYDVQQLDDxQbGVrdHJh
IChzdWNjZXNzb3IgdG8gUGFzdGllcmEp
IC0gU3RhYmxlIEFuZHJvaWQgQXBwIFNp
Z25pbmcwWTATBgcqhkjOPQIBBggqhkjO
PQMBBwNCAAR4/XUzP5KfyrH/vNwdoXTb
BnGyy60RinKK9rCj0jQ9j5xLSZ5ZH0t5
CqjD3Y4qJ39EYWdNW2tzf5sC6UZetSIg
o2AwXjAfBgNVHSMEGDAWgBR0oIwzj0Ym
gws9UA8oM4Sg15N7VTAMBgNVHRMBAf8E
AjAAMA4GA1UdDwEB/wQEAwIHgDAdBgNV
HQ4EFgQUdKCMM49GJoMLPVAPKDOEoNeT
e1UwCgYIKoZIzj0EAwIDSAAwRQIgIe2w
h/M+NJXS//AntDkws1EBfh87Z/W4sm3y
FHaV+BECIQC+uX6iS7qSJ54+Piq2bugS
XD1BO79ZCB5CwyWZsczJSQ==
-----END CERTIFICATE-----
\end{Verbatim}
\end{minipage}
```

# APK verification

Use Android SDK `apksigner` to identify the current APK signer:

```bash
APK="${1:?usage: verify-stable-apk APK}"
ACTUAL="$(
  apksigner verify --print-certs "$APK" |
  awk -F': ' '/certificate SHA-256 digest/ { print tolower($2); exit }'
)"
case "$ACTUAL" in
  b28ac03130088bb27a356e1dd704b35f2701268474a386c348d07ed92f3ef71a) echo 'OK: PKB.rocks Stable Certificate A' ;;
  0af4f6b9f964886eef11400926a388904388b1f6608d88eeba90d539f74d9099) echo 'OK: PKB.rocks Stable Certificate B' ;;
  c9207e47369e421ae69d0e64ba1ba3e67e9a739d94e444a4ba83c1085cc09a56) echo 'OK: PKB.rocks Stable Certificate C' ;;
  *) echo 'NOT OK: unrecognized signer.' >&2
     echo 'Check for updated signing attestations if this APK continues an authorized lineage.' >&2
     printf 'Actual: %s\n' "$ACTUAL" >&2; exit 1 ;;
esac
```

\clearpage

# Hardware attestations

\vspace{0.75\baselineskip}

```{=latex}
\noindent
\begin{minipage}[t]{2.1in}
\centering
\subsection*{Signer A}
\qrcode[height=2.1in]{-----BEGIN CERTIFICATE-----MIICVTCCAT2gAwIBAgIQAWMelEUa/hQ4ZF0VuptumjANBgkqhkiG9w0BAQsFADAhMR8wHQYDVQQDDBZZdWJpY28gUElWIEF0dGVzdGF0aW9uMCAXDTI0MTIwMTAwMDAwMFoYDzk5OTkxMjMxMjM1OTU5WjAlMSMwIQYDVQQDDBpZdWJpS2V5IFBJViBBdHRlc3RhdGlvbiA5YzBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABFgjT10jyi1pz29id5CDyCHtqBAIspZRBBgwxQNQRpWhzoIJauzmqNoyE82oawoD42BL5X5VY5h6PXISnsfX6dejTjBMMBEGCisGAQQBgsQKAwMEAwUHBDAUBgorBgEEAYLECgMHBAYCBAJRqQkwEAYKKwYBBAGCxAoDCAQCAwIwDwYKKwYBBAGCxAoDCQQBAzANBgkqhkiG9w0BAQsFAAOCAQEANx5sLbC5qkP4bQh9C6jlNOVexzaWhBah+FFhZVORdhsuGoyVRd4QT0vdNRzDo85DL/iyIG8wt/lZEpzjIsE04UrYl0mKle7iYHeSML7aPX6Y8rLaJAW9tdAhFm3UU09MHY5HNJVQ+BDLHgG3MsgwygXdrFDWIzegg+VlsjpQeEZ9x9GXM+mui658t51IHlp2NBjqq4Z9v4oUFZVfJGT1BBOa0dwxBNK6g5IqitrcP7e8eiMn10RcsFfGVmp2PuYi+OP7KovXIzRYR2+eXBFoQIL47w2jfTh/jKMtwXCESUF4NAN1VR+xzAkYdBHM7Y1r3UiV29cKqvhAMAUDtA5Exg==-----END CERTIFICATE-----}\par\vspace{\baselineskip}
\raggedright
\begin{Verbatim}[fontsize=\certfont]
-----BEGIN CERTIFICATE-----
MIICVTCCAT2gAwIBAgIQAWMelEUa/hQ4
ZF0VuptumjANBgkqhkiG9w0BAQsFADAh
MR8wHQYDVQQDDBZZdWJpY28gUElWIEF0
dGVzdGF0aW9uMCAXDTI0MTIwMTAwMDAw
MFoYDzk5OTkxMjMxMjM1OTU5WjAlMSMw
IQYDVQQDDBpZdWJpS2V5IFBJViBBdHRl
c3RhdGlvbiA5YzBZMBMGByqGSM49AgEG
CCqGSM49AwEHA0IABFgjT10jyi1pz29i
d5CDyCHtqBAIspZRBBgwxQNQRpWhzoIJ
auzmqNoyE82oawoD42BL5X5VY5h6PXIS
nsfX6dejTjBMMBEGCisGAQQBgsQKAwME
AwUHBDAUBgorBgEEAYLECgMHBAYCBAJR
qQkwEAYKKwYBBAGCxAoDCAQCAwIwDwYK
KwYBBAGCxAoDCQQBAzANBgkqhkiG9w0B
AQsFAAOCAQEANx5sLbC5qkP4bQh9C6jl
NOVexzaWhBah+FFhZVORdhsuGoyVRd4Q
T0vdNRzDo85DL/iyIG8wt/lZEpzjIsE0
4UrYl0mKle7iYHeSML7aPX6Y8rLaJAW9
tdAhFm3UU09MHY5HNJVQ+BDLHgG3Msgw
ygXdrFDWIzegg+VlsjpQeEZ9x9GXM+mu
i658t51IHlp2NBjqq4Z9v4oUFZVfJGT1
BBOa0dwxBNK6g5IqitrcP7e8eiMn10Rc
sFfGVmp2PuYi+OP7KovXIzRYR2+eXBFo
QIL47w2jfTh/jKMtwXCESUF4NAN1VR+x
zAkYdBHM7Y1r3UiV29cKqvhAMAUDtA5E
xg==
-----END CERTIFICATE-----
\end{Verbatim}
\end{minipage}\hfill\begin{minipage}[t]{2.1in}
\centering
\subsection*{Signer B}
\qrcode[height=2.1in]{-----BEGIN CERTIFICATE-----MIICVTCCAT2gAwIBAgIQATjq/AqgFxqU9Tff1+6ojTANBgkqhkiG9w0BAQsFADAhMR8wHQYDVQQDDBZZdWJpY28gUElWIEF0dGVzdGF0aW9uMCAXDTI0MTIwMTAwMDAwMFoYDzk5OTkxMjMxMjM1OTU5WjAlMSMwIQYDVQQDDBpZdWJpS2V5IFBJViBBdHRlc3RhdGlvbiA5YzBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABEksrswfWJBYeAQ3izO6q/I//iSoIvA3W82xfmeVkIjHBILj7x+/9wVAIOFGCQNtQCQBj3/lybaaaqdOk/GrTMGjTjBMMBEGCisGAQQBgsQKAwMEAwUHBDAUBgorBgEEAYLECgMHBAYCBAJRqdcwEAYKKwYBBAGCxAoDCAQCAwIwDwYKKwYBBAGCxAoDCQQBAzANBgkqhkiG9w0BAQsFAAOCAQEAsmx1azDVz4onZSXTlEViAgL3u6pF8mkmJLqWJrlY3DaQl195EOLUwILiz8Lud3ncMOLY7NCdGn0iRW3hRvrTCJcWMz2g+o86z4MSvY6pZd+OqdgAZSWqrXJ/EHK3XK/ckCXVc/EGw6hX3C7xLN9U1/JLMDrDzW/vcwOD58jUu0AFTveudPdO+v2WxZX8kfQCla4UvTqcEfqNl60e/UjUGkPY3rl+tAmWGPSNTSmUEzNXJDIMzbyazcKmZyZgPbmnL73yUElWozuWix0WqPBKXbDODHCkAM5p9kg3v0fmnLEY1HKSC+v9WHzj8R8jY7LwQNIssZA7FOMSjIl8pO47dQ==-----END CERTIFICATE-----}\par\vspace{\baselineskip}
\raggedright
\begin{Verbatim}[fontsize=\certfont]
-----BEGIN CERTIFICATE-----
MIICVTCCAT2gAwIBAgIQATjq/AqgFxqU
9Tff1+6ojTANBgkqhkiG9w0BAQsFADAh
MR8wHQYDVQQDDBZZdWJpY28gUElWIEF0
dGVzdGF0aW9uMCAXDTI0MTIwMTAwMDAw
MFoYDzk5OTkxMjMxMjM1OTU5WjAlMSMw
IQYDVQQDDBpZdWJpS2V5IFBJViBBdHRl
c3RhdGlvbiA5YzBZMBMGByqGSM49AgEG
CCqGSM49AwEHA0IABEksrswfWJBYeAQ3
izO6q/I//iSoIvA3W82xfmeVkIjHBILj
7x+/9wVAIOFGCQNtQCQBj3/lybaaaqdO
k/GrTMGjTjBMMBEGCisGAQQBgsQKAwME
AwUHBDAUBgorBgEEAYLECgMHBAYCBAJR
qdcwEAYKKwYBBAGCxAoDCAQCAwIwDwYK
KwYBBAGCxAoDCQQBAzANBgkqhkiG9w0B
AQsFAAOCAQEAsmx1azDVz4onZSXTlEVi
AgL3u6pF8mkmJLqWJrlY3DaQl195EOLU
wILiz8Lud3ncMOLY7NCdGn0iRW3hRvrT
CJcWMz2g+o86z4MSvY6pZd+OqdgAZSWq
rXJ/EHK3XK/ckCXVc/EGw6hX3C7xLN9U
1/JLMDrDzW/vcwOD58jUu0AFTveudPdO
+v2WxZX8kfQCla4UvTqcEfqNl60e/UjU
GkPY3rl+tAmWGPSNTSmUEzNXJDIMzbya
zcKmZyZgPbmnL73yUElWozuWix0WqPBK
XbDODHCkAM5p9kg3v0fmnLEY1HKSC+v9
WHzj8R8jY7LwQNIssZA7FOMSjIl8pO47
dQ==
-----END CERTIFICATE-----
\end{Verbatim}
\end{minipage}\hfill\begin{minipage}[t]{2.1in}
\centering
\subsection*{Signer C}
\qrcode[height=2.1in]{-----BEGIN CERTIFICATE-----MIICVTCCAT2gAwIBAgIQAT+EuX5rI7oeEo6iQoC7gzANBgkqhkiG9w0BAQsFADAhMR8wHQYDVQQDDBZZdWJpY28gUElWIEF0dGVzdGF0aW9uMCAXDTI0MTIwMTAwMDAwMFoYDzk5OTkxMjMxMjM1OTU5WjAlMSMwIQYDVQQDDBpZdWJpS2V5IFBJViBBdHRlc3RhdGlvbiA5YzBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABHj9dTM/kp/Ksf+83B2hdNsGcbLLrRGKcor2sKPSND2PnEtJnlkfS3kKqMPdjionf0RhZ01ba3N/mwLpRl61IiCjTjBMMBEGCisGAQQBgsQKAwMEAwUHBDAUBgorBgEEAYLECgMHBAYCBAJRqdswEAYKKwYBBAGCxAoDCAQCAwIwDwYKKwYBBAGCxAoDCQQBAzANBgkqhkiG9w0BAQsFAAOCAQEAJOPy/YkTMzKFY2KyaOcr+0RezXCzN/jXmpCPbk8ILlJ+Vhmfba6wKd6zM1FP8H7zmHlh9lsa3nG3RgYrbErrUZgecXYc1l00CegyRcrc41dozFchzIBOuQRAqg5KrSJeYsO0bppeeg5S7He0n3M1VLvb5uL7NDFAqgOMFUFsuFb2RKCtqgnSkarpV63qpVggEXdX+cOdBd+Gp5j/gMLMPq8zYVuz/CE+Fqh+uGDtecoaKQ1YTAJb2CE2dpmqwQtxTb3MG8wJjhWKdiCMiAP0vunpTs6ot+FRbhKaorl5WA296vpqdZFu72DKVz89esN8WtSyDgCUp/8MPZleR0ipLQ==-----END CERTIFICATE-----}\par\vspace{\baselineskip}
\raggedright
\begin{Verbatim}[fontsize=\certfont]
-----BEGIN CERTIFICATE-----
MIICVTCCAT2gAwIBAgIQAT+EuX5rI7oe
Eo6iQoC7gzANBgkqhkiG9w0BAQsFADAh
MR8wHQYDVQQDDBZZdWJpY28gUElWIEF0
dGVzdGF0aW9uMCAXDTI0MTIwMTAwMDAw
MFoYDzk5OTkxMjMxMjM1OTU5WjAlMSMw
IQYDVQQDDBpZdWJpS2V5IFBJViBBdHRl
c3RhdGlvbiA5YzBZMBMGByqGSM49AgEG
CCqGSM49AwEHA0IABHj9dTM/kp/Ksf+8
3B2hdNsGcbLLrRGKcor2sKPSND2PnEtJ
nlkfS3kKqMPdjionf0RhZ01ba3N/mwLp
Rl61IiCjTjBMMBEGCisGAQQBgsQKAwME
AwUHBDAUBgorBgEEAYLECgMHBAYCBAJR
qdswEAYKKwYBBAGCxAoDCAQCAwIwDwYK
KwYBBAGCxAoDCQQBAzANBgkqhkiG9w0B
AQsFAAOCAQEAJOPy/YkTMzKFY2KyaOcr
+0RezXCzN/jXmpCPbk8ILlJ+Vhmfba6w
Kd6zM1FP8H7zmHlh9lsa3nG3RgYrbErr
UZgecXYc1l00CegyRcrc41dozFchzIBO
uQRAqg5KrSJeYsO0bppeeg5S7He0n3M1
VLvb5uL7NDFAqgOMFUFsuFb2RKCtqgnS
karpV63qpVggEXdX+cOdBd+Gp5j/gMLM
Pq8zYVuz/CE+Fqh+uGDtecoaKQ1YTAJb
2CE2dpmqwQtxTb3MG8wJjhWKdiCMiAP0
vunpTs6ot+FRbhKaorl5WA296vpqdZFu
72DKVz89esN8WtSyDgCUp/8MPZleR0ip
LQ==
-----END CERTIFICATE-----
\end{Verbatim}
\end{minipage}
```

\clearpage

# Device attestation certificates

\vspace{0.75\baselineskip}

```{=latex}
\noindent
\begin{minipage}[t]{2.1in}
\centering
\subsection*{YK1 F9}
\qrcode[height=2.1in]{-----BEGIN CERTIFICATE-----MIIC8DCCAdqgAwIBAgIJALo25FZYZJYbMAsGCSqGSIb3DQEBCzAlMSMwIQYDVQQDDBpZdWJpY28gUElWIEF0dGVzdGF0aW9uIEIgMTAgFw0yNDEyMDEwMDAwMDBaGA85OTk5MTIzMTIzNTk1OVowITEfMB0GA1UEAwwWWXViaWNvIFBJViBBdHRlc3RhdGlvbjCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBALcKTvN4UA+ve35Fauusc5FOpJ+sjbdChdujlwJqBqgr7iIWwix7Y2+ElhtdLRPtnh9xJ5NQ/kj5MbuQYjHQQZCagyVgiZhLV2Z7BzovCw/tGFkY311TTaDfk1DABxAT8cMb6AOQa5fHzqNwagWtA3KTEPOUvBn8NKavxN5UmEoqL5OUdQbphWxQY8UhOgnWMeao30Vrx8ebyA5osIhDW9A10b5Pd4rgb11aa82P9C1cAPLsGvaq9ufaI2Yce890eJ037Jis6I1r3qHz6IvxdXgr5UXyOhslGpKr1jz983UjSnaKWbbSYIv6OuEqUbUqjIpSA2dRYJ3gcnMTKnQcy+cCAwEAAaMpMCcwEQYKKwYBBAGCxAoDAwQDBQcEMBIGA1UdEwEB/wQIMAYBAf8CAQAwCwYJKoZIhvcNAQELA4IBAQBqtYVRoyvkCHQO6m5xy5e4K/gE8apPoCmHmZXp7p6q5mtuFMbNHwuc/3yrllTlyov8JpZzY7U1Dwl2rK7H6pV9gVOCngesZsWk7eNH4hqGpG73/6+ymyokeADRD3AqdM6VurEa1HXL7qb9Rl3Hr8+YQXk/HpaDAyub/TN39Jxhydpn18L1fEDzVLq9kPuc4fBEK/ZmzHAH87a0o+sVVQ94MfcNAQhN4T3noUhXkq2fKhJQnOMg0SE2IKXblhZNTZXr0r55ssHyWwwFH9txKVtvr9voFaFMs2oOnFk6Qy6DcUD5rosE9WGo6r2HHBCda+J/kbm5BE1NPr4y8QzXIG8g-----END CERTIFICATE-----}\par\vspace{\baselineskip}
\raggedright
\begin{Verbatim}[fontsize=\certfont]
-----BEGIN CERTIFICATE-----
MIIC8DCCAdqgAwIBAgIJALo25FZYZJYb
MAsGCSqGSIb3DQEBCzAlMSMwIQYDVQQD
DBpZdWJpY28gUElWIEF0dGVzdGF0aW9u
IEIgMTAgFw0yNDEyMDEwMDAwMDBaGA85
OTk5MTIzMTIzNTk1OVowITEfMB0GA1UE
AwwWWXViaWNvIFBJViBBdHRlc3RhdGlv
bjCCASIwDQYJKoZIhvcNAQEBBQADggEP
ADCCAQoCggEBALcKTvN4UA+ve35Fauus
c5FOpJ+sjbdChdujlwJqBqgr7iIWwix7
Y2+ElhtdLRPtnh9xJ5NQ/kj5MbuQYjHQ
QZCagyVgiZhLV2Z7BzovCw/tGFkY311T
TaDfk1DABxAT8cMb6AOQa5fHzqNwagWt
A3KTEPOUvBn8NKavxN5UmEoqL5OUdQbp
hWxQY8UhOgnWMeao30Vrx8ebyA5osIhD
W9A10b5Pd4rgb11aa82P9C1cAPLsGvaq
9ufaI2Yce890eJ037Jis6I1r3qHz6Ivx
dXgr5UXyOhslGpKr1jz983UjSnaKWbbS
YIv6OuEqUbUqjIpSA2dRYJ3gcnMTKnQc
y+cCAwEAAaMpMCcwEQYKKwYBBAGCxAoD
AwQDBQcEMBIGA1UdEwEB/wQIMAYBAf8C
AQAwCwYJKoZIhvcNAQELA4IBAQBqtYVR
oyvkCHQO6m5xy5e4K/gE8apPoCmHmZXp
7p6q5mtuFMbNHwuc/3yrllTlyov8JpZz
Y7U1Dwl2rK7H6pV9gVOCngesZsWk7eNH
4hqGpG73/6+ymyokeADRD3AqdM6VurEa
1HXL7qb9Rl3Hr8+YQXk/HpaDAyub/TN3
9Jxhydpn18L1fEDzVLq9kPuc4fBEK/Zm
zHAH87a0o+sVVQ94MfcNAQhN4T3noUhX
kq2fKhJQnOMg0SE2IKXblhZNTZXr0r55
ssHyWwwFH9txKVtvr9voFaFMs2oOnFk6
Qy6DcUD5rosE9WGo6r2HHBCda+J/kbm5
BE1NPr4y8QzXIG8g
-----END CERTIFICATE-----
\end{Verbatim}
\end{minipage}\hfill\begin{minipage}[t]{2.1in}
\centering
\subsection*{YK2 F9}
\qrcode[height=2.1in]{-----BEGIN CERTIFICATE-----MIIC8DCCAdqgAwIBAgIJALo25FZYZJYbMAsGCSqGSIb3DQEBCzAlMSMwIQYDVQQDDBpZdWJpY28gUElWIEF0dGVzdGF0aW9uIEIgMTAgFw0yNDEyMDEwMDAwMDBaGA85OTk5MTIzMTIzNTk1OVowITEfMB0GA1UEAwwWWXViaWNvIFBJViBBdHRlc3RhdGlvbjCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBALcKTvN4UA+ve35Fauusc5FOpJ+sjbdChdujlwJqBqgr7iIWwix7Y2+ElhtdLRPtnh9xJ5NQ/kj5MbuQYjHQQZCagyVgiZhLV2Z7BzovCw/tGFkY311TTaDfk1DABxAT8cMb6AOQa5fHzqNwagWtA3KTEPOUvBn8NKavxN5UmEoqL5OUdQbphWxQY8UhOgnWMeao30Vrx8ebyA5osIhDW9A10b5Pd4rgb11aa82P9C1cAPLsGvaq9ufaI2Yce890eJ037Jis6I1r3qHz6IvxdXgr5UXyOhslGpKr1jz983UjSnaKWbbSYIv6OuEqUbUqjIpSA2dRYJ3gcnMTKnQcy+cCAwEAAaMpMCcwEQYKKwYBBAGCxAoDAwQDBQcEMBIGA1UdEwEB/wQIMAYBAf8CAQAwCwYJKoZIhvcNAQELA4IBAQBqtYVRoyvkCHQO6m5xy5e4K/gE8apPoCmHmZXp7p6q5mtuFMbNHwuc/3yrllTlyov8JpZzY7U1Dwl2rK7H6pV9gVOCngesZsWk7eNH4hqGpG73/6+ymyokeADRD3AqdM6VurEa1HXL7qb9Rl3Hr8+YQXk/HpaDAyub/TN39Jxhydpn18L1fEDzVLq9kPuc4fBEK/ZmzHAH87a0o+sVVQ94MfcNAQhN4T3noUhXkq2fKhJQnOMg0SE2IKXblhZNTZXr0r55ssHyWwwFH9txKVtvr9voFaFMs2oOnFk6Qy6DcUD5rosE9WGo6r2HHBCda+J/kbm5BE1NPr4y8QzXIG8g-----END CERTIFICATE-----}\par\vspace{\baselineskip}
\raggedright
\begin{Verbatim}[fontsize=\certfont]
-----BEGIN CERTIFICATE-----
MIIC8DCCAdqgAwIBAgIJALo25FZYZJYb
MAsGCSqGSIb3DQEBCzAlMSMwIQYDVQQD
DBpZdWJpY28gUElWIEF0dGVzdGF0aW9u
IEIgMTAgFw0yNDEyMDEwMDAwMDBaGA85
OTk5MTIzMTIzNTk1OVowITEfMB0GA1UE
AwwWWXViaWNvIFBJViBBdHRlc3RhdGlv
bjCCASIwDQYJKoZIhvcNAQEBBQADggEP
ADCCAQoCggEBALcKTvN4UA+ve35Fauus
c5FOpJ+sjbdChdujlwJqBqgr7iIWwix7
Y2+ElhtdLRPtnh9xJ5NQ/kj5MbuQYjHQ
QZCagyVgiZhLV2Z7BzovCw/tGFkY311T
TaDfk1DABxAT8cMb6AOQa5fHzqNwagWt
A3KTEPOUvBn8NKavxN5UmEoqL5OUdQbp
hWxQY8UhOgnWMeao30Vrx8ebyA5osIhD
W9A10b5Pd4rgb11aa82P9C1cAPLsGvaq
9ufaI2Yce890eJ037Jis6I1r3qHz6Ivx
dXgr5UXyOhslGpKr1jz983UjSnaKWbbS
YIv6OuEqUbUqjIpSA2dRYJ3gcnMTKnQc
y+cCAwEAAaMpMCcwEQYKKwYBBAGCxAoD
AwQDBQcEMBIGA1UdEwEB/wQIMAYBAf8C
AQAwCwYJKoZIhvcNAQELA4IBAQBqtYVR
oyvkCHQO6m5xy5e4K/gE8apPoCmHmZXp
7p6q5mtuFMbNHwuc/3yrllTlyov8JpZz
Y7U1Dwl2rK7H6pV9gVOCngesZsWk7eNH
4hqGpG73/6+ymyokeADRD3AqdM6VurEa
1HXL7qb9Rl3Hr8+YQXk/HpaDAyub/TN3
9Jxhydpn18L1fEDzVLq9kPuc4fBEK/Zm
zHAH87a0o+sVVQ94MfcNAQhN4T3noUhX
kq2fKhJQnOMg0SE2IKXblhZNTZXr0r55
ssHyWwwFH9txKVtvr9voFaFMs2oOnFk6
Qy6DcUD5rosE9WGo6r2HHBCda+J/kbm5
BE1NPr4y8QzXIG8g
-----END CERTIFICATE-----
\end{Verbatim}
\end{minipage}\hfill\begin{minipage}[t]{2.1in}
\centering
\subsection*{YK3 F9}
\qrcode[height=2.1in]{-----BEGIN CERTIFICATE-----MIIC8DCCAdqgAwIBAgIJALo25FZYZJYbMAsGCSqGSIb3DQEBCzAlMSMwIQYDVQQDDBpZdWJpY28gUElWIEF0dGVzdGF0aW9uIEIgMTAgFw0yNDEyMDEwMDAwMDBaGA85OTk5MTIzMTIzNTk1OVowITEfMB0GA1UEAwwWWXViaWNvIFBJViBBdHRlc3RhdGlvbjCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBALcKTvN4UA+ve35Fauusc5FOpJ+sjbdChdujlwJqBqgr7iIWwix7Y2+ElhtdLRPtnh9xJ5NQ/kj5MbuQYjHQQZCagyVgiZhLV2Z7BzovCw/tGFkY311TTaDfk1DABxAT8cMb6AOQa5fHzqNwagWtA3KTEPOUvBn8NKavxN5UmEoqL5OUdQbphWxQY8UhOgnWMeao30Vrx8ebyA5osIhDW9A10b5Pd4rgb11aa82P9C1cAPLsGvaq9ufaI2Yce890eJ037Jis6I1r3qHz6IvxdXgr5UXyOhslGpKr1jz983UjSnaKWbbSYIv6OuEqUbUqjIpSA2dRYJ3gcnMTKnQcy+cCAwEAAaMpMCcwEQYKKwYBBAGCxAoDAwQDBQcEMBIGA1UdEwEB/wQIMAYBAf8CAQAwCwYJKoZIhvcNAQELA4IBAQBqtYVRoyvkCHQO6m5xy5e4K/gE8apPoCmHmZXp7p6q5mtuFMbNHwuc/3yrllTlyov8JpZzY7U1Dwl2rK7H6pV9gVOCngesZsWk7eNH4hqGpG73/6+ymyokeADRD3AqdM6VurEa1HXL7qb9Rl3Hr8+YQXk/HpaDAyub/TN39Jxhydpn18L1fEDzVLq9kPuc4fBEK/ZmzHAH87a0o+sVVQ94MfcNAQhN4T3noUhXkq2fKhJQnOMg0SE2IKXblhZNTZXr0r55ssHyWwwFH9txKVtvr9voFaFMs2oOnFk6Qy6DcUD5rosE9WGo6r2HHBCda+J/kbm5BE1NPr4y8QzXIG8g-----END CERTIFICATE-----}\par\vspace{\baselineskip}
\raggedright
\begin{Verbatim}[fontsize=\certfont]
-----BEGIN CERTIFICATE-----
MIIC8DCCAdqgAwIBAgIJALo25FZYZJYb
MAsGCSqGSIb3DQEBCzAlMSMwIQYDVQQD
DBpZdWJpY28gUElWIEF0dGVzdGF0aW9u
IEIgMTAgFw0yNDEyMDEwMDAwMDBaGA85
OTk5MTIzMTIzNTk1OVowITEfMB0GA1UE
AwwWWXViaWNvIFBJViBBdHRlc3RhdGlv
bjCCASIwDQYJKoZIhvcNAQEBBQADggEP
ADCCAQoCggEBALcKTvN4UA+ve35Fauus
c5FOpJ+sjbdChdujlwJqBqgr7iIWwix7
Y2+ElhtdLRPtnh9xJ5NQ/kj5MbuQYjHQ
QZCagyVgiZhLV2Z7BzovCw/tGFkY311T
TaDfk1DABxAT8cMb6AOQa5fHzqNwagWt
A3KTEPOUvBn8NKavxN5UmEoqL5OUdQbp
hWxQY8UhOgnWMeao30Vrx8ebyA5osIhD
W9A10b5Pd4rgb11aa82P9C1cAPLsGvaq
9ufaI2Yce890eJ037Jis6I1r3qHz6Ivx
dXgr5UXyOhslGpKr1jz983UjSnaKWbbS
YIv6OuEqUbUqjIpSA2dRYJ3gcnMTKnQc
y+cCAwEAAaMpMCcwEQYKKwYBBAGCxAoD
AwQDBQcEMBIGA1UdEwEB/wQIMAYBAf8C
AQAwCwYJKoZIhvcNAQELA4IBAQBqtYVR
oyvkCHQO6m5xy5e4K/gE8apPoCmHmZXp
7p6q5mtuFMbNHwuc/3yrllTlyov8JpZz
Y7U1Dwl2rK7H6pV9gVOCngesZsWk7eNH
4hqGpG73/6+ymyokeADRD3AqdM6VurEa
1HXL7qb9Rl3Hr8+YQXk/HpaDAyub/TN3
9Jxhydpn18L1fEDzVLq9kPuc4fBEK/Zm
zHAH87a0o+sVVQ94MfcNAQhN4T3noUhX
kq2fKhJQnOMg0SE2IKXblhZNTZXr0r55
ssHyWwwFH9txKVtvr9voFaFMs2oOnFk6
Qy6DcUD5rosE9WGo6r2HHBCda+J/kbm5
BE1NPr4y8QzXIG8g
-----END CERTIFICATE-----
\end{Verbatim}
\end{minipage}
```

\clearpage

# Manufacturer certificate chain

\vspace{0.75\baselineskip}

```{=latex}
\noindent
\begin{minipage}[t]{2.1in}
\centering
\subsection*{PIV Attestation B 1}
\qrcode[height=2.1in]{-----BEGIN CERTIFICATE-----MIIDSTCCAjGgAwIBAgIUWVf2oJG+t1qP8t8TicWgJ2KYan4wDQYJKoZIhvcNAQELBQAwLjEsMCoGA1UEAwwjWXViaWNvIEF0dGVzdGF0aW9uIEludGVybWVkaWF0ZSBCIDEwIBcNMjQxMjAxMDAwMDAwWhgPOTk5OTEyMzEyMzU5NTlaMCUxIzAhBgNVBAMMGll1YmljbyBQSVYgQXR0ZXN0YXRpb24gQiAxMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAv7WBL9/5AKxSpCMoL63183WqRtFrOHY7tdyuGtoidoYWQrxVaV9S+ZwH0aynh0IzD5A/PvCtuxdtL5w2cAI3tgsborOlEert4IZ904CZQfq3ooar1an/wssbtMpPOQkC3MQiqrUyHlFS2BTbuwbBXY66lSVX/tGRuUgnBdfBJtcQKS6MO4bU5ndPQqhGPyzcyY1LvlfzK7KJ1r/bixCRFqjhJRnPs0Czpg6rkRrFgC6cd5bK1UgTsJy+3wrIqkv4CeV3EhSVnhnQjZgIrdIcI5WZ8T1Oq3OhMlWmY0K0dy/oZdP/bpbG2qbyHLa6gprLT/qChQWLmffxn6D2DAB1zQIDAQABo2YwZDAdBgNVHQ4EFgQUM0Nt3QHo7eGzaKMZn2SmXT74vpcwHwYDVR0jBBgwFoAU6rdCkJ4Me2R621R8A7p8Tp/YoWEwEgYDVR0TAQH/BAgwBgEB/wIBATAOBgNVHQ8BAf8EBAMCAYYwDQYJKoZIhvcNAQELBQADggEBAI0HwoS84fKMUyIof1LdUXvyeAMmEwW7+nVETvxNNlTMuwv7zPJ4XZAm9Fv95tz9CqZBj6l1PAPQn6Zht9LQA92OF7W7buuXuxuusBTgLM0C1iX2CGXqY/k/uSNvi3ZYfrpd44TIrfrr8bCG9ux7B5ZCRqb8adDUm92Yz3lK1aX2M6CwjC9IZVTXQWhLyP8Ys3p7rb20CO2jJzV94deJ/+AsEb+bnCQImPat1GDKwrBosar+BxtU7k6kgkxZ0G384O59GFXqnwkbw2b5HhORvOsX7nhOUhePFufzi1vT1g8Tzbwr+TUfTwo2biKHHcI762KGtp8o6Bcv5y8WgExFuWY=-----END CERTIFICATE-----}\par\vspace{\baselineskip}
\raggedright
\begin{Verbatim}[fontsize=\certfont]
-----BEGIN CERTIFICATE-----
MIIDSTCCAjGgAwIBAgIUWVf2oJG+t1qP
8t8TicWgJ2KYan4wDQYJKoZIhvcNAQEL
BQAwLjEsMCoGA1UEAwwjWXViaWNvIEF0
dGVzdGF0aW9uIEludGVybWVkaWF0ZSBC
IDEwIBcNMjQxMjAxMDAwMDAwWhgPOTk5
OTEyMzEyMzU5NTlaMCUxIzAhBgNVBAMM
Gll1YmljbyBQSVYgQXR0ZXN0YXRpb24g
QiAxMIIBIjANBgkqhkiG9w0BAQEFAAOC
AQ8AMIIBCgKCAQEAv7WBL9/5AKxSpCMo
L63183WqRtFrOHY7tdyuGtoidoYWQrxV
aV9S+ZwH0aynh0IzD5A/PvCtuxdtL5w2
cAI3tgsborOlEert4IZ904CZQfq3ooar
1an/wssbtMpPOQkC3MQiqrUyHlFS2BTb
uwbBXY66lSVX/tGRuUgnBdfBJtcQKS6M
O4bU5ndPQqhGPyzcyY1LvlfzK7KJ1r/b
ixCRFqjhJRnPs0Czpg6rkRrFgC6cd5bK
1UgTsJy+3wrIqkv4CeV3EhSVnhnQjZgI
rdIcI5WZ8T1Oq3OhMlWmY0K0dy/oZdP/
bpbG2qbyHLa6gprLT/qChQWLmffxn6D2
DAB1zQIDAQABo2YwZDAdBgNVHQ4EFgQU
M0Nt3QHo7eGzaKMZn2SmXT74vpcwHwYD
VR0jBBgwFoAU6rdCkJ4Me2R621R8A7p8
Tp/YoWEwEgYDVR0TAQH/BAgwBgEB/wIB
ATAOBgNVHQ8BAf8EBAMCAYYwDQYJKoZI
hvcNAQELBQADggEBAI0HwoS84fKMUyIo
f1LdUXvyeAMmEwW7+nVETvxNNlTMuwv7
zPJ4XZAm9Fv95tz9CqZBj6l1PAPQn6Zh
t9LQA92OF7W7buuXuxuusBTgLM0C1iX2
CGXqY/k/uSNvi3ZYfrpd44TIrfrr8bCG
9ux7B5ZCRqb8adDUm92Yz3lK1aX2M6Cw
jC9IZVTXQWhLyP8Ys3p7rb20CO2jJzV9
4deJ/+AsEb+bnCQImPat1GDKwrBosar+
BxtU7k6kgkxZ0G384O59GFXqnwkbw2b5
HhORvOsX7nhOUhePFufzi1vT1g8Tzbwr
+TUfTwo2biKHHcI762KGtp8o6Bcv5y8W
gExFuWY=
-----END CERTIFICATE-----
\end{Verbatim}
\end{minipage}\hfill\begin{minipage}[t]{2.1in}
\centering
\subsection*{Intermediate B 1}
\qrcode[height=2.1in]{-----BEGIN CERTIFICATE-----MIIDSDCCAjCgAwIBAgIUDqERw+4RnGSggxgUewJFEPDRZ3YwDQYJKoZIhvcNAQELBQAwJDEiMCAGA1UEAwwZWXViaWNvIEF0dGVzdGF0aW9uIFJvb3QgMTAgFw0yNDEyMDEwMDAwMDBaGA85OTk5MTIzMTIzNTk1OVowLjEsMCoGA1UEAwwjWXViaWNvIEF0dGVzdGF0aW9uIEludGVybWVkaWF0ZSBCIDEwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDI7XnH+ZvDwMCQU8M8ZeV5qscublvVYaaRt3Ybaxn9godLx5swH0lXrdgjh5h7FpVgCgYYX7E4bl1vbzULemrMWT8N3WMGUe8QAJbBeioV7W/E+hTZP/0SKJVa3ewKBo6ULeMnfQZDrVORAk8wTLq2v5Llj5vMj7JtOotKa9J7nHS8kLmzXXSaj0SwEPh5OAZUTNV4zs1bvoTAQQWrL4/J9QuKt6WCFE5nUNiRQcEbVF8mlqK2bx2z6okVltyDVLCxYbpUTELvY1usR3DTGPUoIClOm4crpwnDRLVHvjYePGBB//pEyzxA/gcScxjwaH1ZUw9bnSbHyurKqbTa1KvjAgMBAAGjZjBkMB0GA1UdDgQWBBTqt0KQngx7ZHrbVHwDunxOn9ihYTAfBgNVHSMEGDAWgBTS7u9aIo06bVwjlz3yhdUm8SV7kjASBgNVHRMBAf8ECDAGAQH/AgECMA4GA1UdDwEB/wQEAwIBhjANBgkqhkiG9w0BAQsFAAOCAQEAqQaCWMxTGqVVX7Sk7kkJmUueTSYKuU6+KBBSgwIRnlw9K7He1IpxZ0hdwpPNikKjmcyFgFPzhImwHJgxxuT90Pw3vYOdcJJNktDg35PXOfzSn15cFAx1RO0mPTmIb8dXiEWOpzoXvdwXDM41ZaCDYMT7w4IQtMyvE7xUBZq2bjtAnq/NDUA7be4H8H3ipC+/+NKlUrcUh+j48K67WI0u1m6FeQueBA7n06j825rqDqsaLs9Tb7KAHAw8PmrWaNPG2kjKerxPEfecivlFawp2RWZvxrVtn3TV2SBxyCJCkXsND05dCErVHSJIs+BdtTVNY9AwtyPmnyb0v4mSTzvWdw==-----END CERTIFICATE-----}\par\vspace{\baselineskip}
\raggedright
\begin{Verbatim}[fontsize=\certfont]
-----BEGIN CERTIFICATE-----
MIIDSDCCAjCgAwIBAgIUDqERw+4RnGSg
gxgUewJFEPDRZ3YwDQYJKoZIhvcNAQEL
BQAwJDEiMCAGA1UEAwwZWXViaWNvIEF0
dGVzdGF0aW9uIFJvb3QgMTAgFw0yNDEy
MDEwMDAwMDBaGA85OTk5MTIzMTIzNTk1
OVowLjEsMCoGA1UEAwwjWXViaWNvIEF0
dGVzdGF0aW9uIEludGVybWVkaWF0ZSBC
IDEwggEiMA0GCSqGSIb3DQEBAQUAA4IB
DwAwggEKAoIBAQDI7XnH+ZvDwMCQU8M8
ZeV5qscublvVYaaRt3Ybaxn9godLx5sw
H0lXrdgjh5h7FpVgCgYYX7E4bl1vbzUL
emrMWT8N3WMGUe8QAJbBeioV7W/E+hTZ
P/0SKJVa3ewKBo6ULeMnfQZDrVORAk8w
TLq2v5Llj5vMj7JtOotKa9J7nHS8kLmz
XXSaj0SwEPh5OAZUTNV4zs1bvoTAQQWr
L4/J9QuKt6WCFE5nUNiRQcEbVF8mlqK2
bx2z6okVltyDVLCxYbpUTELvY1usR3DT
GPUoIClOm4crpwnDRLVHvjYePGBB//pE
yzxA/gcScxjwaH1ZUw9bnSbHyurKqbTa
1KvjAgMBAAGjZjBkMB0GA1UdDgQWBBTq
t0KQngx7ZHrbVHwDunxOn9ihYTAfBgNV
HSMEGDAWgBTS7u9aIo06bVwjlz3yhdUm
8SV7kjASBgNVHRMBAf8ECDAGAQH/AgEC
MA4GA1UdDwEB/wQEAwIBhjANBgkqhkiG
9w0BAQsFAAOCAQEAqQaCWMxTGqVVX7Sk
7kkJmUueTSYKuU6+KBBSgwIRnlw9K7He
1IpxZ0hdwpPNikKjmcyFgFPzhImwHJgx
xuT90Pw3vYOdcJJNktDg35PXOfzSn15c
FAx1RO0mPTmIb8dXiEWOpzoXvdwXDM41
ZaCDYMT7w4IQtMyvE7xUBZq2bjtAnq/N
DUA7be4H8H3ipC+/+NKlUrcUh+j48K67
WI0u1m6FeQueBA7n06j825rqDqsaLs9T
b7KAHAw8PmrWaNPG2kjKerxPEfecivlF
awp2RWZvxrVtn3TV2SBxyCJCkXsND05d
CErVHSJIs+BdtTVNY9AwtyPmnyb0v4mS
TzvWdw==
-----END CERTIFICATE-----
\end{Verbatim}
\end{minipage}\hfill\begin{minipage}[t]{2.1in}
\centering
\subsection*{Attestation Root 1}
\qrcode[height=2.1in]{-----BEGIN CERTIFICATE-----MIIDPjCCAiagAwIBAgIUXzeiEDJEOTt14F5n0o6Zf/bBwiUwDQYJKoZIhvcNAQENBQAwJDEiMCAGA1UEAwwZWXViaWNvIEF0dGVzdGF0aW9uIFJvb3QgMTAgFw0yNDEyMDEwMDAwMDBaGA85OTk5MTIzMTIzNTk1OVowJDEiMCAGA1UEAwwZWXViaWNvIEF0dGVzdGF0aW9uIFJvb3QgMTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAMZ6/TxM8rIT+EaoPvG81ontMOo/2mQ2RBwJHS0QZcxVaNXvl12LUhBZ5LmiBScIZd1Rnx1od585h+/dhK7hEm7JAALkKKts1fO53KGNLZujz5h3wGncr4hyKF0G74b/U3K9hE5mGND6zqYchCRAHfrYMYRDF4YL0X4D5nGdxvppAy6nkEmtWmMnwO3i0TAucsrbE485HvGM4r0VpgVdJpvgQjiTJCTIq+D35hwtT8QDIv+nGvpcyi5wcIfCkzyCimJukhYy6KoqNMKQEdpNiSOvWyDMTMt1bwCvEzpw91u+msUt4rj0efnO9s0ZOwdwMRDnH4xgUl5ZLwrrPkfC1/0CAwEAAaNmMGQwHQYDVR0OBBYEFNLu71oijTptXCOXPfKF1SbxJXuSMB8GA1UdIwQYMBaAFNLu71oijTptXCOXPfKF1SbxJXuSMBIGA1UdEwEB/wQIMAYBAf8CAQMwDgYDVR0PAQH/BAQDAgGGMA0GCSqGSIb3DQEBDQUAA4IBAQC3IW/sgB9pZ8apJNjxuGoX+FkILks0wMNrdXL/coUvsrhzsvl6mePMrbGJByJ1XnquB5sgcRENFxdQFma3mio8Upf1owM1ZreXrJ0mADG2BplqbJnxiyYa+R11reIFTWeIhMNcZKsDZrFAyPuFjCWSQvJmNWe9mFRYFgNhXJKkXIb5H1XgEDlwiedYRM7VolBNlld6pRFKlX8ust6OTMOeADl2xNF0m1LThSdeuXvDyC1g9+ILfz3S6OIYgc3iroRcFD354g7rKfu67qFAw9gC4yi0xBTPrY95rh4/HqaUYCA/L8ldRk6H7Xk35D+WVpmq2Sh/xT5HiFuhf4wJb0bK-----END CERTIFICATE-----}\par\vspace{\baselineskip}
\raggedright
\begin{Verbatim}[fontsize=\certfont]
-----BEGIN CERTIFICATE-----
MIIDPjCCAiagAwIBAgIUXzeiEDJEOTt1
4F5n0o6Zf/bBwiUwDQYJKoZIhvcNAQEN
BQAwJDEiMCAGA1UEAwwZWXViaWNvIEF0
dGVzdGF0aW9uIFJvb3QgMTAgFw0yNDEy
MDEwMDAwMDBaGA85OTk5MTIzMTIzNTk1
OVowJDEiMCAGA1UEAwwZWXViaWNvIEF0
dGVzdGF0aW9uIFJvb3QgMTCCASIwDQYJ
KoZIhvcNAQEBBQADggEPADCCAQoCggEB
AMZ6/TxM8rIT+EaoPvG81ontMOo/2mQ2
RBwJHS0QZcxVaNXvl12LUhBZ5LmiBScI
Zd1Rnx1od585h+/dhK7hEm7JAALkKKts
1fO53KGNLZujz5h3wGncr4hyKF0G74b/
U3K9hE5mGND6zqYchCRAHfrYMYRDF4YL
0X4D5nGdxvppAy6nkEmtWmMnwO3i0TAu
csrbE485HvGM4r0VpgVdJpvgQjiTJCTI
q+D35hwtT8QDIv+nGvpcyi5wcIfCkzyC
imJukhYy6KoqNMKQEdpNiSOvWyDMTMt1
bwCvEzpw91u+msUt4rj0efnO9s0ZOwdw
MRDnH4xgUl5ZLwrrPkfC1/0CAwEAAaNm
MGQwHQYDVR0OBBYEFNLu71oijTptXCOX
PfKF1SbxJXuSMB8GA1UdIwQYMBaAFNLu
71oijTptXCOXPfKF1SbxJXuSMBIGA1Ud
EwEB/wQIMAYBAf8CAQMwDgYDVR0PAQH/
BAQDAgGGMA0GCSqGSIb3DQEBDQUAA4IB
AQC3IW/sgB9pZ8apJNjxuGoX+FkILks0
wMNrdXL/coUvsrhzsvl6mePMrbGJByJ1
XnquB5sgcRENFxdQFma3mio8Upf1owM1
ZreXrJ0mADG2BplqbJnxiyYa+R11reIF
TWeIhMNcZKsDZrFAyPuFjCWSQvJmNWe9
mFRYFgNhXJKkXIb5H1XgEDlwiedYRM7V
olBNlld6pRFKlX8ust6OTMOeADl2xNF0
m1LThSdeuXvDyC1g9+ILfz3S6OIYgc3i
roRcFD354g7rKfu67qFAw9gC4yi0xBTP
rY95rh4/HqaUYCA/L8ldRk6H7Xk35D+W
Vpmq2Sh/xT5HiFuhf4wJb0bK
-----END CERTIFICATE-----
\end{Verbatim}
\end{minipage}
```

\clearpage

# Hardware evidence verification

The accompanying script `scripts/verify-signing-key-attestations.py` verifies the printed certificates and their QR payloads from this Markdown source. It traces each slot attestation through its F9 certificate and the two manufacturer intermediates to Yubico Attestation Root 1. It also verifies the Android certificate self-signatures and the matching public keys.

```sh
python3 scripts/verify-signing-key-attestations.py
```

Python 3 and OpenSSL 3 are required. If necessary, select OpenSSL explicitly with `--openssl /path/to/openssl`. No YubiKey, PIN, private evidence directory or additional Python package is required.

The root is independently fetched over HTTPS from:

<https://developers.yubico.com/PKI/yubico-ca-1.pem>

The downloaded root must match this pinned DER SHA-256 fingerprint:

```text
62760c6a6ef91679f454c8902b80fd009825b3f25da90f1fbace2ec6586cd5a8
```

The root is cached under `~/.cache/pkb-signing-attestations`. Repeated runs reuse that file only after checking its fingerprint. An unexpected cached or downloaded root causes verification to fail. Embedded intermediates are treated as untrusted until their complete certificate chain verifies.

This script does not validate the document QES signatures or Android installation behaviour.
