# TOTALITY — Camera & Gallery V1 Commit Report

- **Date:** 2026-10-01.
- **Status:** Camera & Gallery V1, the Field Guide audit and the final corrections received final approval. Committed as **one local commit; not pushed**.
- **Commit message:** `feat: implement Camera and Gallery V1`.
- **Parent:** `b0943d0b5517e2b208be6c711f964b0a66e43b02` ("feat: redesign Basic Copper Phone phase 1").

This report is part of the commit it describes, so it cannot contain that commit's own hash. To retrieve it:

```
git log --format='%H %s' -1 -- Context/Audit/TOTALITY_CAMERA_GALLERY_V1_COMMIT_REPORT.md
```

(or `git log -1 --format='%H %P %s' --grep='implement Camera and Gallery V1'`, which also shows the parent).

Details are in the two documents committed alongside:
- `Context/Audit/TOTALITY_CAMERA_GALLERY_V1_IMPLEMENTATION_REPORT.md`: design, storage, input, tests, evidence, limitations; final corrections in §0; architectural rule in §2.1.
- `Context/Audit/TOTALITY_FIELD_GUIDE_CAMERA_SCAN_AUDIT.md`: the Field Guide study and the Scan/Codex recommendations.

---

## 1. What the commit contains

### Implemented, verified behaviour
- **Camera app (full-screen viewfinder).** Opened from the Phone dock. It is not a `Screen`, so the player keeps moving, sprinting, flying and aiming.
  - **Real photographs:** taken between the world pass and the GUI pass, at full window resolution, saved as lossless PNG. No HUD, hand, crosshair or Camera UI appears in them.
  - **Shutter:** automatic save, consecutive shots, flash → inset photo → flight into the Gallery shortcut, with reduced-motion and gentle-flash fallbacks.
  - **Zoom:** 0.5×/1×/2× presets plus smooth wheel zoom, done as a real projection change. The player's FOV setting is never written, and the view is restored on close.
  - **Input safety:** no attack, block breaking, item use or pick-block while the Camera is open.
  - **Controls:** hold Alt for the cursor; arrows switch mode and zoom; ESC goes back to the originating home page; TAB closes the Phone session.
  - **Scan mode:** reserved. Selectable, it shows a placeholder and does nothing.
- **Gallery app.** On a genuine secondary home page; the same Gallery is reached from the Camera shortcut.
  - **Grid:** thumbnails only (LRU cache), names, favourite marks, All/Favourites, and empty, loading and unavailable states.
  - **Full-screen viewer:** aspect ratio preserved, previous/next, metadata, favourite, double-click rename (Enter saves, click-outside saves, ESC cancels), delete with confirmation.
- **Storage.** Local only, under `<game dir>/totality/gallery/v1/<world|server key>/<player UUID>/`: one PNG plus one JSON sidecar per photo, and a thumbnail cache.
  - Stable ids.
  - Atomic writes.
  - Recovery from missing or corrupt files.
  - Isolation per world/server and player.
- **Development:**
  - full-screen capture including the HUD and the Camera controls (kept by decision);
  - `/totalityphone camera | gallery | camera-fullscreen`;
  - capture scene 65 (fresh/persist/isolated);
  - `Context/Tools/camera-gallery-v1/` (run script, contact-sheet tool).

### Architectural boundaries
- The photography system (`client/photo`) and the Camera app (`client/camera`) do no networking. Servers never store or transfer photographs (enforced by `CameraGalleryWiringTest`).
- **Architectural rule (implementation report §2.1):** *Codex discoveries must never depend on Camera photograph storage.* A future scan may optionally link a photo's stable id to a discovery, but deleting, losing or corrupting that photo must never remove or invalidate the discovery. This is documentation only; no Scan or Codex code exists.
- **Future extensions** (export, formats, albums, printing, email, server storage, higher-tier zoom, Scan/Codex) are **design intent only and not implemented**.

### Field Guide audit (included)
- **Verified from the actual 26.3 JAR (bytecode) and the 26.1 sources:**
  - Field Guide has **no image capture**; its Exposure-photograph bridge is stubbed.
  - Scanning is a spyglass/lens look-and-hold, with a +1/−2 dwell timer, a ray-trace that steps through plants, composite disambiguation, and server-side verification (`ScanVerifier`).
  - Progress is stored server-side per player.
- **Recommendations for Scan/Codex** are clearly marked as proposals; nothing was copied (the mod is MIT, but treated as study-only).

### Both final visual corrections (included)
1. **The rename input no longer overlaps the date/metadata.**
   - The viewer's header geometry lives in `PhotoViewerScreen.Layout`; the details line always starts below the 12-px edit field.
   - The double-click, click-outside-save and ESC-cancel behaviour is unchanged.
   - Verified by `PhotoViewerLayoutTest` (11 sizes) and in-game at every available GUI scale: the field ends at y 14–20 and the details start at y 17–68.
