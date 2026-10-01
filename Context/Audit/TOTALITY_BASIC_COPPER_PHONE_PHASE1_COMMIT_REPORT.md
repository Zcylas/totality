# TOTALITY — Basic Copper Phone Phase 1 Commit Report

- **Date:** 2026-10-01.
- **Status:** Phase 1 approved (after Revision 1 and the centred-padlock correction). Committed as **one local commit, not pushed**.
- **Commit message:** `feat: redesign Basic Copper Phone phase 1`
- **Parent:** `942218b45bff061a594b08af83207f7373ca72a8` ("feat: implement server-authoritative Entitlement API").

This report is part of the commit it describes, so it cannot contain that commit's own hash. To retrieve it:

```
git log --format='%H %s' -1 -- Context/Audit/TOTALITY_BASIC_COPPER_PHONE_PHASE1_COMMIT_REPORT.md
```

(or `git log -1 --grep='redesign Basic Copper Phone phase 1'`).

Design and implementation details are in the two reports committed alongside:
- `Context/Audit/TOTALITY_BASIC_COPPER_PHONE_PHASE1_REPORT.md` (Phase 1);
- `Context/Audit/TOTALITY_BASIC_COPPER_PHONE_PHASE1_REVISION_REPORT.md` (Revision 1, plus §0, the final centred-padlock correction).

---

## 1. What the commit contains

- **Casing:** the Basic Copper Phone casing redesigned at 1:2 proportions. Sprites are generated from the unchanged item texture; the face plate is the calmer Revision 1 version.
- **Styling:** separate OS theme (`PhoneTheme`) and hardware styling (`PhoneDeviceStyle`).
- **Home screen:** status bar, 3×4 application grid, favourites dock, page indicator and sliding pages, responsive labels ("Tech" / "Inv." only where the full names cannot fit).
- **Notification shade:** visual prototype, with notification type styled separately from urgency, fed only by synthetic development data (`PhonePrototype`).
- **Equipment slot:** new Phone slot icon (`container/slot/phone.png`).
- **Hitboxes:** corrected application and dock hit areas. The shared `PhoneHomeGeometry` gives the visible frame plus label for drawing and input alike, and the dock spacing is symmetric.
- **Padlocks:** centred inside the tile, with identical locked and unlocked tiles and frames.
- **Dev command:** `/totalityphone` (client-side, development environment only, synthetic data).
- **Tests, captures and tooling:** unit tests, real-client capture scenes 45 and 64, and in-repo tooling (sprite generator, capture script).

The centred-padlock correction is included:
- `PhoneHomeGeometry.padlock(x, y, size)` exists, and `frame(x, y)` takes no locked flag.
- `PhoneUi.appIcon` draws the padlock at that shared position.
- `PhoneHomeGeometryTest.padlockIsCentredInsideTheTileAndFramesNeverGrow` passes.
- The real-client check "locked (Map) and unlocked (Inventory) apps have identically sized frames" passes at both resolutions.

## 2. Files in the commit (35)

**Modified (14)**
```
src/main/java/zcylas/totality/TotalityClient.java              (registers /totalityphone; dev environment only)
src/main/java/zcylas/totality/client/phone/PhoneCapture.java
src/main/java/zcylas/totality/client/phone/PhoneCaptureStates.java
src/main/java/zcylas/totality/screen/phone/BankScreen.java
src/main/java/zcylas/totality/screen/phone/PhoneAppGridScreen.java
src/main/java/zcylas/totality/screen/phone/PhoneDeviceStyle.java
src/main/java/zcylas/totality/screen/phone/PhoneFrameRenderer.java
src/main/java/zcylas/totality/screen/phone/PhoneSetupScreen.java
src/main/java/zcylas/totality/screen/phone/PhoneUi.java
src/main/resources/assets/totality/textures/gui/sprites/phone/crude/frame.png
src/main/resources/assets/totality/textures/gui/sprites/phone/crude/frame.png.mcmeta
src/main/resources/assets/totality/textures/gui/sprites/phone/crude/speaker.png
src/test/java/zcylas/totality/screen/phone/PhoneCodexAppTest.java
src/test/java/zcylas/totality/screen/phone/PhoneDeviceLayoutTest.java
```

**Deleted (3)**
```
src/main/resources/assets/totality/textures/gui/sprites/phone/crude/glass.png
src/main/resources/assets/totality/textures/gui/sprites/phone/crude/glass.png.mcmeta
src/main/resources/assets/totality/textures/gui/sprites/phone/crude/lock.png    (moved: phone/os/lock.png, same pixels)
```

