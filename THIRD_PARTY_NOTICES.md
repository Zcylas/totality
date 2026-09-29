# Third-Party Notices

Totality is licensed under the MIT License (see `LICENSE`). The Totality jar also redistributes the
third-party components below for the Voice Input API. Their license texts are shipped in the jar
under `META-INF/licenses/third-party/` (in this repository: `licenses/third-party/`).

Totality does not bundle JNA: it uses the copy Minecraft already ships (net.java.dev.jna:jna 5.17.0).

## Vosk API 0.3.45 (`com.alphacephei:vosk:0.3.45`)

- Copyright Alpha Cephei Inc. — https://github.com/alphacep/vosk-api
- License: Apache License 2.0 (`Apache-2.0.txt`)
- Redistributed from the official Maven Central artifact (SHA-256
  `9e38e96e4448a41d889bb254b2f8424945554945c6e77e1cd97a3d8633fac2ba`), nested at
  `META-INF/jars/vosk-0.3.45.jar`. Every upstream entry is unmodified; Fabric Loom's Jar-in-Jar
  packaging only adds a generated `fabric.mod.json` so Fabric Loader can load it.
  It contains the Java wrapper (`org.vosk.*`) and the native libraries `linux-x86-64/libvosk.so`,
  `win32-x86-64/libvosk.dll` and `darwin/libvosk.dylib`.

The Vosk native libraries statically include:

| Component | License | Text |
|---|---|---|
| Kaldi speech recognition toolkit (https://github.com/kaldi-asr/kaldi) | Apache-2.0 | `Apache-2.0.txt` |
| OpenFst (Alpha Cephei fork, https://github.com/alphacep/openfst) | Apache-2.0 | `Apache-2.0.txt` |
| OpenBLAS 0.3.20 — Copyright (c) 2011-2014, The OpenBLAS Project | BSD-3-Clause | `OpenBLAS-BSD-3-Clause.txt` |
| CLAPACK (https://github.com/alphacep/clapack) — Copyright (c) 1992-2008 The University of Tennessee | BSD-style | `CLAPACK-BSD.txt` |

The Windows natives in the same jar are accompanied by MinGW-w64 runtime libraries (built with
GCC 10, mingw-w64 8.0.0):

| File | License | Text |
|---|---|---|
| `win32-x86-64/libstdc++-6.dll`, `win32-x86-64/libgcc_s_seh-1.dll` (GCC runtime libraries) | GPL-3.0 with the GCC Runtime Library Exception 3.1 | `GPL-3.0.txt`, `GCC-Runtime-Library-Exception-3.1.txt` |
| `win32-x86-64/libwinpthread-1.dll` (mingw-w64 winpthreads) — Copyright (c) 2011 mingw-w64 project; parts (C) 2010 Lockless Inc. | MIT and BSD-style | `mingw-w64-winpthreads.txt` |

The GCC runtime DLLs are redistributed as unmodified binaries exactly as published inside the Vosk
jar. Their corresponding source is the GNU Compiler Collection 10 (https://gcc.gnu.org/) and
mingw-w64 8.0.0 (https://www.mingw-w64.org/).

## Vosk English model `vosk-model-small-en-us-0.15`

- Copyright 2020 Alpha Cephei Inc. — https://alphacephei.com/vosk/models
- License: Apache License 2.0 (as listed in the official model catalogue; `Apache-2.0.txt`)
- Redistributed unmodified as the official archive
  `https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip`, bundled at
  `totality/voice/models/vosk-model-small-en-us-0.15.zip`.
- SHA-256 `30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498`, 41,205,931 bytes.
  Alpha Cephei does not publish checksums; this value is Totality's own pin, recorded from the
  official download on 2026-09-25.