2. **The Scan placeholder has clear separation from the zoom controls and the mode selector.**
   - Its geometry lives in `CameraViewfinder.Layout`, centred between the top hint and the zoom pill.
   - Verified by `CameraViewfinderLayoutTest` (7 sizes) and in-game at every available GUI scale: gaps of **48–216 GUI px** to the zoom pill.

## 2. Files in the commit (42)

**Modified (9)**
```
src/main/java/zcylas/totality/TotalityClient.java                       (registers CameraClient; one import, one call)
src/main/java/zcylas/totality/client/hologram/dev/HologramCapture.java   (capture world name selectable; default unchanged)
src/main/java/zcylas/totality/client/phone/PhoneCapture.java             (registers capture scene 65)
src/main/java/zcylas/totality/client/phone/PhoneCaptureStates.java       (Phase 1 capture expectations: 2 home pages)
src/main/java/zcylas/totality/client/phone/PhoneDevCommand.java          (camera / gallery / camera-fullscreen options)
src/main/java/zcylas/totality/client/phone/PhoneInteractionCapture.java  (Phase 1 capture expectations: 2 / 4 pages)
src/main/java/zcylas/totality/client/phone/PhonePrototypeCapture.java    (Phase 1 capture expectations: 4 prototype pages)
src/main/java/zcylas/totality/screen/phone/PhoneAppGridScreen.java       (Camera dock action, Gallery secondary page, return page)
src/main/resources/totality.mixins.json                                  (five client camera mixins)
```

**Added: production (20)**
```
src/main/java/zcylas/totality/client/camera/CameraClient.java
src/main/java/zcylas/totality/client/camera/CameraMode.java
src/main/java/zcylas/totality/client/camera/CameraSession.java
src/main/java/zcylas/totality/client/camera/CameraViewfinder.java
src/main/java/zcylas/totality/client/camera/CameraZoom.java
src/main/java/zcylas/totality/client/camera/ShutterAnimation.java
src/main/java/zcylas/totality/client/photo/Gallery.java
src/main/java/zcylas/totality/client/photo/GalleryScope.java
src/main/java/zcylas/totality/client/photo/GalleryStore.java
src/main/java/zcylas/totality/client/photo/PhotoCapture.java
src/main/java/zcylas/totality/client/photo/PhotoMetadata.java
src/main/java/zcylas/totality/client/photo/PhotoTextures.java
src/main/java/zcylas/totality/mixin/client/camera/CameraCaptureMixin.java
src/main/java/zcylas/totality/mixin/client/camera/CameraFovMixin.java
src/main/java/zcylas/totality/mixin/client/camera/CameraHudMixin.java
src/main/java/zcylas/totality/mixin/client/camera/CameraKeyboardMixin.java
src/main/java/zcylas/totality/mixin/client/camera/CameraMouseMixin.java
src/main/java/zcylas/totality/screen/phone/GalleryScreen.java
src/main/java/zcylas/totality/screen/phone/PhoneOrigin.java
src/main/java/zcylas/totality/screen/phone/PhotoViewerScreen.java
```

**Added: development capture and tests (8)**
```
src/main/java/zcylas/totality/client/phone/CameraGalleryCapture.java     (capture scene 65; development only, inert in normal play)
src/test/java/zcylas/totality/client/camera/CameraViewfinderLayoutTest.java
src/test/java/zcylas/totality/client/camera/CameraZoomTest.java
src/test/java/zcylas/totality/client/camera/ShutterAnimationTest.java
src/test/java/zcylas/totality/client/photo/CameraGalleryWiringTest.java
src/test/java/zcylas/totality/client/photo/GalleryScopeTest.java
src/test/java/zcylas/totality/client/photo/GalleryStoreTest.java
src/test/java/zcylas/totality/screen/phone/PhotoViewerLayoutTest.java
```

**Added: tooling and documentation (5)**
```
Context/Tools/camera-gallery-v1/run_camera_capture.sh
Context/Tools/camera-gallery-v1/make_contact_sheets.py
Context/Audit/TOTALITY_FIELD_GUIDE_CAMERA_SCAN_AUDIT.md
Context/Audit/TOTALITY_CAMERA_GALLERY_V1_IMPLEMENTATION_REPORT.md
Context/Audit/TOTALITY_CAMERA_GALLERY_V1_COMMIT_REPORT.md
```

- **No new assets.** All Camera/Gallery graphics are drawn, and the Gallery icon follows the provisional abbreviation convention.
- **Staging:**
  - every file was staged explicitly, by path;
  - `src/` was clean at `b0943d0` when this work began, and every hunk of the 9 modified files was reviewed as Camera & Gallery V1 work.

## 3. Fresh verification (on the exact committed code)

