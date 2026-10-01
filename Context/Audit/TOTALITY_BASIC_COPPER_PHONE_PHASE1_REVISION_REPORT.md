# TOTALITY — Basic Copper Phone Phase 1, Revision 1 Report (with final visual correction)

**Corrections after manual review. Nothing committed or pushed; stopped for review.**

- Builds on the Phase 1 delivery (`TOTALITY_BASIC_COPPER_PHONE_PHASE1_REPORT.md`), which remains valid except where this report says otherwise. Base commit `942218b`.
- Bundles in `Context/Audit/Review Bundles/`:
  - `TOTALITY_BASIC_COPPER_PHONE_PHASE1_REVISION_REVIEW.zip` — Revision 1.
  - `TOTALITY_BASIC_COPPER_PHONE_PHASE1_FINAL_CORRECTION_REVIEW.zip` — incremental, for the final correction (§0).
- Full Phone functionality, Notification V3 and the Codex app were not started.

---

## 0. Final visual correction (after Revision 1 review)

Revision 1 is otherwise approved and unchanged.

**What changed**
- **Padlock centred inside the tile.** The 5×7 padlock is now centred inside every locked app's icon tile and never leaves it. A new shared helper, `PhoneHomeGeometry.padlock(x, y, size)`, gives its position; `PhoneUi.appIcon` draws it there, and the tests check the same rectangle.
  - It is drawn in the bright text colour.
  - The app's abbreviation underneath is dimmed almost to the tile colour, so the padlock reads first. A locked sprite icon would get a translucent cover; no current locked app has one.
- **One frame for every app.** `PhoneHomeGeometry.frame(x, y)` no longer takes a locked flag: every tile, and so every hover and keyboard-focus frame, is the same size whether the app is locked or not. `appBounds` and `hitsApp` dropped the flag too. Hit-testing still uses exactly the drawn frame plus the label.
- **Unchanged:** labels (still below their icons), badges, hitboxes, spacing, the dock layout, hover vs focus styling, actions and dragging.
- **Dock comment fixed.** The outdated "GUI 4 on a 1080p window: 18 instead of 20" in `PhoneHomeGeometry.compute` is corrected. The dock icons match the grid icons at every captured size; the step-down only exists as a safeguard.

**Files changed**

| File | Change |
|---|---|
| `screen/phone/PhoneHomeGeometry.java` | `padlock()` replaces `lockBox()`; `frame`/`appBounds`/`hitsApp` lose the locked flag; dock comment |
| `screen/phone/PhoneUi.java` | Centred padlock; dimmed locked symbol |
| `screen/phone/PhoneAppGridScreen.java` | Call sites (3 lines) |
| `client/phone/PhoneInteractionCapture.java` | Keyboard focus on an unlocked app; checks that locked and unlocked frames are the same size |
| `test/.../PhoneHomeGeometryTest.java` | `padlockIsCentredInsideTheTileAndFramesNeverGrow` replaces the old clearance test; the other tests drop the flag |

**Verification**
- **Build and tests:** `./gradlew build` succeeds. A forced full test run gives **230 classes, 2,365 tests, 0 failures, 0 errors, 2 skipped** (pre-existing). `PhoneHomeGeometryTest` checks, at every captured window size, GUI scale and label scale:
  - the padlock is inside the tile and centred (±1 px);
  - every frame is the tile plus 2 px on each side;
  - the hit frame equals the drawn frame;
  - the dock's padlock rule;
  - all empty-space, no-overlap and dock-symmetry checks, unchanged.
- **Real client:** `rev2-1080` (1920×1012, GUI 4/3/2/1) **94/94 PASS**; `rev2-720` (GUI 3/2/1) **90/90 PASS**. No `FAIL` or `TIMEOUT` lines.
  - These include every Revision 1 interaction check (empty-space clicks, the valid Inventory click, drags, the dock, the shade, `/totalityphone`) and per-scale hits.
  - New checks: keyboard focus moves to Inventory, and the locked Map frame and the unlocked Inventory frame have identical position and size.
- **Screenshots:**
  - `p1_52_hover_inventory` — unlocked, hover
  - `p1_54_locked_hover_map` — locked, hover
  - `p1_53_keyboard_focus` — locked, focus
  - `p1_53b_keyboard_focus_unlocked` — unlocked, focus
  - Contact sheets: `final-correction/01_locked_vs_unlocked_frames_1080.png`, `02_padlock_zoom_1080.png`, `03_all_scales.png`, `04_hitbox_and_bounds.png`.

