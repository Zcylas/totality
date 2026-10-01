# TOTALITY — Basic Copper Phone Redesign, Phase 1 Report

**Visual prototype — awaiting your approval. Nothing committed or pushed.**

- Base commit: `942218b45bff061a594b08af83207f7373ca72a8` (Entitlement API R4, local only).
- Date: 2026-10-01.
- Scope: the opened Phone's casing, the home screen, the notification-shade prototype, and the Phone equipment-slot icon.
- Not started: full Phone functionality, Notification V3 and the Codex app.
- Review bundle: `Context/Audit/Review Bundles/TOTALITY_BASIC_COPPER_PHONE_PHASE1_REVIEW.zip`.

---

## 1. What you are approving

| Area | State |
|---|---|
| Physical casing (taller and narrower, thick bezels, cleaner chin) | Implemented; real in-game screenshots |
| Home screen (status bar, 3×4 grid, dock, badges, pages, slide) | Implemented; real in-game screenshots |
| Notification shade (visual prototype and interaction scaffolding) | Implemented with **dev-only test data**; not connected to Notification V2 |
| Phone equipment-slot icon | Implemented; photographed in the real equipment slot |
| Existing apps, setup, saves, Bank entitlement | Preserved; the Bank and Setup screens are recoloured to the new OS palette but not redesigned (§3.4) |

Every image referenced here is a **real Minecraft client screenshot**, produced by the automated capture harness (`Screenshot.grab` on the real render target). The contact sheets in `screenshots/contact-sheets/` are side-by-side crops of those real screenshots, made with Python. They are not mockups, and no concept art is presented as in-game.

---

## 2. Design decisions and how I used the two references

The order of priority was: (1) the prompt, (2) the existing item texture, (3) the two boards as complementary inspiration.

| Topic | Board 1 (main conversation) | Board 2 (image conversation) | Decision |
|---|---|---|---|
| Body proportions | Very narrow (~0.43 w/h) | ~0.55 w/h | **1:2 (0.50)**: clearly a smartphone and no longer a tablet, while the display stays wide enough for three label columns |
| Casing | Flat speckled copper, plain chin | Riveted rim, darker inset face, corner screws, bottom port | Item copper palette (every colour sampled from and asserted against `basic_copper_phone.png`), with Board 2's **rim and inset face plate** shown as an engraved groove plus **four corner screws**. No back-panel or ridge detailing (not visible from the front) |
| Bottom bezel | Pill-shaped slot | Port/button slot | A **recessed perforated microphone grille** centred on the chin. It is deliberately not a button, because physical buttons are postponed. The old noisy dark chin band is gone |
| Top bezel | Speaker only | Speaker plus camera dot | The item's **speaker slot** (kept), plus a small **front-camera lens** to its left (Board 2) |
| Side buttons | Two on the right | Several, both sides | The item's **two right-edge buttons**, at the same relative heights as on the item |
| Status bar | Time, signal, bell + red count, green battery | Time, signal, green dot + count, battery; severity legend | Board 2's **severity glyphs** (green dot, orange "!", red ▲), matching the prompt. The battery is drawn in neutral text colour so green stays reserved for "normal notifications". No carrier text |
| Icons | Uniform navy tiles, coloured glyphs | Colour-coded tiles per app | **Uniform navy rounded tiles with cyan provisional abbreviations**, so no per-app colours are decided before the final icon artwork. The Codex keeps its own icon |
| App labels | Below icons | Below icons | Below icons, one consistent size, up to two lines, ellipsis when a single word cannot fit |
| Dock | 4 icons on a rounded panel, no text | Same | Same; the layout supports up to five favourites (`DOCK_CAPACITY = 5`) and shows only the slots that exist |
| Shade cards | Severity outline, icon, title, time, chevron; expanded card with centred Mark as Read; "× Clear All" bottom; empty state with bell icon | Similar; severity-tinted cards; swipe-hint panels | Severity **outline + tinted icon + coloured app name** (Board 1). The **swipe-hint panels behind a dragged card** are from Board 2. The empty state shows **text only**, as the prompt requires (both boards draw a bell; dropped) |
| Wallpaper | Dark navy, faint gradient | Mountains illustration | **Plain dark navy with a subtle cyan rise toward the bottom** (no pattern) |