| Verification | Result | Baseline |
|---|---|---|
| `./gradlew build --offline` | **BUILD SUCCESSFUL** | — |
| Forced full test run (`./gradlew test --rerun --offline`, results cleared first) | **237 classes, 2,405 tests, 0 failures, 0 errors, 2 skipped** (`VoiceCommandWavProbeTest`, `VoskWavRecognitionIntegrationTest`: opt-in, pre-existing) | 2,405 / 0 / 2 |
| Phone Phase 1 unit tests | `PhonePhase1PrototypeTest` 9/9, `PhoneCodexAppTest` 3/3, `PhoneHomeGeometryTest` 5/5, `PhoneDeviceLayoutTest` 3/3, `EntitlementWiringSourceRegressionTest` 10/10 | pass |
| Real client 1080p (window 1920×1012), scenes 45 + 64 (Phone Phase 1 regression) + 65 (`run_camera_capture.sh commit1080 1920 1080 fresh default`) | exit 0, **196/196 PASS**, 0 FAIL | 196/196 |
| Relaunch persistence (`commit1080 … persist`) | exit 0, **2/2 PASS** (14 photos with identical ids, names and favourites; shortcut thumbnail shown) | 2/2 |
| Other player, same world (`isolated OtherTester`, distinct `--uuid`) | exit 0, **2/2 PASS** (separate empty Gallery; the first untouched) | 2/2 |
| Other world, same player (`isolated default CameraWorldB`) | exit 0, **2/2 PASS** | 2/2 |
| Real client 720p (1280×720), scenes 45 + 64 + 65 (`commit720 … fresh default`) | exit 0, **187/187 PASS**, 0 FAIL (GUI 4 is not available at 720p; skipped automatically) | 187/187 |

Fresh measurements from these runs:
- **Clean photo:** the photo equals the render target (1920×1012; 1280×720), and the shutter-ring white fraction is **0.0**. In the development full-screen capture it is **1.0**.
- **Scan clearance from the zoom pill:**
  - 1080p, GUI 4/3/2/1: **54 / 97 / 108 / 216 GUI px**;
  - 720p, GUI 3/2/1: **48 / 108 / 143 GUI px**.
- **Rename field vs the details line:**
  - every GUI scale ends the field 3 px (GUI 4/3) or more above the details line;
  - e.g. GUI 1 at 1080p: the field spans y 8..20 and the details start at y 68.

## 4. Known limitations and deferred work

**Not verified:**
- **Shader packs (Iris):** not tested; none is installed in this environment.
- **Real dedicated multiplayer server:** not tested. Server-scope isolation is unit-tested only.

**Known limitations:**
1. Gallery identity follows the world save folder name or the normalised server address.
2. While the Camera is open:
   - dynamic FOV effects (sprint, speed, underwater) are not applied;
   - name tags are hidden in photos (F1 semantics).
3. Pixel readback conversion runs on the render thread (vanilla `Screenshot.takeScreenshot`, as with F2). Encoding and file I/O are off-thread.
4. At GUI 2 and GUI 1, the rename field is vanilla's fixed-size `EditBox`. It looks small beside the enlarged name text but doesn't overlap (open decision 11 in the implementation report).
5. The arrow-key shortcuts are fixed (Alt is rebindable as the Radial Modifier). There is no drag-scrolling in the Gallery grid. Rename is in the viewer only. Icons are provisional and the shutter uses a vanilla sound.
6. The dev client's player name is fixed by `build.gradle`, so identity tests use `--uuid`.

**Deferred** (not started; awaiting a separate request): Scan Phase 2, Codex, export, format settings, albums, printing, email attachments, server storage, custom Camera sounds and final icons. The other open decisions are in implementation report §12.

## 5. Deliberately not in the commit

- The pre-existing, unrelated working-tree changes, left untouched:
  - the deleted `Context/References/TOTALITY_MASTER_v3.6_SECTIONS_26L_26M_EXTRACT.md`;
  - the untracked `Context/References/Anime Screenshots/`, `Mob Hud V1/`, `Notification API V2/` and `Other/`. `Other/` includes the Camera & Gallery concept image `Neon Minecraft Camera & Gallery Blueprint.png`, used only as a reference;
  - the untracked `Context/References/STEFAN_TOTALITY_MASTER_REFERENCE_v3.8_final_audited.docx`.
- `Inspiration Mods/fieldguide-1.20.0+26.3-fabric.jar` and `Inspiration Mods/Field-Guide-26.1/`: references only, and git-ignored (`.gitignore` line 42, `/Inspiration Mods/`).
- The review bundle `Context/Audit/Review Bundles/TOTALITY_CAMERA_GALLERY_V1_REVIEW.zip` (git-ignored).
- Capture game directories, screenshots, logs and photographs under `build/`.
- `build.gradle`: unchanged.

**Not pushed.** Scan Phase 2 and Codex are not started.
