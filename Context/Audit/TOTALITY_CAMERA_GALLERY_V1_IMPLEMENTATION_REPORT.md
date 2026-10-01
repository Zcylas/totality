# TOTALITY — Camera & Gallery V1 Implementation Report

**Status:** **Approved.** V1, the Field Guide audit and the final corrections (§0) received final approval and are committed in one local commit (not pushed). See `Context/Audit/TOTALITY_CAMERA_GALLERY_V1_COMMIT_REPORT.md` for the commit's contents and its fresh verification.

- **Base:** `b0943d0b5517e2b208be6c711f964b0a66e43b02` (Basic Copper Phone Phase 1).
- **Date:** 2026-10-01.
- **Companion audit:** `Context/Audit/TOTALITY_FIELD_GUIDE_CAMERA_SCAN_AUDIT.md`. It was written before the implementation, and its findings shaped §4 (photography) and §13 (Scan recommendations).
- **Review bundle:** `Context/Audit/Review Bundles/TOTALITY_CAMERA_GALLERY_V1_REVIEW.zip`.
- **Not done, by design:** Scan mode functionality, Codex, export, format settings, albums, printing, email, server storage.

Every screenshot referenced here is a **real Minecraft client screenshot**, taken with `Screenshot.grab` by the automated capture harness. The contact sheets are grids of those real screenshots, assembled with Python (`make_contact_sheets.py`), which adds only file-name labels. The visual reference board in `Context/References/Other/` was used as inspiration only; no concept art appears in the evidence.

---

## 0. Final corrections (2026-10-01)

| # | Request | Change | Verification |
|---|---|---|---|
| 1 | The rename input must not overlap the date/metadata | `PhotoViewerScreen.Layout` now owns the header geometry: `nameFieldX/Y/W`, the fixed `NAME_FIELD_H` (12, vanilla `EditBox`) and `detailsY() = max(17u, field bottom + 3)`. Drawing and the edit field both use it. The double-click rename, click-outside-save, ESC-cancel and the Gallery layout are unchanged | Unit: `PhotoViewerLayoutTest` (11 window/GUI sizes). Real client at **every available GUI scale** (1080p: 4/3/2/1; 720p: 3/2/1): double-click → field shown → `field bottom + 2 ≤ details top` → ESC cancels with the name unchanged. Screenshots `cam_72b_rename_gui*` |
| 2 | Clear separation between the Scan placeholder, the mode selector and the zoom controls | `CameraViewfinder.Layout` now owns the Scan placeholder (`scanCenterY/Half/PanelTop/PanelH/Top/Bottom`), centred between the top hint and the zoom pill (`chipPillTop`). Drawing uses the same numbers. Scan remains a placeholder with no functionality | Unit: `CameraViewfinderLayoutTest.scanPlaceholderAndModeSelectorKeepClearOfTheZoomControls` (7 sizes). Real client at every available GUI scale: Scan selected, `scan bottom + 4u ≤ zoom pill top` (measured gaps **48–216 GUI px**), clear of the top hint, mode labels clearly below the pill. Screenshots `cam_70b_scan_gui*` |
| 3 | The development capture keeps the Camera controls | **Unchanged, by your decision.** Still verified (white fraction 1.0 in the development capture) | Scene 65 |

Both fixes were drawn from the screenshots of the earlier runs. The previous delivery had already moved the elements, but only now is their geometry shared, unit-tested and checked in-game at every GUI scale.

**Observation, not changed** (outside the requested scope): at GUI 2 and GUI 1 the rename field is vanilla's fixed-size `EditBox` (12 GUI px tall). It looks small next to the enlarged name text there (`cam_72b_rename_gui2`, `cam_72b_rename_gui1`), though its text is ordinary vanilla size for those scales and nothing overlaps. If you prefer, a scaled field can follow.

**Re-verification on the corrected code:**
- `./gradlew build` successful.
- Full test run: **237 classes, 2,405 tests, 0 failures, 0 errors, 2 skipped** (opt-in voice tests).
- Real client:
  - `corr1080` fresh (Phone 45 + 64 + Camera 65): **196/196 PASS**;
  - `corr1080` persist: **2/2**;
  - `corr720` fresh (45 + 64 + 65): **187/187**;
  - 0 FAIL.
- The world/player isolation runs (`final1080`, unaffected by these UI-only changes) remain **2/2 + 2/2** and their logs stay in the bundle.

---

## 1. Summary

| Requirement | Result |
|---|---|
| Full-screen viewfinder opened from the Phone | Camera dock icon → viewfinder. It is not a `Screen`, so play continues underneath |
| Real photographs, clean of HUD, hand, crosshair and Camera UI | GPU readback between the world pass and the GUI pass, at full window resolution, saved as lossless PNG |
| Automatic save; stays in Camera; consecutive shots | Yes: 6-shot burst verified, every shot saved, names Photo 1..N |
| Development full-screen capture | `-Dtotality.camera.fullScreenCapture=true` or `/totalityphone camera-fullscreen` (development environment only) |
| Move, sprint, fly, aim; no attack, break, use or pick | Verified with the real input entry points |
| Hold Alt for the cursor; keyboard mode/zoom; mouse on controls | Yes |
| ESC = back, TAB = close the Phone session | Yes |
| 0.5× / 1× / 2× presets, smooth wheel zoom, indicator | Yes. 0.5× is a genuinely wider projection; FOV restored on close |
| Shutter animation (flash → photo → flies into the thumbnail) | Yes, plus reduced-motion and gentle-flash fallbacks |
| Scan mode reserved | Selectable. It shows "Scan — Coming with the Codex" and takes nothing |
| Gallery on a genuine secondary home page; Camera shortcut; one shared Gallery | Yes |
| Gallery inside the Phone: grid, names, favourites, empty and loading states, thumbnail cache | Yes |
| Full-screen viewer: previous/next, metadata, favourite, double-click rename, confirmed delete, back | Yes |
| Local storage isolated per world/server and player; survives restart; damaged-file handling | Yes, verified across real relaunches |