The OS palette is now separate from the device:
- `PhoneTheme` (new) holds the OS colours: navy, cyan, severities, badges and dock.
- `PhoneDeviceStyle` now holds hardware only: frame, insets, aspect, speaker, camera, grille, buttons and bezel.

A copper casing therefore never tints the interface, and a future casing never needs new OS code.

---

## 3. Implementation

### 3.1 Files changed (24 under `src/`, against `942218b`)

**Modified**

| File | Change |
|---|---|
| `screen/phone/PhoneAppGridScreen.java` | Rewritten as the home screen. Covers the layout, pages and slide, dock, status bar, gesture routing (press/drag/release), keyboard navigation, and dev diagnostics. Existing app actions and the Bank entitlement check are kept verbatim |
| `screen/phone/PhoneDeviceStyle.java` | Hardware only: 1:2 aspect, new insets, camera and grille sprites, `bezel`. The OS `Display` palette moved out |
| `screen/phone/PhoneFrameRenderer.java` | Draws the OS wallpaper instead of the item-glass specks; adds the camera and grille. `MAX_HEIGHT` raised from 300 to 400 (§5) |
| `screen/phone/PhoneUi.java` | Uses `PhoneTheme`. New primitives: rounded rects, app icon, crisp sprite, badge, label wrap/ellipsis, signal/battery/severity glyphs, page indicator with home marker, chevrons, cross. `FULL_SIZE_DISPLAY_WIDTH` changed from 180 to 120 so the narrower display keeps the same text scale as before at GUI 3 and 4 |
| `screen/phone/BankScreen.java`, `PhoneSetupScreen.java` | One-line type change (`PhoneTheme`), the dropped `style` argument, and pre-existing inline FQNs replaced with imports (CLAUDE.md rule). No layout or behaviour change |
| `client/phone/PhoneCapture.java` | Registers capture scene 64; inline FQNs replaced with imports |
| `client/phone/PhoneCaptureStates.java` | Scene-45 states updated to the new layout. Clicks are now press and release, because actions happen on release |
| `screen/phone/PhoneCodexAppTest.java`, `PhoneDeviceLayoutTest.java` | Updated to the new order (Codex first), the 1:2 aspect and the new sprite set |
| `textures/gui/sprites/phone/crude/frame.png`, `.mcmeta`, `speaker.png` | Regenerated casing |

**Added**

| File | Purpose |
|---|---|
| `screen/phone/PhoneTheme.java` | OS palette (independent of the casing) |
| `screen/phone/PhoneNotificationShade.java` | Shade prototype: data rules, open/close animation, pull, scroll, swipe, click, drawing |
| `screen/phone/PhonePrototype.java` | **Development-only** test data (badges, notifications, dev pages, label stress). Off unless `-Dtotality.phone.prototype=true` |
| `client/phone/PhonePrototypeCapture.java` | Repeatable capture scene 64 (§4) |
| `screen/phone/PhonePhase1PrototypeTest.java` | 7 unit tests (§4.1) |
| `textures/gui/sprites/phone/crude/camera.png`, `grille.png` | Casing details |
| `textures/gui/sprites/phone/os/lock.png` | Padlock glyph, moved from `crude/` because it is OS art (pixels unchanged) |
| `textures/gui/sprites/container/slot/phone.png` | Phone equipment-slot icon |

**Removed:** `textures/gui/sprites/phone/crude/glass.png` and `.mcmeta` (item-glass specks; a lit display now shows the wallpaper).

**Not modified:** the item texture `textures/item/basic_copper_phone.png`, the equipment screen and menu, Notification API V2, every app screen apart from the recolour, save data and components.

**Sprite generator:** `Totality-Research/basic-copper-phone-phase1/tools/generate_basic_copper_phone_gui.py` (also in the bundle). It is deterministic and asserts that every copper colour exists in the item texture. It replaces `generate_crude_phone_gui.py` for these files.

### 3.2 Home screen

- **Order:** row 1 Codex · Map · Inventory; row 2 Spells · Abilities · Classes; row 3 Technology · Bank · Store; row 4 System · Mail · Settings. Favourites: Character, Skills, Quests, Camera. Favourites are not repeated in the grid.
- **Availability:**
  - Locked, with their previous lock reasons: Map, Spells, Abilities, Classes, Store, Mail.
  - **Bank** still reads `ClientEntitlementView.isSelectable(PhoneAppEntitlements.BANK_APP)`; no client flag was reintroduced.
  - Unlocked no-ops, following the existing "no backing system yet" convention: Codex, Technology, System, Settings, Camera. Technology, System and Camera are new and provisional (§7).
  - Wallet is gone: it had no action, and Bank already covers the account.