**Added (18)**
```
src/main/java/zcylas/totality/client/phone/PhoneDevCommand.java
src/main/java/zcylas/totality/client/phone/PhoneInteractionCapture.java
src/main/java/zcylas/totality/client/phone/PhonePrototypeCapture.java
src/main/java/zcylas/totality/screen/phone/PhoneHomeGeometry.java
src/main/java/zcylas/totality/screen/phone/PhoneNotificationShade.java
src/main/java/zcylas/totality/screen/phone/PhonePrototype.java
src/main/java/zcylas/totality/screen/phone/PhoneTheme.java
src/main/resources/assets/totality/textures/gui/sprites/container/slot/phone.png
src/main/resources/assets/totality/textures/gui/sprites/phone/crude/camera.png
src/main/resources/assets/totality/textures/gui/sprites/phone/crude/grille.png
src/main/resources/assets/totality/textures/gui/sprites/phone/os/lock.png
src/test/java/zcylas/totality/screen/phone/PhoneHomeGeometryTest.java
src/test/java/zcylas/totality/screen/phone/PhonePhase1PrototypeTest.java
Context/Tools/basic-copper-phone-phase1/generate_basic_copper_phone_gui.py
Context/Tools/basic-copper-phone-phase1/run_phone_capture.sh
Context/Audit/TOTALITY_BASIC_COPPER_PHONE_PHASE1_REPORT.md
Context/Audit/TOTALITY_BASIC_COPPER_PHONE_PHASE1_REVISION_REPORT.md
Context/Audit/TOTALITY_BASIC_COPPER_PHONE_PHASE1_COMMIT_REPORT.md
```

**Notes on staging**
- Every file was staged explicitly, by path.
- `src/` was clean at `942218b` when the Phone assignment started, so none of these files contains unrelated changes.
- The only non-Phone line is in `TotalityClient.java`: one import and one call, registering the Phone dev command.

**Tooling (new in the repo)**
- No Python or shell tooling had been committed before. Earlier generators lived in the untracked `Totality-Research/` folder.
- At your request, the Phase 1 tools are now in `Context/Tools/basic-copper-phone-phase1/`:
  - **`generate_basic_copper_phone_gui.py`** writes the casing sprites, the OS padlock and the slot icon. Run it from the repo root: `python3 Context/Tools/basic-copper-phone-phase1/generate_basic_copper_phone_gui.py .`. It was verified to reproduce all 7 committed sprite files byte for byte.
  - **`run_phone_capture.sh`** is the portable real-client verification script. Usage: `<name> <width> <height>`, e.g. `r1080 1920 1080`. It was used for the verification below.
- `PhoneDeviceStyle`'s doc comment now points to that path (comment only).

**Deliberately not in the commit**
- The pre-existing reference changes, untouched:
  - the deleted `Context/References/TOTALITY_MASTER_v3.6_SECTIONS_26L_26M_EXTRACT.md`;
  - the untracked `Context/References/Anime Screenshots/`, `Mob Hud V1/`, `Notification API V2/` and `Other/` folders;
  - the untracked `Context/References/STEFAN_TOTALITY_MASTER_REFERENCE_v3.8_final_audited.docx`.

  The two Phone concept boards in `Context/References/Other/` are among them.
- Review bundles (`Context/Audit/Review Bundles/`, git-ignored).
- Capture output and game directories under `build/`.
- `Totality-Research/` (outside the repository).

## 3. Verification (fresh, before committing)

| Check | Result | Baseline |
|---|---|---|
| `./gradlew build --offline` | BUILD SUCCESSFUL | — |
| Forced full test run: `./gradlew test --rerun` with results cleared first | **230 classes, 2,365 tests, 0 failures, 0 errors, 2 skipped** (`VoiceCommandWavProbeTest`, `VoskWavRecognitionIntegrationTest`: opt-in) | 2,365 / 0 / 2 |
| Real client, 1920×1080 requested (1920×1012 window), GUI 4/3/2/1, scenes 45 + 64 (`run_phone_capture.sh commit-1080`) | exit 0, **94/94 PASS**, no FAIL/TIMEOUT | 94/94 |
| Real client, 1280×720, GUI 3/2/1 (4 not available), scenes 45 + 64 (`run_phone_capture.sh commit-720`) | exit 0, **90/90 PASS**, no FAIL/TIMEOUT | 90/90 |

The real-client runs re-verified, among others:
- **Hitboxes:** real clicks in the Codex/Map gap, the Codex/Map/Spells/Abilities corner and around Inventory activate nothing, while a valid click opens Inventory. Empty points hit nothing at every GUI scale.
- **Frames:** locked and unlocked apps have identical frames.
- **Shade:** all shade gestures still work.
- **Command:** every `/totalityphone` option works, and the wallet and Bank entitlement are unchanged afterwards.

## 4. Deferred (documented, not implemented)

- **Badge over padlock:** at 720p/GUI 3, notification badges can overlap the centred padlock on locked apps.
- **Synthetic data:** notification data and application badges remain synthetic development data.
- **Keyboard:** the notification shade has no full keyboard navigation yet.
- **Future work:** final application icons, complete Phone functionality and Notification V3 integration.
- **Codex:** the app is not implemented; its icon is a no-op.

Also see Revision Report §8 (window-size limit of the capture environment, provisional notification type colours, and the 1 px centring caveat for a future fifth favourite).

Not started: Phase 2, Notification V3, Codex. **Not pushed.**