**Observation (not changed, as requested):** on the smallest tiles (720p/GUI 3, 14 px), a badge on a locked app can cover the top of the padlock, e.g. Mail's test badge "12". Badges were left exactly as they were.

---

## 1. App hitboxes (high priority) — root cause and fix

### Root cause

You were right that this was an interaction bug, not just a hover highlight. Phase 1 hit-tested the **whole grid cell**, not the visible app:

```java
// Phase 1, PhoneAppGridScreen.tileAt
if (mx >= x && mx < x + geo.cellW() && my >= y && my < y + geo.cellH()) return i;
```

At 1080p/GUI 4, a cell is 34×44 GUI px, while the icon is 20×20. Everything else in the cell counted as the app, which explains both reports:

- **Codex:** the empty space toward Map, Spells and Abilities belongs to Codex's cell, so clicking there pressed Codex.
- **Inventory:** the space around Inventory's icon is Inventory's cell, so clicking there opened Inventory.

The coordinate transforms were not at fault. The pose shift during the 170 ms slide-in is the only transform, and it was handled for drawing but not for input. That is fixed too: the pointer is now mapped back through the slide.

The dock had the same flaw: anything within its full height and ±(icon/2 + 2) px horizontally counted.

### Fix

The new pure class `PhoneHomeGeometry` is the single source of truth for drawing and input. Each app's interactive area is exactly its visible bounds:

- **The hover/focus frame:** the icon plus 1 px clearance plus the 1 px frame line. Since the final correction (§0) this frame is the same size for locked and unlocked apps.
- **The label block:** the label's own measured width, its lines, and the 3 px between the label and the frame.

Every interaction uses those same bounds: hover highlight, press feedback, click, the lock-reason tooltip and the dev bounds overlay. The dock uses its icon frames the same way.

**Clicks and drags stay distinct:**
- An action happens on release, and only if the pointer never moved 4 px or more (unchanged from Phase 1).
- A drag that starts on an app never activates it. Verified in the real client: a vertical drag from Inventory and a short sideways drag from Codex.

**Keyboard focus is separate from mouse hover:**
- Hover: a quiet 1 px cyan frame.
- Focus: a bright frame with corner ticks.
- Moving the mouse ends keyboard mode (a pre-existing rule); arrow keys, Enter and Space are unchanged.

**Other elements checked:**

| Element | Phase 1 | Now |
|---|---|---|
| Grid apps | Whole cell | Frame + label ✔ |
| Dock | Full dock height, icon ±2 px | Icon frame ✔ |
| Page markers | ±3 px around a 5 px marker | Unchanged (already tight) |
| Status bar | The bar itself | Unchanged (it is visible as a bar) |
| Shade dismiss "×" | Right 10 px of the whole card header | A 9×(icon+2) box around the glyph ✔ |
| Shade chevron | Right 10 px of the **whole expanded card** | A 9×(line+2) box around the glyph ✔ |
| Expanded card body | Partly a collapse area (right edge) | Inert; only the header, chevron, × and Mark as Read act ✔ |
| Mark as Read, Clear All | Button bounds | Unchanged |

### Verification

**Unit tests — `PhoneHomeGeometryTest`.** These run across 9 window/GUI-scale combinations at every crisp label scale, using worst-case data: every app locked, two-line labels at full column width. They check that:
- Each icon centre, icon corner and label hits its own app.
- These points hit nothing: between neighbouring icons, left and right of each icon inside its old cell, between a label and the next row, and the four-way corner (Codex/Map/Spells/Abilities).
- No two apps' bounds overlap.

**Real client (scene 64).** Real press and release through the screen's own handlers, then the outcome is checked:

| Click target | Result |
|---|---|
| Codex/Map gap, the four-way corner, right/left of Inventory, below Inventory's label, between dock icons 1/2 and 3/4 | Same screen, no press feedback |
| Inventory's **label** (a valid click) | Opens Inventory |

At every GUI scale (1080p: 4/3/2/1; 720p: 3/2/1), the scene also checks that each icon hits its own app and that eight gap points hit nothing.