**Verification on the final code:**
- `./gradlew build` successful.
- Full test run: **237 classes, 2,405 tests, 0 failures, 0 errors, 2 skipped** (the pre-existing opt-in voice tests). That is 40 new tests on top of the Phase 1 baseline of 2,365.
- Real client after the corrections: **385/385 checks PASS** (`corr1080` 196 + persist 2, `corr720` 187), plus the isolation runs 4/4, 0 FAIL (§0, §9).
- **Not verified:** shader packs and a real multiplayer server (§11).

---

## 2. Architecture and data ownership

```
client/photo/   the shared photography system (no UI, no networking)
  PhotoCapture      takes a shot inside the next rendered frame; GPU preview + one readback; writes PNG/thumbnail/metadata
  Gallery           the current world/server+player's photos: main-thread model, single I/O thread, lifecycle
  GalleryStore      disk format (pure Java + Gson; unit-tested): photos/<id>.png + photos/<id>.json, thumbnails/<id>.png
  GalleryScope      which Gallery: world save folder or server address, plus player UUID -> directory
  PhotoMetadata     stable id, display name, time, size, favourite, mode, zoom, FOV, capture kind, place; open 'extra'
  PhotoTextures     GPU textures: LRU thumbnail cache (72) + ONE full-size photo for the viewer; deferred release

client/camera/  the Camera app
  CameraSession     open/close/suspend, input routing, shutter, lifecycle (death, disconnect, world change)
  CameraViewfinder  draws the viewfinder (HUD layer) and owns its layout/hit-testing
  CameraZoom        mode-agnostic zoom model (presets per phone tier, wheel, smoothing, FOV maths)
  CameraMode        NORMAL (available), SCAN (reserved)
  ShutterAnimation  pure timeline geometry (flash, shrink, fly, crop)
  CameraClient      registers tick/disconnect/level-change hooks

screen/phone/   Phone-side UI
  GalleryScreen     the Gallery app inside the Phone frame
  PhotoViewerScreen full-screen viewer
  PhoneOrigin       "opened from": a home page or the Camera -> back() / closeAll()

mixin/client/camera/  CameraCapture (GameRenderer), CameraFov (Camera), CameraHud (Hud), CameraKeyboard, CameraMouse
```

**Ownership**

- **Photographs belong to the local Gallery** (files under the game directory). Servers never store or transfer them. Nothing in `client/photo` or `client/camera` touches networking; `CameraGalleryWiringTest` enforces this.
- **One Gallery per scope.** `Gallery.current()` resolves the scope every time it is asked. The Camera shortcut, the Gallery app and the viewer all ask the same object, so there is no duplicate storage and no second instance.
- **The Camera owns only transient state:** zoom, mode, cursor, animation. Closing it by any path restores everything it changed: the perspective, and the FOV (which it never writes to the options).
- **Future extensions plug in without replacing V1.** *(Design intent only: none of these is implemented or verified in V1.)*

  | Future feature | Where it plugs in |
  |---|---|
  | Export | Copy `imagePath(meta)` |
  | PNG/JPEG setting | `PhotoMetadata.format` (already validated `png\|jpg`) |
  | Albums | A new metadata field, or an `albums/` index; ids are stable |
  | Printing / email attachments | Reference by id |
  | Server storage | A second store implementation behind the same `Gallery` model |
  | Scan / Codex | `mode = "scan"`, plus a discovery reference in `extra`; the discovery itself lives server-side (audit §6.3) |
  | Higher tiers | A new `CameraZoom.Range` (unit test `futureTiersCanWidenTheRange`) |

### 2.1 Architectural rule: Codex discoveries never depend on Camera photograph storage

> **Codex discoveries must never depend on Camera photograph storage.**

- A future Scan **may optionally** save an ordinary photograph and associate its stable photo id with a discovery. For example, the photo's metadata `extra` map could hold the discovery id, or the discovery could hold the photo id as a weak reference.
- **Deleting, losing or corrupting that photograph must never remove or invalidate the underlying Codex discovery.**
  - The discovery is recorded and owned server-side, per player, in world data.
  - The photograph is a local, optional, client-side file that a server never stores or needs.
  - Any link between them is a weak, one-way reference that may dangle. Code reading it must treat a missing, deleted or unreadable photo as "no photo" (show a placeholder) and leave the discovery intact.
- **Status in V1:**
  - *Documentation only.* No Scan, Codex or discovery code exists; Scan mode is a placeholder.
  - V1 already satisfies the rule trivially: Gallery deletion (`Gallery.delete` → `GalleryStore.delete`) removes only the photo's own image, metadata and thumbnail files, and nothing else in the game references photo ids.

## 3. Design decisions

