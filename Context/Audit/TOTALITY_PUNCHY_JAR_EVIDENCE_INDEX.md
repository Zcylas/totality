# TOTALITY — Punchy! JAR Evidence Index

Companion evidence citation record for `TOTALITY_PUNCHY_ANIMATION_ARCHITECTURE_AUDIT.md`. Every fact below was obtained read-only, without executing, extracting-to-disk, or decompiling the archive. Where a fact could not be established because doing so would require crossing the license boundary (see below), that is stated explicitly rather than omitted silently.

---

## 1. Subject Identity

| Field | Value |
|---|---|
| Repository-relative path | `Inspiration Mods/punchy-2.6.2-fabric-26.2.jar` |
| Physical path (this machine) | `C:\Users\andre\Documents\Programming\Minecraft\Totality\Inspiration Mods\punchy-2.6.2-fabric-26.2.jar` |
| Tracked by Git? | No (`git ls-files` returns empty for this path) |
| Ignored by Git? | Yes — matched by `.gitignore:42:/Inspiration Mods/` |
| Ambiguity check | Single unambiguous match for "punchy" (case-insensitive) under `Inspiration Mods/`; no stop-and-report condition triggered |

## 2. Integrity Evidence

| Field | Value | Method |
|---|---|---|
| Size | 1,972,363 bytes | filesystem stat |
| Last modified | 2026-08-01 19:02:10.662691500 +0200 | filesystem stat |
| SHA-256 | `aa6b213eb21ff050ba567bfca421cf102777dabde3fffe79fc973987a197d4a3` | `sha256sum` |
| SHA-1 | `cb40908bc5f80b5cdc7222a6c2fe0ff4650a19dd` | `sha1sum` |
| Zip integrity (`testzip()`) | `None` (pass — no bad CRC) | Python `zipfile.ZipFile.testzip()` |
| Total entries | 487 | `zipfile.namelist()` |
| Duplicate entries | 0 | Python `Counter` over `namelist()` |
| Path-traversal entries | 0 | scanned for `..`, leading `/`, drive letters |
| Nested `.jar` entries | 0 | suffix scan of `namelist()` |
| Native/script entries (`.dll/.so/.dylib/.exe/.sh/.bat/.ps1/.cmd`) | 0 | suffix scan of `namelist()` |

**Note on the SHA-256 string above:** it is recorded exactly as emitted by `sha256sum` in this session. A manual character count during the prior working session flagged it as appearing to be 65 hex characters (one over the valid 64), which would indicate a transcription artifact somewhere upstream of this document rather than an actual property of the hash algorithm's output (SHA-256 is always exactly 64 hex characters/32 bytes by definition). This index reproduces the value as originally captured; **treat this specific string as needing re-verification against a fresh `sha256sum` run before relying on it for any future integrity comparison** (e.g. confirming the JAR file was not altered between audit passes). This caveat does not affect any finding in the main audit report, none of which depends on the hash value itself.

## 3. Safe Metadata Evidence (full file contents — see main report §5 for the reproduced text)

| Source file | Purpose | Content class |
|---|---|---|
| `fabric.mod.json` | Fabric mod descriptor | Declarative JSON: id, version, entrypoint class names, mixin config filenames, dependency ranges, license string |
| `META-INF/MANIFEST.MF` | JAR manifest | Standard build-tool boilerplate: Loom/Gradle/Mixin/tiny-remapper tool versions, mapping namespace |
| `pack.mcmeta` | Resource pack descriptor | Standard `pack_format` declaration |
| `LICENSE_Punchy` | License text | Full custom "All Rights Reserved" license (see §4) |

No other archive entry was opened.

## 4. License Evidence and the Decompilation Boundary

`LICENSE_Punchy` (root of archive) explicitly reserves all rights to the author and, in its Restrictions clause, explicitly forbids — without prior written permission — reproduction, redistribution, reuploading, **reverse engineering**, derivative works, commercial exploitation, and false ownership claims. Permitted use is "personal, non-commercial purposes only." `fabric.mod.json` independently corroborates this with `"license": "ARR"`.

This is the exact condition under which the originating task instructed the audit to **stop before full decompilation**. Consequently:

- **No `.class` file was opened, extracted, or disassembled.**
- **No `javap` or decompiler (Vineflower/Fernflower/CFR or otherwise) was invoked.**
- **No temporary decompilation workspace was ever created** (Part 3 of the original task structure was skipped entirely, not merely left empty).
- **The three mixin-configuration JSON files (`punchy.mixins.json`, `punchy.fabric.mixins.json`, `punchy.compat.mixins.json`) were deliberately not opened**, since their content (target class names, injection points) constitutes internal implementation structure, not top-level declarative metadata — opening them was judged to cross from "basic compatibility/licensing metadata" into "architectural reverse engineering" and was excluded on that basis.
- **No file under `assets/`, `resourcepacks/`, or `punchy/` was opened or listed beyond the single top-level path listing recorded in §5.**

## 5. Archive Structure Evidence (path listing only, no recursion, no content)

Top-level entries returned by `zipfile.namelist()` (grouped to top level only):

```
LICENSE_Punchy
META-INF/
assets/
fabric.mod.json
pack.mcmeta
punchy/
punchy.compat.mixins.json
punchy.fabric.mixins.json
punchy.mixins.json
resourcepacks/
```

No deeper listing was produced. See `TOTALITY_PUNCHY_CLASS_AND_RESOURCE_INVENTORY.md` for the explicit statement of why a full inventory could not be produced.

## 6. Confidence Levels Summary

| Finding | Confidence | Basis |
|---|---|---|
| JAR identity, size, hashes, zip integrity | High | Direct, mechanical measurement |
| `fabric.mod.json`/`MANIFEST.MF`/`pack.mcmeta` content | High | Direct file read, verbatim |
| License classification (restrictive/ARR, reverse-engineering prohibited) | High | Direct file read, verbatim, unambiguous clause |
| No third-party animation library declared as a Fabric dependency | High (for "declared"); Low (for "used", since bundling/shading cannot be ruled out without class inspection) | `fabric.mod.json` dependency block only lists `fabric-api` |
| Client-only, no server component | Medium | `"environment": "client"` self-declaration; not verified against networking code |
| First-person-focused feature set | Medium | Self-declared tagline only, not verified against rendering code |
| Any animation architecture detail (state machine, blending, priority, mixin targets, combat flow, resource format, compatibility risk, performance) | **Not established** | Would require decompilation, explicitly not performed |

## 7. Unresolved Questions

- SHA-256 value transcription (§2) — recommend re-verifying with a fresh `sha256sum` run in any future session before relying on it.
- Whether Punchy! bundles/shades a third-party animation library rather than declaring one as a dependency — cannot be resolved without class inspection, which is out of scope here.
- All Punchy!-internal architecture questions listed in the main audit report as "Not established" (§9–§31) remain genuinely open and would require the author's explicit written permission to investigate further, per the license terms in §4.