**Diagnostics:** `/totalityphone bounds` outlines exactly what hit-testing uses (`p1_55_bounds_home`, `p1_56_bounds_shade`, `p1_69_cmd_bounds`).

---

## 2. Favourites dock spacing

**Measured cause:** dock slots were centred at `dockW·(2i+1)/(2n)` with integer division. At 1080p/GUI 4 that gave outer gaps of 2 and 3 px and uneven inner gaps.

**Now:**
- Equal gaps outside and between: `panel = n·icon + (n+1)·gap`, centred so that (display − panel) is even. For four favourites the panel is always exactly centred.
- Dock icons match the grid's, stepping down 2 px only where the gap would otherwise be under 5 px. That keeps 1 px of empty space between neighbouring hover frames, so a pointer between two dock icons hits neither. At every captured size the dock icons match the grid icons (e.g. 18 px at 1080p/GUI 4, 14 px at 720p/GUI 3); the step-down never triggers there, and exists only as a safeguard (for example for a future 5th favourite).
- **Future 5th favourite:** with an even number of gaps, an odd-width display can't centre the panel exactly, so it may be 1 px off. Equal icon spacing still holds.

Logged by the real client (`verification/capture-*.log`, "dock:" lines) for every scale. Outer left/right padding is equal everywhere, e.g. 1080p/GUI 4: icon 18, gap 5, L/R 5/5.

Order and favourites are unchanged: Character, Skills, Quests, Camera.

---

## 3. Padlock and hover frame (superseded by the final correction, §0)

Revision 1 hung the padlock off the icon's right edge and widened locked apps' frames to enclose it. The final correction (§0) replaces this: the padlock is centred inside the tile, and every app has the same frame.

**Kept from Revision 1:** the 1 px frame line with 1 clear pixel around the tile; the dim (not faint) colour for a locked app's hover frame; hover vs keyboard-focus styling.

---

## 4. Review improvements

**Responsive labels**
- Only "Technology" → **Tech** and "Inventory" → **Inv.** have short forms, and they appear only when the full label can't fit at any readable crisp size. App identifiers, actions and badges still use the full name.
- Real-client results (logged per scale):

| Window / GUI | Labels shown |
|---|---|
| 1080p (1920×1012) / 4, 3, 2, 1 | All full names |
| 720p / 3 (default) | **Inv.**, **Tech**, others full |
| 720p / 2 | Inventory, **Tech**, others full |
| 720p / 1 | All full names |

No label is cut short ("...") at any scale; the real client checks this at each one.

**Notification styling.** The prototype's presentation data now separates **visual type** from **urgency**:
- **Type** (`Entry.typeColor`; provisional prototype-only `PhonePrototype.Look` colours) sets the icon tile, abbreviation and source name.
- **Urgency** sets a 2 px stripe on the left edge and the green dot / orange "!" / red ▲ glyph beside the timestamp.
- Cards are otherwise neutral for every type and urgency.
- The test set shows **System at normal, important and critical**, **Quests at normal and important**, and **six types sharing normal urgency** (`p1_11_shade_compact`, `p1_13_shade_expanded`, `p1_64_cmd_critical`).
- No Notification V2 link and no permanent categories: `Look` is a private dev enum.

**Technology:** visible but unavailable ("Not available yet."), with no entitlement invented. Bank still reads `ClientEntitlementView.isSelectable(PhoneAppEntitlements.BANK_APP)`. System and Camera remain no-op placeholders.

**Main-page marker:** the tiny house is replaced by a **cyan 5×5 ring**, always cyan so "home" reads from any page, and filled when it is the current page. Other pages are 2×2 dots.

**Casing noise:**
- The face plate now uses only the item's two dominant face colours, at a ratio of 8:3 (Phase 1 used four), and about half the speckles (2% dark / 0.7% light, down from 4% / 1.5%).
- Rim, groove, screws, bezel, geometry and aspect are unchanged, and every colour still comes from the unchanged item texture.
- A before/after enlargement is in `screenshots/casing_noise_before_after_x8.png`.

**Unchanged by request:**
- The Setup and Bank recolour (approved), the OS palette, wallpaper, aspect and casing geometry, and the slot icon.
- Wallet stays off the Phone; the Wallet gameplay system was not touched.

---

### Bug found during this revision: icon overflow at small GUI scales