1. **Viewfinder = HUD overlay, not a `Screen`.** A `Screen` stops movement and frees the cursor, which would break "move, sprint, fly and aim while photographing". The Camera intercepts only what conflicts: mouse buttons, the wheel, ESC, the Phone key, the arrows and F5 (perspective), each in a small mixin and only while the Camera is live.
2. **The capture point is between the world pass and the GUI pass**, not a screenshot of the final frame. Everything the world renders, including the vanilla post effect and a shader pack's final composite (which run before the GUI), is in the photo; anything drawn as GUI is not.
3. **The Camera counts as "HUD hidden" (F1 semantics).**
   - This is what keeps the **held item, block outline and name tags** out of photos. Vanilla checks `Hud.isHidden()` in the world pass for these.
   - World-view effects that F1 also keeps stay in: underwater tint, burning, in-wall.
4. **Zoom is a projection change through `Camera.calculateFov`.**
   - `tan(fov/2)` scales by 1/zoom, the same relationship a phone's 0.5× ultra-wide has to its 1× lens.
   - The player's FOV setting is the 1× reference.
   - Dynamic FOV effects (sprint, speed, underwater) are **not** applied while the Camera is open, so framing stays stable while running.
5. **Mouse-look slows proportionally when magnified** (×½ at 2×, like the spyglass) and never speeds up when wide.
6. **Alt** is Totality's existing modifier key mapping (`key.totality.radial_modifier`, default Left Alt, rebindable), read physically each frame.
   - Releasing it re-grabs the mouse through vanilla's `grabMouse`, which re-centres and ignores the first motion: no jump, no click.
   - **Arrow keys** are not bound by vanilla or Totality: ←/→ switch mode (← = swipe left = towards Scan), ↑/↓ step the zoom presets. They are fixed context keys, active only while the Camera is open.
7. **Shutter feedback without double processing.**
   - The frame is copied **GPU-to-GPU** into a preview texture for the animation, with no CPU work.
   - It is read back **once**. That one `NativeImage` is encoded to PNG *and* downscaled to the 256 px thumbnail on the I/O thread, and the thumbnail is uploaded straight into the cache.
   - The preview is released once the saved thumbnail exists.
8. **One sidecar per photo instead of a single index file.**
   - A damaged file can only ever affect one photo.
   - A pending write can't race an index rewrite.
   - The directory can be repaired by hand.
9. **The Gallery page is a real second home page** to the right of the main page, so in normal play the page indicator (with its house marker) is now visible. The main page's twelve apps and the four favourites are untouched (verified in-game and by tests).
10. **Gallery → viewer browses the list it came from.** Opening from Favourites browses favourites only.
11. **Rename is in the viewer** (double-click the name), as in the reference board. Enter saves; ESC cancels; clicking elsewhere saves (desktop convention); a blank name is never saved.

## 4. Photography and rendering

| Step | Where | Thread |
|---|---|---|
| Shutter click → `PhotoCapture.request(shot)` | `CameraSession.shutter` | render/main |
| `GameRenderer.renderLevel` TAIL → mark "world rendered" | `CameraCaptureMixin` | render |
| `GameRenderer.render` at `FogRenderer.endFrame()` (after the world, entity outlines and post chain; before the GUI) → `PhotoCapture.beforeGui(mainRenderTarget)` | `CameraCaptureMixin` | render |
| `copyTextureToTexture` into a preview texture (`COPY_DST\|TEXTURE_BINDING`, same format) → animation | `PhotoCapture.take` | render (GPU) |
| `Screenshot.takeScreenshot(target, …)`: vanilla's async readback (fence callback), alpha forced opaque, rows flipped | vanilla | render |
| Write `<id>.png.tmp` → atomic move; thumbnail (`resizeSubRectTo`, STB linear) → `thumbnails/<id>.png`; metadata sidecar | `PhotoCapture.save` | **Gallery I/O** |
| `Gallery.added(meta)`; thumbnail upload; shortcut switches from the preview to the thumbnail | `mc.execute` | render/main |
| Development full-screen: the same, but at `GameRenderer.render` TAIL (after the GUI) | `PhotoCapture.endOfFrame` | render |

**Resolution.** The photo is the main render target at **full window resolution**: 1880×1052 or 1920×1012 in the 1080p runs (the Wayland compositor sizes the window) and 1280×720 in the 720p run. It is never downscaled, and the PNG is lossless RGBA.

**Proof of cleanliness** (automated, real client):
- The fraction of pure-white pixels on the shutter ring's circle is **0.0** in a clean photo.
- It is **1.0** in a development full-screen capture of the same view.

**Shaders.** **Not tested**: no shader pack or Iris is installed in this development environment. Iris writes its final composite into the main target at the end of the level render, before the GUI, so the capture point *should* include shader output, but **this is unverified**. Please treat shader compatibility as open (§11).

**Render-thread cost.** Vanilla's readback callback converts pixels row by row on the render thread, the same cost as an F2 screenshot. PNG encoding, thumbnail scaling and every file write happen off-thread.

## 5. Storage layout and metadata

```
<game dir>/totality/gallery/v1/
  world-<save folder, sanitised>-<crc32 of exact name>/<player UUID>/     (singleplayer, by save folder)
  server-<address, sanitised>-<crc32 of normalised address>/<player UUID>/ (multiplayer; ":25565" and case ignored)
      context.json           kind, source (folder/address), player — informational
      photos/<id>.png        e.g. photos/20261001-194831-424-500c.png
      photos/<id>.json       metadata sidecar (below)
      thumbnails/<id>.png    256 px wide cache; deletable, regenerated on demand
```