- **Icons:** rounded navy tiles, about 19% of the display width (at most 26 GUI px), with a provisional two-letter abbreviation.
  - The **Codex icon** is drawn at the largest size where each of its 32 texels is a whole number of screen pixels, so it is never blurry.
  - Locked apps are dimmed, with the padlock hanging off the tile's right edge.
- **Labels:** one size for all apps. It is the largest crisp scale, up to the display text scale, at which every word of every production label fits its column. Labels wrap at spaces onto at most two lines; a word that still doesn't fit ends in "...".
- **Badges:** a neutral light-grey pill at the icon's top-right corner, showing up to 99 and then "99+".
- **Status bar:** one row. On the left, `Minecraft time (real time)` in the existing format. On the right: notification indicator, cosmetic signal, cosmetic full battery.
  - The indicator shows the severity glyph of the highest unread urgency plus the total unread count, and is hidden at zero.
  - Tapping the status bar toggles the shade.
- **Pages:**
  - The main page is the centre page. In normal play it is the only page and the indicator is hidden.
  - With the prototype on, there is an empty development page on each side.
  - Small clickable dots; the main page is marked with a provisional tiny house.
  - The grid slides horizontally (220 ms ease-out, clipped to the display). The status bar, wallpaper and dock stay put.
  - Swipe the grid sideways to change page. More than 1/5 of the display width turns the page; less springs back; dragging past the first or last page meets resistance.
  - Left/Right past the grid edge also turns the page.
  - **Reopening always starts on the main page.**
- **Unchanged behaviour:**
  - The opening slide-in and power-on animations.
  - TAB closes the Phone. ESC closes the shade if it is open, otherwise the Phone. ESC from Bank returns to the home screen.
  - Keyboard focus and the lock-reason tooltip.

### 3.3 Notification shade (prototype)

- The shade slides down from under the status bar and covers the rest of the display. The status bar stays visible.
- Cards are listed newest first. A **compact** card shows the icon, app name in severity colour, relative time, a dismiss ×, one message line ending in "..." and an expand chevron. An **expanded** card adds the full message, details and a centred **Mark as Read** button, with an up-chevron.
- Severity styling:
  - Normal: neutral outline, cyan abbreviation.
  - Important: orange.
  - Critical: red (outline, tinted icon and app name).
- `× Clear All` is fixed at the bottom centre. An empty shade shows only `No notifications`, with no icon and no Clear All.
- Timestamps read `now`, `Nm ago`, `Nh ago`, `Yesterday` (24–47 h) or `Nd ago`.
- Interaction scaffolding, all implemented and exercised in-game:
  - Pull down from the status bar opens the shade; releasing past 1/3 opens it fully.
  - Dragging up past the end of the list closes it.
  - Vertical drag and the mouse wheel scroll the list; a thin scroll indicator appears on overflow.
  - Swiping a card right (more than ¼ of its width) expands it. Swiping left dismisses it, with hint panels revealed behind the card.
  - A tap on a compact card expands it; a tap on the chevron or an expanded header collapses it.
  - Mark as Read dismisses the notification; Clear All dismisses everything shown.
- Dismissing only removes entries from the prototype's own list. Nothing touches Notification API V2, and no underlying action is resolved.
- Cards are measured from their content, so V3 data needs no layout rewrite.

### 3.4 Visible side effect on existing screens

The Setup and Bank screens use the same `PhoneUi` and now render in the navy/cyan OS palette and the new 1:2 casing. Their content and layout are unchanged (see `93_phone_grid`, `92_phone_setup` and `97_phone_bank` in the bundle). I judged copper accents on the new navy wallpaper to be the inconsistent choice. **Please confirm this is acceptable**, since the prompt says not to redesign the apps.

---

## 4. Testing

### 4.1 Automated