The real-client screenshots showed the Codex icon overflowing its 11 px tile in a shade card at GUI 1. The same bug existed in Phase 1 and also affected GUI 2 cards and the GUI 1 home grid.

`PhoneUi.crispSprite` never drew below one screen pixel per texel, so a 32-texel icon that couldn't fit at that size spilled out of its tile. It now draws the sprite at exactly the space available in that case (a nearest-neighbour downscale). It never leaves its tile, and where it fits crisply nothing changes. See `p1_31_shade_gui1` and `p1_31_shade_gui2`.

---

## 5. `/totalityphone` — development testing command

**Conventions.** It follows `/totalityhologram` (`HologramShowcase`):
- a Fabric **client-side** command, never sent to the server;
- registered only when `VerificationReporter.isDevEnvironment()` is true;
- registered from `TotalityClient` next to the hologram showcase.

The root `totalityphone` collides with nothing; the existing `"phone"` literal is a server subcommand.

**How it works.** It opens the **real** `PhoneAppGridScreen` with its real interaction code, on the next client tick (the chat screen closes itself after a command runs). It uses the equipped or held phone's frame, and the Basic Copper Phone if there is none.

**Synthetic data only.** It changes only `PhonePrototype`'s test state. It contains no network, entitlement, wallet, currency, notification or progression calls (a source test enforces this). The real client also confirms the wallet value and Bank entitlement are identical before and after the whole command sequence.

| Command | Effect |
|---|---|
| `/totalityphone` or `/totalityphone help` | List the options |
| `/totalityphone home` | Open the home screen in the current test state |
| `/totalityphone pages` | Test data on; main page with a development page on each side |
| `/totalityphone normal` | Six normal-urgency notifications (green indicator); open home |
| `/totalityphone important` | Adds important ones: 8, orange |
| `/totalityphone critical` | Adds a critical one: 9, red |
| `/totalityphone shade` | Open the shade with the full set |
| `/totalityphone expanded` | Shade with the Quests notification expanded |
| `/totalityphone empty` | Shade with no notifications ("No notifications") |
| `/totalityphone labels` | Long-label stress page |
| `/totalityphone bounds` | Toggle the interactive-bounds overlay |
| `/totalityphone reset` | Initial test state: test data on, full set, no stress, no overlay |
| `/totalityphone off` | Test data off: the ordinary Phone presentation |

Examples:
- `/totalityphone critical`, then tap the status bar (or `/totalityphone shade`) to test the shade with all urgencies.
- `/totalityphone bounds` with the mouse over the grid, to see exactly what is clickable.
- `/totalityphone off` before checking normal play.

`-Dtotality.phone.prototype=true` still works; it starts the client with test data on.

Verified in the real client through the registered command tree (`ClientCommands.getActiveDispatcher()`):
- `help` runs, and an unknown option is rejected.
- `off`, `pages`, `normal`, `important`, `critical`, `shade`, `expanded`, `empty`, `labels`, `bounds` (on and off), `reset`, `home` and `off` again each open the right state. Screenshots: `p1_60`–`p1_71`.

---

## 6. Files changed in this revision (vs the Phase 1 delivery)

**Added**

| File | Purpose |
|---|---|
| `screen/phone/PhoneHomeGeometry.java` | Pure home layout: cells, icons, frames, padlock box, app/dock interactive bounds, symmetric dock |
| `client/phone/PhoneDevCommand.java` | `/totalityphone` |
| `client/phone/PhoneInteractionCapture.java` | Real-client interaction, per-scale and command checks (scene 64) |
| `test/.../screen/phone/PhoneHomeGeometryTest.java` | 5 geometry regression tests |

**Modified**