- The id format is `yyyyMMdd-HHmmss-SSS-xxxx`: local capture time plus 4 random hex digits. It is validated by regex before any path is built, so a hostile file or sidecar can't point outside the gallery (tested).
- Example sidecar from the real run:

```json
{
  "schema": 1,
  "id": "20261001-200717-835-6b6d",
  "number": 13,
  "name": "Sunrise Peaks",
  "capturedAt": 1790878037835,
  "format": "png",
  "width": 1880,
  "height": 1052,
  "favorite": true,
  "mode": "normal",
  "zoom": 1.0,
  "fov": 70.0,
  "capture": "clean",
  "dimension": "minecraft:overworld",
  "position": { "x": 7.5, "y": 93.35, "z": -0.11, "yaw": 180.0, "pitch": 0.0 },
  "extra": {}
}
```
(Renamed in the viewer during the run: the id and file names never changed.) Unknown fields are ignored on read and missing optional fields get defaults, so later versions can add fields without a migration.

**Graceful handling** (`GalleryStore.load`, unit-tested):

| Situation | Behaviour |
|---|---|
| PNG without a sidecar | Recovered as "Recovered photo N", with its size read from the PNG header, and the sidecar rewritten |
| Unreadable sidecar | Kept aside as `<id>.json.corrupt`; the photo is recovered |
| Sidecar without its PNG | Listed as **missing**: the grid shows "missing", the viewer says so and the entry can be deleted |
| Foreign files or bad names | Ignored |
| A sidecar claiming another photo's id | Rejected, so no duplicates |
| Unreadable gallery directory | The Gallery shows "Gallery unavailable" instead of failing |

- **Writes are atomic** (temporary file, then move).
- **Lifecycle:**
  - Loading is asynchronous ("Loading…").
  - On disconnect, the open Gallery and its GPU textures are dropped; the files stay.
  - Late texture loads from a previous world are discarded by a generation counter.
  - A photo whose save completes after a disconnect is still written to the scope it was taken in.

## 6. Input and navigation

| Context | Input | Effect |
|---|---|---|
| Viewfinder | Left click | Shutter (Normal); a message in Scan |
| | Right/middle click, holding left | Nothing (consumed) |
| | Wheel | Smooth zoom (6 notches = ×2) |
| | ↑ / ↓ | Next / previous zoom preset |
| | ← / → | Mode: ← towards Scan, → towards Normal |
| | Hold Alt | Cursor appears. Click the chips, mode labels, shutter, Gallery shortcut or "‹ Phone"; drag sideways across the view to switch mode |
| | ESC | Back to the home page the Camera was opened from |
| | TAB (Phone key) | Close the whole Phone session |
| | F5 | Ignored (the Camera is first person) |
| Gallery (in the Phone) | Click a tab or photo, wheel, arrows + Enter | Browse, open |
| | ESC / header | Back to the origin: the Camera (resumes, zoom kept) or the home page it came from |
| | TAB | Close everything (ends a suspended Camera too) |
| Viewer | ←/→, wheel, arrows | Previous / next |
| | Double-click the name | Rename (Enter saves, ESC cancels) |
| | ★, Delete (or the Delete key) → confirm | Favourite; delete with confirmation |
| | ESC / ✕ | Back to the Gallery |
| | TAB | Close everything |

**Camera lifecycle and clean-up.** The Camera closes on:
- death;
- disconnect (no screen changes during teardown);
- a dimension or world change (`AFTER_CLIENT_LEVEL_CHANGE`);
- TAB.

Each restores the perspective and normal FOV and releases the preview textures. Textures are always closed one tick after their last use, never during the frame that draws them.

**Other screens while the Camera is open** (chat, inventory, pause): the viewfinder stays drawn underneath, and its input pauses until they close.

## 7. Gallery

- **Grid:**
  - 3 columns of square, centre-cropped thumbnails, with the display name (ellipsised) under each and a gold ★ on favourites.
  - A thin scrollbar on overflow; mouse-wheel and keyboard focus.
  - **All / ★ Favourites** tabs with counts.
  - States: "No photos yet / Take one with the Camera.", "No favourites yet", "Loading…", "Gallery unavailable".
- **Thumbnails:**
  - 256 px wide PNGs, made from the capture itself or regenerated from the photo when the cache file is missing.
  - LRU of 72 GPU textures; the grid never loads a full-size photo (verified: 13 cached thumbnails, no full texture while browsing).
- **Viewer:**
  - One full-size texture at a time. The thumbnail is shown stretched while it loads.
  - The photo is letterboxed at its own aspect ratio (verified at every GUI scale).
  - Shows name, date and time, resolution and zoom, and "full screen (dev)" on development captures.
  - Previous/next arrows, position "N / M", ★, Delete and ✕.

## 8. Files

**Added: production (`src/main/java/zcylas/totality/`)**