- `./gradlew build --offline`: **BUILD SUCCESSFUL**.
- Full `./gradlew test --rerun` with fresh results: **229 classes, 2,358 tests, 0 failures, 0 errors, 2 skipped** (both pre-existing opt-in tests). XML is in the bundle under `verification/test-results/`.
- New `PhonePhase1PrototypeTest` (7 tests):
  - Relative timestamps.
  - Newest-first ordering, and highest unread urgency with total count (the indicator hides at zero).
  - Snap open/close and expand/dismiss.
  - Badge 99+.
  - **Prototype data off in normal play** (no badges, no notifications).
  - Home-screen order, dock not repeated, Bank still on the Entitlement API, no "TOTALITY" carrier, no OS colours in the device style.
  - The slot icon is 16×16 single-colour line art in exactly the ring icon's grey.
- Updated tests: `PhoneDeviceLayoutTest` (1:2 aspect, tall not tablet, sprites match the style) and `PhoneCodexAppTest`. `EntitlementWiringSourceRegressionTest` passes unchanged.

### 4.2 In-game capture (real client)

How to reproduce: run scenes 45 and 64 with the capture harness:

```
JAVA_TOOL_OPTIONS="-Dtotality.hologram.capture=true -Dtotality.hologram.capture.scenes=45,64" \
  ./gradlew runClient --offline --args="--gameDir $PWD/build/phone-phase1-capture-r1080 --width 1920 --height 1080"
```

Pre-write `options.txt` with:

```
pauseOnLostFocus:false
onboardAccessibility:false
tutorialStep:none
```

The exact script is `tools/run_capture.sh` in the bundle.

For manual play testing, add `-Dtotality.phone.prototype=true` to show the dev pages, badges and test notifications.

Results:
- **1920×1080 run:** 29/29 checks PASS. The Wayland compositor gave a **1920×1012** window, so GUI scales 4, 3, 2 and 1 are all available.
- **1280×720 run:** 29/29 checks PASS. GUI scales 3, 2 and 1; scale 4 isn't available at 720p and was skipped automatically.

Checks cover:
- Setup → home; normal play has a single page with no indicator; the dock order.
- Codex is first and stays a no-op; keyboard focus; TAB close; ESC from Bank back to home.
- The prototype opens on the centre page; dot navigation both ways; a short drag springs back; a long swipe turns the page; keyboard back to main.
- Green (2), orange (3) and red (8) indicators.
- Shade: open by tap; tap to expand; swipe right expands; header click collapses; swipe left dismisses; Mark as Read dismisses; drag up closes; pull down opens; Clear All empties it and hides the indicator; ESC closes the shade but keeps the Phone open.

Logs: `verification/capture-r1080.log` and `verification/capture-r720.log`.

### 4.3 Screenshot index

In the bundle under `screenshots/r1080/` and `screenshots/r720/`, with the harness's `notification_v2_` filename prefix.

| Shot | Shows |
|---|---|
| `p1_00_open_t1..t4` | Slide-in and power-on |
| `p1_01_home_main` | Main home page: badges (3, 99+, 1, 2, 12, 4, 1), dev-page dots with the house marker |
| `p1_02_slide_to_right_t1..t5`, `p1_04_slide_to_left_t1..t5` | Page slide frames (stationary status bar and dock) |
| `p1_03_dev_page_right`, `p1_05_dev_page_left` | Development pages |
| `p1_06_page_drag_partial` | Grid following a sideways drag |
| `p1_07_status_green`, `p1_08_status_orange`, `p1_09_status_red` | Status-bar indicators |
| `p1_10_shade_opening_t1..t4`, `p1_11_shade_compact` | Shade opening; compact list that needs scrolling (scroll indicator) |
| `p1_12_shade_scrolled_to_end` | Scrolled list |
| `p1_13_shade_expanded` | Expanded Quests card, hovered Mark as Read |
| `p1_14_swipe_right_partial`, `p1_15_swipe_right_expanded` | Swipe-right hint, then the result |
| `p1_16_swipe_left_partial`, `p1_17_after_dismiss` | Swipe-left dismiss hint, then the result |
| `p1_18_drag_up_closing`, `p1_19_pull_down_opening` | Close and open drags mid-gesture |
| `p1_20_shade_empty`, `p1_21_home_no_notifications` | Empty state; indicator hidden |
| `p1_22_label_stress` | 12 increasingly long test labels (right dev page) |
| `p1_30_home_guiN`, `p1_31_shade_guiN`, `p1_32_home_normal_guiN` | Each GUI scale: prototype home, expanded shade, normal-play home |
| `p1_40_equipment_slot_gui4/3/2` | Phone slot icon in the real equipment panel |
| `90`–`99_*` | Scene 45 (existing flow): held item, setup, home, hover/locked tooltip/dock hover, keyboard focus, press, Bank, night, Codex states |