| File | Change |
|---|---|
| `screen/phone/PhoneAppGridScreen.java` | Geometry via `PhoneHomeGeometry`; visible-bounds hit-testing for hover, press and click; input mapped through the slide-in; responsive labels (`App.shortLabel`); separate hover/focus frames; Technology locked; dev bounds overlay; capture diagnostics |
| `screen/phone/PhoneUi.java` | `iconFrame` (hover vs focus); padlock position; cyan ring main-page marker; pure `displayScale(layout, guiScale)`; `outlineRect`; `crispSprite` overflow fix |
| `screen/phone/PhoneNotificationShade.java` | `Entry.typeColor`; neutral cards with an urgency stripe and glyph; tight dismiss/chevron boxes; inert expanded body; `interactiveBounds()` |
| `screen/phone/PhonePrototype.java` | Scenarios NONE/NORMAL/IMPORTANT/CRITICAL; provisional `Look` types; `reset()`, `off()`, `showBounds` |
| `client/phone/PhonePrototypeCapture.java` | New sections and counts; Mark as Read before the swipe-left |
| `TotalityClient.java` | Registers `PhoneDevCommand` (dev environment only), plus one import |
| `textures/gui/sprites/phone/crude/frame.png` | Calmer face plate |
| `PhoneCodexAppTest.java`, `PhonePhase1PrototypeTest.java` | New `App`/`Entry` shapes; Technology, Inventory and type-vs-urgency tests; a dev-command isolation test |

The sprite generator was updated (`face_calm`, speckle rates). It is in the bundle, and since the Phase 1 commit it is in the repository at `Context/Tools/basic-copper-phone-phase1/generate_basic_copper_phone_gui.py` (it reproduces every committed Phone sprite byte for byte).

Patches in the bundle:
- `REVISION_ONLY.patch`: Phase 1 → this revision.
- `FULL_vs_942218b.patch`: everything since the base commit.

---

## 7. Verification results

- **Build:** `./gradlew build --offline` succeeds.
- **Full test suite:** forced `./gradlew test --rerun` gives **230 classes, 2,365 tests, 0 failures, 0 errors, 2 skipped** (pre-existing opt-in tests).
- **New and updated tests:** `PhoneHomeGeometryTest` (5), `PhonePhase1PrototypeTest` (9), `PhoneCodexAppTest` (3), `PhoneDeviceLayoutTest` (3). `EntitlementWiringSourceRegressionTest` and `HologramClientIsolationTest` pass unchanged.
- **Real client, scenes 45 + 64:** see §7.1 for the final runs.

### 7.1 Final capture runs

Both runs exited with code 0 and logged no `FAIL` or `TIMEOUT` lines.

| Run | Window | GUI scales | Checks |
|---|---|---|---|
| `rev1-1080` | 1920×1012 (compositor limit) | 4, 3, 2, 1 | **92/92 PASS** |
| `rev1-720` | 1280×720 | 3, 2, 1 (4 not available, skipped automatically) | **88/88 PASS** |

The `/totalityphone nonsense` line in each log reads "command rejected". That is the expected outcome, and the next check ("an unknown option is rejected") passes.

What the checks cover:
- **Interactions:** the empty-space clicks and the valid Inventory click (§1); drags from apps; hover vs keyboard focus; Technology unavailable; the expanded card body inert.
- **Per scale:** icon and gap hits, dock symmetry, labels never cut short.
- **Phase 1 checks:** all still pass (pages, swipes, indicators, shade gestures, Mark as Read, Clear All, ESC).
- **Command:** every `/totalityphone` option, plus wallet and Bank entitlement unchanged.

The logs (`verification/capture-rev1-*.log`) also record the measured dock geometry and the labels shown at each scale.

---

## 8. Remaining limitations and open points

- **720p/GUI 3** shows "Inv." and "Tech". That is the agreed rule; there is no other way to fit them at a readable crisp size.
- **Dock icon size:** dock icons match the grid icons at every captured size; they would step down only where the hover frames would otherwise touch (e.g. a future 5th favourite on the smallest phone).
- **A future 5th favourite** may sit 1 px off centre on odd-width displays (§2).
- **Shade hit areas:** a compact card stays tappable over its whole visible area (tap = expand), which is intended. The shade still has no keyboard navigation.
- **Window size:** the compositor limits the 1080p window to 1920×1012, as in Phase 1.
- **Notification styling:** the visual types are prototype-only, and their colours are provisional.

**Deferred to later phases** (recorded at the Phase 1 commit; nothing implemented for these):
- **Badge over padlock:** at 720p/GUI 3 (14 px tiles), a notification badge on a locked app can overlap its centred padlock (§0).
- **Synthetic data:** notification data and app badges are development data only (`PhonePrototype`). In normal play there are no badges, no status indicator and an empty shade.
- **Keyboard:** the notification shade has no full keyboard navigation (ESC closes it).
- **Future work:** final app icon artwork, complete Phone functionality and Notification V3 integration.
- **Codex:** the app is not implemented; its icon is a no-op.