| File | Purpose |
|---|---|
| `client/photo/PhotoCapture.java` | Capture pipeline: render hooks, GPU preview copy, single readback, PNG + thumbnail + metadata on the I/O thread; development full-screen toggle |
| `client/photo/Gallery.java` | The current Gallery: scope resolution, async load, main-thread model, single I/O thread, rename/favourite/delete, close on disconnect |
| `client/photo/GalleryStore.java` | Disk format and recovery (pure Java) |
| `client/photo/GalleryScope.java` | World/server + player → directory key (sanitised + CRC32) |
| `client/photo/PhotoMetadata.java` | Metadata record, JSON schema 1, validation, name cleaning |
| `client/photo/PhotoTextures.java` | Thumbnail LRU, single full-size texture, deferred GPU release |
| `client/camera/CameraSession.java` | Camera app state, input, lifecycle |
| `client/camera/CameraViewfinder.java` | Viewfinder drawing, layout and hit-testing |
| `client/camera/CameraZoom.java` | Zoom model and FOV maths |
| `client/camera/CameraMode.java` | Normal / Scan (reserved) |
| `client/camera/ShutterAnimation.java` | Shutter timeline (pure) |
| `client/camera/CameraClient.java` | Event registration |
| `mixin/client/camera/CameraCaptureMixin.java` | `GameRenderer`: world-rendered flag, before-GUI and end-of-frame capture points |
| `mixin/client/camera/CameraFovMixin.java` | `Camera.calculateFov`: zoom |
| `mixin/client/camera/CameraHudMixin.java` | `Hud`: viewfinder instead of the HUD; `isHidden()` true while the Camera is open |
| `mixin/client/camera/CameraKeyboardMixin.java` | `KeyboardHandler.keyPress`: ESC/TAB/arrows/F5 while the Camera is open |
| `mixin/client/camera/CameraMouseMixin.java` | `MouseHandler`: buttons, wheel, look sensitivity |
| `screen/phone/GalleryScreen.java` | Gallery app |
| `screen/phone/PhotoViewerScreen.java` | Full-screen viewer |
| `screen/phone/PhoneOrigin.java` | Return navigation |

**Added: development and verification**

| File | Purpose |
|---|---|
| `client/phone/CameraGalleryCapture.java` | Real-client capture scene 65 (`fresh` / `persist` / `isolated` modes); dev only, inert in normal play |
| `src/test/java/zcylas/totality/client/camera/CameraZoomTest.java` | 9 tests |
| `src/test/java/zcylas/totality/client/camera/ShutterAnimationTest.java` | 3 tests |
| `src/test/java/zcylas/totality/client/camera/CameraViewfinderLayoutTest.java` | 3 tests (7 window/GUI sizes), including Scan/mode/zoom separation |
| `src/test/java/zcylas/totality/screen/phone/PhotoViewerLayoutTest.java` | 1 test (11 sizes): the rename field never overlaps the details line |
| `src/test/java/zcylas/totality/client/photo/GalleryStoreTest.java` | 12 tests |
| `src/test/java/zcylas/totality/client/photo/GalleryScopeTest.java` | 5 tests |
| `src/test/java/zcylas/totality/client/photo/CameraGalleryWiringTest.java` | 7 source-level tests |
| `Context/Tools/camera-gallery-v1/run_camera_capture.sh` | Real-client run script (fresh/persist/isolated; `--uuid` for a different identity) |
| `Context/Tools/camera-gallery-v1/make_contact_sheets.py` | Contact sheets from real screenshots |
| `Context/Audit/TOTALITY_FIELD_GUIDE_CAMERA_SCAN_AUDIT.md`, this report | Reports |

**Modified**

| File | Change |
|---|---|
| `screen/phone/PhoneAppGridScreen.java` | The Camera dock entry gets its action (`CameraSession.open(frame, page)`). New secondary page with the Gallery app. A constructor overload opens on a given page (return navigation). The class doc is updated. Nothing else changed |
| `client/phone/PhoneDevCommand.java` | `/totalityphone camera`, `gallery`, `camera-fullscreen` (development only) |
| `client/phone/PhoneCapture.java` | Registers capture scene 65 |
| `client/phone/PhoneCaptureStates.java`, `PhonePrototypeCapture.java`, `PhoneInteractionCapture.java` | Phase 1 capture **expectations** updated for the new page: normal play has 2 pages (was 1), the prototype has 4 (was 3), the right development page index is 3 (was 2), and keyboard Right past the last column now turns to the Gallery page and Left returns. No behaviour checks were removed |
| `client/hologram/dev/HologramCapture.java` | The capture world name can be chosen (`-Dtotality.hologram.capture.world`) for world-isolation runs; default unchanged |
| `TotalityClient.java` | `CameraClient.register();` (one import, one call) |
| `resources/totality.mixins.json` | The five client mixins |

- **New assets:** none. All viewfinder graphics are drawn, and the Gallery icon uses the provisional abbreviation convention ("Ga").
- **Preserved, untouched:** the pre-existing reference-folder changes (`Context/References/...`: the deleted v3.6 extract; untracked `Anime Screenshots/`, `Mob Hud V1/`, `Notification API V2/`, `Other/`, v3.8 docx) and `build.gradle`.


## 9. Tests and real-client results

> The tables below describe the `final1080`/`final720` delivery runs. The corrected code was re-verified with the same scenes plus the new per-GUI-scale rename and Scan checks: `corr1080` 196/196, persist 2/2, `corr720` 187/187, and 2,405 tests (§0). The bundle's contact sheets and representative screenshots now come from the `corr` runs.

### 9.1 Automated