---

## 5. Fit and scaling findings

Display sizes come from the layout code and match the screenshots. The 1080p row is the 1012-px-tall window the compositor allowed.

| Window / GUI | Phone (GUI px) | Display width | Status text | Label scale (screen px per font px) | All 12 labels fit? |
|---|---|---|---|---|---|
| 1080p / 4 (default) | 121×241 | 105 | 0.75 | 0.5 (2 px) | Yes |
| 1080p / 3 | 163×325 | 147 | 1.0 | 0.667 (2 px) | Yes |
| 1080p / 2 | 200×400 | 184 | 1.0 | 1.0 (2 px) | Yes |
| 1080p / 1 | 200×400 | 184 | 1.0 | 1.0 (1 px) | Yes (phone is 40% of the window height) |
| 720p / 3 (default) | 114×228 | 98 | 0.667 | 0.667 (2 px) | **No**: "Technol...", "Invento..." |
| 720p / 2 | 174×348 | 158 | 1.0 | 1.0 (2 px) | **No**: "Technol..." (Inventory fits) |

**The label stress test** (`p1_22_label_stress`, 1080p/GUI 4) used, shortest to longest: Map, Classes, Settings, Inventory, Technology, Achievements, Notifications, World Map, Quest Journal, Trading Post, Player Statistics, Very Long Application Name.
- One-word labels fit up to 64 px of font width at full size. That includes "Technology" and "Notifications" (13 narrow characters). "Achievements", with wide letters, ends in "...".
- "World Map" and "Trading Post" fit on one line.
- "Quest / Journal" and "Player / Statistics" wrap onto two lines.
- "Very Long / Application..." ends in "..." on its second line.

There is **no clipping or overlap** at any captured size: labels never leave their column, badges stay inside the grid area, and the padlock sits beside its icon without touching the label.

At 720p (default GUI 3), "Technology" and "Inventory" can't fit at any crisp size that stays readable; at 720p GUI 2, only "Technology" can't. The next crisp step down is 1 screen px per font px. Options are in §7.

There is no distortion: every size keeps exactly 1:2, and all casing sprites draw at 1 art px = 1 GUI px (nine-slice with tiled edges).

`MAX_HEIGHT` was raised from 300 to 400 GUI px. At 300, the GUI-2 phone was only 59% of a 1080p window's height, and 1× labels truncated. It is now 79%.

---

## 6. Known limitations

- Shade and badges use **development data only**. In normal play the indicator is hidden, badges are absent and the shade shows "No notifications". That is intentional until Notification V3.
- The 720p label truncation in §5.
- Gesture scaffolding is mouse-driven: press, drag past 4 px, release. Clicks therefore act on **release**, a deliberate change from the old press-to-open, so that a press can become a drag. Keyboard behaviour is unchanged.
- The shade has no keyboard navigation yet. ESC closes it.
- The dev pages and the main-page house marker are provisional artwork.
- The screenshots show 1920×1012 instead of 1080 because the Wayland compositor limits the window. GUI 4 behaves as at 1080p (both give a 253- or 270-px GUI height).

---

## 7. Awaiting your decision or visual approval

1. **Casing:** the 1:2 body, rim with engraved groove, corner screws, camera dot and chin microphone grille (`p1_01_home_main`, `p1_30_home_gui3`).
2. **OS look:** palette, wallpaper, uniform icon tiles and the neutral badge colour.
3. **Long labels at 720p:** choose one, or keep the truncation:
   - (a) accept "Technol..." at 720p;
   - (b) shorter display names (e.g. "Tech");
   - (c) a slightly wider body at small sizes.
4. **New app availability:** Technology, System and Camera are unlocked no-ops. Should Technology be locked until its API exists?
5. **Wallet removed** from the Phone (Bank covers the account).
6. **Setup and Bank recolour** (§3.4).
7. **Phone slot icon** (`p1_40_equipment_slot_gui4`): outline phone with screen, speaker, home dot and the item's side button.
8. **Main-page marker** (tiny house) and dev-page presentation.

Phase 1 stops here. Full Phone functionality, Notification V3, the Codex app and every postponed item in the brief are untouched.