- **Build:** `./gradlew build --offline` → **BUILD SUCCESSFUL**.
- **Full run:** `./gradlew test --rerun --offline` with results cleared first → **236 classes, 2,403 tests, 0 failures, 0 errors, 2 skipped** (`VoiceCommandWavProbeTest`, `VoskWavRecognitionIntegrationTest`: opt-in, pre-existing).
- **During development:**
  - `EntitlementWiringSourceRegressionTest` caught `PhotoTextures.forget(id)`, because its "no direct unlock/forget writes" pattern matched it. I renamed the method to `discard` rather than weaken the guard.
  - The other 38 new tests:

| Suite | What it proves |
|---|---|
| `CameraZoomTest` (9) | Presets 0.5/1/2 and stepping; multiplicative, clamped wheel; smooth monotone arrival (not a jump, under 40 frames); `fov` = 2·atan(tan(base/2)/zoom), 0.5× > 100° from 70°, the 165° cap; sensitivity ×½ at 2× and never faster; indicator labels; log-nearest preset; future tiers can widen the range |
| `ShutterAnimationTest` (3) | Immediate flash, inset, flight towards the bottom-left, landing exactly on the thumbnail with a square crop, under 0.7 s; reduced motion skips the flight; crop UVs |
| `CameraViewfinderLayoutTest` (2) | Every control hit-tests to itself and stays inside the window at 7 window/GUI sizes; the view itself is no control; physical size of 3–4 screen px per unit at every GUI scale |
| `GalleryStoreTest` (12) | Restart persistence; rename/favourite change only metadata; delete removes image, metadata and thumbnail; orphan recovery; corrupt sidecar set aside; missing image reported; foreign files and path-traversal ids ignored or rejected; no duplicate ids; scope isolation; JSON round trip including `extra`; name cleaning and format validation |
| `GalleryScopeTest` (5) | World / server / player separation; sanitised-alike names stay distinct; address normalisation; hostile names stay under the root |
| `CameraGalleryWiringTest` (7) | Mixins registered; no networking in `client/photo` or `client/camera`; persistent game-dir storage keyed by world/server and profile UUID; development capture gated; Scan reserved, with no invented discoveries; the Camera is not a `Screen` and never writes the FOV option; the home screen's 12 apps and dock are unchanged and the Gallery page follows the main page |

Existing Phone tests (`PhonePhase1PrototypeTest`, `PhoneCodexAppTest`, `PhoneHomeGeometryTest`, `PhoneDeviceLayoutTest`) pass unchanged.

### 9.2 Real client (final code)

Script: `Context/Tools/camera-gallery-v1/run_camera_capture.sh`. Every input goes through Minecraft's real entry points, called by reflection because GLFW can't be driven headless: `KeyboardHandler.keyPress`, `MouseHandler.onButton/onScroll/turnPlayer`. So vanilla's routing **and** the Camera mixins are what's tested.

| Launch | Scenes | Result |
|---|---|---|
| `final1080 fresh` (window 1880×1052) | 45 + 64 (Phone Phase 1 regression) + 65 (Camera & Gallery) | **184 / 184 PASS** |
| `final1080 persist` (relaunch, same world and player) | 65 | **2 / 2 PASS**: 14 photos with identical ids, names and favourites; the shortcut shows the latest thumbnail |
| `final1080 isolated OtherTester` (same world, other UUID) | 65 | **2 / 2 PASS**: separate empty Gallery `…/cee8988a-…`; the first one untouched |
| `final1080 isolated` world `CameraWorldB` (same player) | 65 | **2 / 2 PASS**: separate empty Gallery `world-cameraworldb-4f7a7c9f/…`; the first one untouched |
| `final720 fresh` (1280×720) | 45 + 64 + 65 | **178 / 178 PASS** (GUI 4 is not available at 720p and was skipped automatically) |

**Scene 65 checks, all PASS at both resolutions:**

- **Home and Camera:**
  - the main page plus the secondary page, opening on the main page;
  - the favourites and the 12 main apps unchanged;
  - the Camera dock icon opens the viewfinder with no screen open, remembering its origin page;
  - at 1× the FOV equals the setting;
  - the HUD is hidden.
- **Zoom:**
  - ↑ = 2×, with an in-between value rendered during the transition;
  - 2× and 0.5× FOVs match the formula (0.5× ≈ 109° from 70°);
  - 3 and 9 wheel notches give 0.71× and 1.41×, and the indicator reads "1.4×";
  - ↓ returns to 1×;
  - the hotbar slot is unchanged by the wheel.
- **Photography:**
  - one click = one photo;
  - the PNG is exactly the render-target size (1880×1052 or 1920×1012; 1280×720);
  - the shutter ring's white fraction is **0.0** in the photo (clean);
  - metadata is correct; the thumbnail is cached from the capture.
- **Safety:**
  - the target stone is **not broken** by clicks (creative breaks instantly on attack);
  - no swing;
  - holding left: one photo, no mining;
  - right click with stone in hand places nothing;
  - no item use;
  - a pig in the crosshair: 3 clicks, **full health, no hurt time, no attacker**.
- **Consecutive:** a 6-shot burst → 8 photos, 8 distinct ids, names Photo 1..8, still in the Camera.
- **Movement:**
  - walked and sprinted **5.4 blocks** while photographing;
  - **flew up 9.2 blocks** (real double-tap jump) and photographed in flight; the photo's recorded y is higher;
  - mouse-look turned **30.0°** at 1× and **15.0°** at 2× for the same motion.
- **Cursor:**
  - Alt shows the cursor (mouse released);
  - clicking the 2×, 0.5× and 1× chips works;
  - clicking SCAN selects Scan;
  - the Scan shutter takes no photo and shows the Codex message;
  - a swipe right returns to Normal, a swipe left goes to Scan, → returns to Normal;
  - releasing Alt: cursor hidden, **yaw unchanged, no photo taken**, mouse grabbed again.
- **Navigation:**
  - the shortcut opens the Gallery (Camera suspended, same photos, normal FOV);
  - ESC returns to the Camera with the zoom kept (2×);
  - ESC returns to home page 0, with the FOV restored, first person and the HUD back;
  - a Camera opened from page 1 returns to page 1;
  - TAB closes the Camera, and TAB in a Gallery opened from the Camera ends the session.
- **Gallery:**
  - the secondary page holds the Gallery app;
  - it opens the same Gallery and returns to page 1;
  - 13 thumbnails cached and **no full-size texture** while browsing;
  - the wheel scrolls.
- **Viewer:**
  - opens; the aspect ratio is preserved;
  - → / ← arrows work;
  - the favourite is saved to the sidecar;
  - a single click doesn't edit, a double click does;
  - Enter saves the name (id and files unchanged);
  - ESC cancels an edit;
  - Delete asks first, Cancel keeps the photo;
  - a confirmed delete removes the PNG, JSON and thumbnail, and the viewer moves on.
- **Tabs and back:**
  - the Favourites tab shows 1;
  - ESC returns to the Gallery, then to home page 1;
  - TAB closes the Phone.
- **Accessibility:** reduced motion shows no flight and updates the shortcut immediately.
- **GUI scales:** at 4, 3, 2 and 1, the viewfinder controls are inside the window and the viewer keeps the aspect ratio.
- **Development capture:** the HUD stays visible; the white fraction is **1.0** (the interface is in the image); `capture = "full_screen"`; then back to clean.

**Phone Phase 1 regression (scenes 45 and 64):**
- All checks pass at both resolutions, including hitboxes, the shade, the dev command and the equipment slot.
- Their expectations were updated only for the intentional extra page (§8).

**Earlier attempts.** Their summary lines are in `verification/earlier-runs/`. Their detailed logs were overwritten when later runs reused the same game directory, so only the summaries and the FAIL lines quoted here remain. Both failures were test-setup issues, fixed before the final runs:
- **First 1080p run, 87/89.** The flying test set `abilities.flying` directly, and vanilla clears it on the ground; it now uses a real double-tap jump.
- **First other-player run, 1 FAIL.** `build.gradle` forces `--username Zcylas`, so the identity hadn't changed; the script now passes `--uuid`.

I also changed two visuals after reviewing earlier screenshots:
- the rename field overlapped the date line;
- the Scan panel touched the zoom chips at GUI 4.

## 10. Screenshots and capture evidence

All files are in the bundle under `screenshots/` (`notification_v2_` is the harness's file prefix). The full sets are indexed in `screenshots/INDEX.md`: 179 files at 1080p and 169 at 720p, 488 MB in total, not all bundled.

| Contact sheet (`screenshots/contact-sheets/`) | Shows |
|---|---|
| `r1080/contact_open.png` | Home → Camera; opening frames; viewfinder at 1× |
| `r1080/contact_zoom.png`, `contact_zoom_transition_2x.png`, `contact_zoom_transition_05x.png` | 1×, 2×, 0.5×, 0.71×, 1.41× (genuinely different projections); per-tick preset transitions |
| `r1080/contact_shutter_animation.png` | **Every tick of the shutter animation**: flash, inset photo, flight into the shortcut, updated thumbnail |
| `r1080/contact_movement.png` | Target block before the shot; the pig unharmed; photo while walking; photo while flying |
| `r1080/contact_cursor_and_modes.png` | Alt cursor hovering 2×; Scan mode; Scan message; shortcut hover |
| `r1080/contact_navigation.png` | Gallery from the Camera; back in the Camera; home after the Camera; secondary page; grid; scrolled grid; returned to page 1 |
| `r1080/contact_viewer.png` | Viewer; next; favourite; rename editing; renamed; delete confirmation; after delete; Favourites tab; named grid |
| `r1080/contact_gui_scales.png`, `r720/contact_gui_scales.png` | Viewfinder, Gallery and viewer at every GUI scale |
| `r1080/contact_final_corrections.png`, `r720/contact_final_corrections.png` | **Final corrections:** Scan placeholder and rename field at every GUI scale |
| `r1080/contact_reduced_motion.png` | Reduced-motion shutter (gentle flash, no flight) |
| `r1080/contact_persist_isolation.png` | After relaunch (14 photos, shortcut thumbnail); other world: empty Gallery and empty shortcut |

**Representative full-size screenshots** are bundled in `screenshots/representative/` (1080p and 720p).

**Real photographs** (the actual Gallery files from `final1080`, each with its JSON sidecar) are in `photos/`. These are the Camera's own output, not screenshots:

| Photo | What it shows |
|---|---|
| `Photo 1` | The first clean shot, 1880×1052 |
| `Sunrise Peaks` | Taken **in flight** (y 93.3 vs 84.6 on the ground), then renamed and favourited in the viewer |
| `Photo 15` | The **development full-screen capture** of the same scene, with the HUD and Camera UI, for comparison with the clean shots |
| Thumbnail | `Photo 1`'s 256 px cache file |

All final photos were taken at 1×; the zoom levels are shown in the viewfinder screenshots.

## 11. Known limitations

1. **Shader packs: not tested** (no Iris or shader pack in this environment). The capture point is designed to include them; see §4.
2. **Multiplayer servers:**
   - The server-scope directory key (address normalisation, isolation from worlds and other servers) is **unit-tested only**.
   - No real dedicated-server join was run for Camera/Gallery.
   - Nothing in the feature talks to the server, so the client path is the same as in singleplayer.
3. **World identity = save folder name.**
   - Renaming a save folder starts a new Gallery (the old one stays on disk).
   - Deleting a world and creating a new one with the same folder name shares the old Gallery.
   - Server identity = the address as typed (normalised), so one server reached by two addresses gets two Galleries.
4. **The dev client's player name is fixed** by `build.gradle` (`--username Zcylas`). The other-player run therefore used vanilla's `--uuid` (offline UUID of "OtherTester"); the Gallery follows the UUID, which is what changed. The first attempt without `--uuid` failed because the identity was in fact the same; that was a test-setup issue, kept in the evidence.
5. **Dynamic FOV effects** (sprint, speed potions, underwater narrowing) are not applied while the Camera is open, by design (stable framing).
6. **The FOV is capped at 165°** for extreme settings: 0.5× from a 110° setting gives 141°, under the cap.
7. **Name tags are hidden in the Camera**, as with F1. Underwater, fire and in-wall overlays remain, because they are part of the first-person view.
8. **Other screens** (chat, inventory, pause) can be opened over the viewfinder; it stays drawn underneath and its input pauses. Alt+Z/Alt+X radials and Alt+B dictation are not blocked while the Camera is open.
9. **The pixel readback conversion runs on the render thread** (vanilla's `Screenshot.takeScreenshot`, the same as F2). Encoding and I/O are off-thread.
10. **The Gallery grid scrolls with the wheel and keyboard;** there is no drag-scrolling. Rename is in the viewer only.
11. **Controls are drawn, not sprite art;** the Gallery icon is a provisional abbreviation, consistent with the Phase 1 convention. Custom Camera sounds are not added (the shutter uses vanilla `ui.cartography_table.take_result`).
12. **The arrow-key shortcuts are fixed**, not rebindable; Alt is rebindable as the Radial Modifier.
13. **Screenshot sizes follow the compositor:** the requested 1920×1080 window arrives as 1920×1012 or 1880×1052, and photos always match the actual render target.


## 12. Decisions requiring your approval

1. **The page indicator is now visible in normal play,** because the Gallery is on a real second page. Is a two-page home acceptable, and is the Gallery's position (first slot of page 2) right?
2. **Viewfinder visual style** (`cam_02_viewfinder_1x`): drawn controls, corner marks, cyan accent, the "‹ Phone" back pill, and the "Hold Alt for the cursor" hint for the first 4 s.
3. **Shutter timing** (~0.64 s: flash 0.12 s, inset photo until 0.33 s, flight) and the **reduced-motion behaviour** (keyed to vanilla's Distortion Effects = 0 and Hide Lightning Flashes).
4. **Controls:**
   - Alt = the Totality Radial Modifier binding;
   - ←/→ for mode, ↑/↓ for zoom presets;
   - F5 ignored in the Camera;
   - the Camera forces first person (the previous perspective is restored on close).
5. **Photos hide name tags (F1 semantics)** and ignore sprint/speed FOV changes.
6. **Storage:**
   - root `<game dir>/totality/gallery/v1/`;
   - world identity by save folder;
   - server identity by normalised address;
   - one JSON sidecar per photo.
7. **Default names "Photo N";** the recovered-photo naming; the 40-character name limit.
8. **Rename commit rule:** clicking elsewhere *saves* the edit (desktop convention); ESC cancels. *(Kept as approved.)*
9. **Development full-screen captures keep the Camera controls.** *(Decided: kept, for debugging UI layering.)*
10. **The Scan placeholder wording:** "Scan — Coming with the Codex" / "Scan arrives with the Codex."
11. **The rename field size at GUI 2 and GUI 1** (see the §0 observation): keep vanilla's field, or scale it?


## 13. Recommendations for the Scan phase

These are **future recommendations, not implemented or verified behaviour.** See the audit §6 for the full reasoning. In short:

1. Scan reuses this Camera as-is: the same viewfinder, `CameraZoom`, input guard, and mode selector already showing Scan. Range can scale with zoom and tier.
2. **The client nominates, the server decides.** The client sends the target (entity id, or block position + state). A server-side Codex service re-validates line of sight, range and eligibility, records the discovery per player in world data, and rate-limits requests.
3. Use a dwell timer with forgiving decay (+1/tick on target, −2 off). Show progress as a ring around a reticle in the viewfinder; prefer a screen-space target bracket over a custom world shader at first.
4. Optionally save a photo on a successful scan (`mode = "scan"`, discovery id in `extra`), subject to the **architectural rule in §2.1**: discoveries never depend on photograph storage, and a deleted, lost or corrupt photo never removes or invalidates a discovery.
5. Codex entries should be data-driven, with redirects, composites (trees, structures) and action-gated entries that Scan cannot unlock.
