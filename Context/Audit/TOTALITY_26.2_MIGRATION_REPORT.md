# Totality — Minecraft 26.1.2 → 26.2 Migration Report

Branch: `migration/minecraft-26.2` (based on checkpoint `953a677`, migration baseline `46a3591`)
Date: 2026-07-16 (updated after Stefan's manual regression pass and the Heat Vision fix)

## 15.1 Environment

| Component | Final version |
|---|---|
| Minecraft | 26.2 |
| Fabric Loader | 0.19.3 |
| Fabric API | 0.154.2+26.2 |
| Fabric Loom (property / resolved) | 1.17-SNAPSHOT / 1.17.14 |
| Gradle | 9.5.1 |
| Java | 25 (Eclipse Adoptium 25.0.2.10-hotspot) |

## 15.2 Changed files, grouped by cause

**Screen API** (`Minecraft.screen`/`setScreen` → `Minecraft.gui.screen()`/`gui.setScreen`):
`client/dialogue/ClientDialogueManager.java`, `client/economy/ClientBankTellerManager.java`,
`client/item/AttunementClientManager.java`, `client/quest/ClientQuestManager.java`,
`client/renderer/hud/context/AbilityContextHud.java`, `client/shop/ClientTradeManager.java`,
`init/TotalityKeybindHandlers.java`, `init/TotalityMovementHandler.java`,
`item/energy/PhoneItem.java`, `networking/TotalityClientPacketHandlers.java`,
`networking/dice/DiceRollResultClientHandler.java`, `mixin/client/LocalPlayerContainerMixin.java`,
`screen/ability/AbilityRadialScreen.java`, `screen/ability/SpellRadialScreen.java`,
`screen/ancestry/ConfirmAncestryScreen.java`, `screen/ancestry/OriginSelectionScreen.java`,
`screen/ancestry/SpeciesSelectionScreen.java`, `screen/character/CharacterScreen.java`,
`screen/character/tabs/ClassTab.java`, `screen/economy/BankTellerScreen.java`,
`screen/equipment/AccessoryInventoryButton.java`, `screen/inventory/TotalityInventoryScreen.java`,
`screen/magic/GrimoireRadialScreen.java`, `screen/menu/AbilitiesScreen.java`,
`screen/phone/BankScreen.java`, `screen/phone/PhoneAppGridScreen.java`,
`screen/phone/PhoneScreens.java`, `screen/phone/PhoneSetupScreen.java`,
`screen/quest/QuestScreen.java`,
plus `screen/classes/ClassSelectionScreen.java`, `ConfirmClassScreen.java`,
`CovenantSelectionScreen.java`, `SubclassSelectionScreen.java` (revealed after the first 100-error cap cleared).

**HUD/options API** (`Options.hideGui` → `Minecraft.gui.hud.isHidden()`):
`client/hud/resource/SecondaryResourceHud.java`, `client/hud/rest/RestHud.java`,
`client/quest/QuestTrackerHud.java`, `client/renderer/hud/MobHealthBarHud.java`,
`client/renderer/hud/TotalityHudRenderer.java`, `client/renderer/hud/notification/NotificationManager.java`.

**Tags** (removed `BlockTags` ore/log constants → `ModTags.VANILLA_*` passthrough constants):
`init/ModTags.java` (new constants added), `api/ability/impl/VeinminerAbility.java`,
`api/ability/kryptonian/HeatVisionAbility.java`, `api/rpg/skills/mining/MiningXpTable.java`,
`api/rpg/skills/mining/ProspectorGemTable.java`.

**Mappings** (renamed/moved vanilla symbols):
`item/magic/rune/effect/HarvestEffect.java` (`BlockPos.getBottomCenter()` → `Vec3.atBottomCenterOf`),
`item/magic/rune/effect/LightningEffect.java` (`EntityType.LIGHTNING_BOLT` → `EntityTypes.LIGHTNING_BOLT`),
`worldgen/ModBiomes.java` (`EntityType.FROG`/`SLIME` → `EntityTypes.FROG`/`SLIME`).

**Datagen**:
`datagen/ModBlockTagProvider.java`, `datagen/ModItemTagProvider.java`
(`valueLookupBuilder` → `builder`, block/item `.add(X)` → `.add(key(X))` since
`BlockItemTagAppender.add` now takes a `ResourceKey`, not the registry object),
`datagen/ModBlockLootTableProvider.java` (`StatePropertiesPredicate` moved
`net.minecraft.advancements.criterion` → `net.minecraft.advancements.predicates`).

**Rendering**:
`api/client/gui/TotalityGuiRenderer.java`, `client/renderer/ability/HeatVisionBeamRenderer.java`,
`client/renderer/energy/SidedOverlayRenderer.java` (full GPU-pipeline rewrite, see §15.3;
both renderers additionally required a second fix after manual testing — see §15.6b).

**Mixins**:
`mixin/client/ItemInHandRendererMixin.java` (retargeted renamed methods),
`mixin/GameRendererMixin.java` (dropped now-invalid injection),
`mixin/client/GuiExtractRenderStateMixin.java` (**new file** — the injection moved here),
`mixin/client/LocalPlayerContainerMixin.java` (redirect target moved `Minecraft`→`Gui`),
`src/main/resources/totality.mixins.json` (registered the new mixin).

**Worldgen** (genuine vanilla behavior change, not a syntax migration):
`worldgen/TotalityOverworldClimate.java` (`EXPECTED_VANILLA_POINT_COUNT`/`_FINGERPRINT` bumped for
vanilla's new Sulfur Caves biome — see §15.6),
`worldgen/TotalityOverworldSurfaceRules.java` (`SurfaceRules.isBiome`/`noiseCondition` signature changes).

**Generated resources** (datagen re-run output — see §15.6 for why these changed):
`src/main/generated/data/minecraft/worldgen/noise_settings/overworld.json`,
17 files under `src/main/generated/data/totality/loot_table/blocks/*.json`,
4 files under `src/main/generated/data/totality/recipe/*.json`.

**Not touched by this migration** (called out explicitly because they came up in manual testing):
the Fluid Tank's model/texture (`assets/totality/models/block/copper_tank.json`,
`models/item/copper_tank.json`, `textures/block/copper_tank.png`) were not modified —
its visual issue predates this migration (see §15.6c). The ability/spell hold-input keybind
logic (`init/TotalityKeybindHandlers.java`) was only touched for the screen-API rename
(§15.2); its hold-gesture conflict (see §15.6d) is a pre-existing design gap, not something
introduced here.

## 15.3 API migration table

| Old (26.1.2) | New (26.2) | Files | Syntactic/Semantic | Runtime verification | Remaining uncertainty |
|---|---|---|---|---|---|
| `Minecraft.screen` field | `Minecraft.gui.screen()` | 30 files (§15.2) | Syntactic (field moved onto `Gui`, identical semantics — confirmed by reading `Gui.java`) | Verified: dev client opened dialogue via join, quest screen loaded; Stefan's manual pass exercised character/phone/grimoire/ability/spell/accessory screens with no errors | Low — representative screens exercised, full transition matrix not exhaustively tested |
| `Minecraft.setScreen(x)` | `Minecraft.gui.setScreen(x)` | Same 30 files | Syntactic | Verified in same client runs | Low — see above |
| `Minecraft.getInstance().getMainRenderTarget()` | `Minecraft.getInstance().gameRenderer.mainRenderTarget()` | `HeatVisionBeamRenderer`, `SidedOverlayRenderer` | Syntactic | **Verified at runtime.** Heat Vision: initially crashed (see §15.6b), fixed, retested clean. Energy side overlay: functionally confirmed working by Stefan's manual pass | Heat Vision visual appearance regressed after the fix (§15.6c) — not a call-site issue, see below |
| `Options.hideGui` | `Minecraft.gui.hud.isHidden()` | 6 HUD files | Syntactic (field relocated from `Options` onto the new `Hud` class) | **Verified** — Stefan confirmed F1 correctly hides/reshows the Totality HUD | None |
| `BlockTags.COAL_ORES`/`DIAMOND_ORES`/`EMERALD_ORES`/`LAPIS_ORES`/`REDSTONE_ORES`/`LOGS_THAT_BURN` | Removed as Java constants; underlying `data/minecraft/tags/block/*.json` files still ship — referenced directly via `TagKey.create(Registries.BLOCK, Identifier.withDefaultNamespace(...))` in new `ModTags.VANILLA_*` constants | `VeinminerAbility`, `HeatVisionAbility`, `MiningXpTable`, `ProspectorGemTable` | Semantic-preserving (same underlying vanilla tag data, just no compile-time constant) | **Verified** — Stefan confirmed Veinminer works with the migrated ore-tag handling | Heat Vision's log-burning interaction with `LOGS_THAT_BURN` was not separately called out in the manual pass |
| `BlockPos.getBottomCenter()` | `Vec3.atBottomCenterOf(BlockPos)` | `HarvestEffect.java` | Syntactic (confirmed identical vanilla implementation via decompile diff) | Not individually re-tested in Stefan's pass | Harvest rune drop-position still worth a dedicated look |
| `EntityType.LIGHTNING_BOLT` / `.FROG` / `.SLIME` (and all other `EntityType.X` constants) | `EntityTypes.LIGHTNING_BOLT` / `.FROG` / `.SLIME` (constants moved to new class `net.minecraft.world.entity.EntityTypes`) | `LightningEffect.java`, `ModBiomes.java` | Syntactic | Not individually re-tested in Stefan's pass | Lightning rune effect still worth a dedicated look |
| `FabricTagsProvider.valueLookupBuilder(tag)` | `FabricTagsProvider.builder(tag)`, returning `BlockItemTagAppender` whose `.add()` takes a `ResourceKey`, not the registry object | `ModBlockTagProvider`, `ModItemTagProvider` | Semantic-preserving, but required wrapping every `.add(X)` as `.add(key(X))` via a new `key()` helper | Verified: datagen ran clean, generated block/item tag JSON is byte-identical to pre-migration output | None |
| `net.minecraft.advancements.criterion.StatePropertiesPredicate` | `net.minecraft.advancements.predicates.StatePropertiesPredicate` | `ModBlockLootTableProvider.java` | Syntactic (package move only) | Verified: `true_wheat_crop.json` loot table regenerated with the block-state condition intact | None |
| `VertexFormat.Mode` / `RenderPipeline.getVertexFormatMode()`/`getVertexFormat()` | `PrimitiveTopology` / `RenderPipeline.getPrimitiveTopology()`/`getVertexFormatBinding(0)`; pipeline builder now uses `.withVertexBinding(int, VertexFormat)` + `.withPrimitiveTopology(PrimitiveTopology)` instead of one `.withVertexFormat(fmt, mode)` call | `TotalityGuiRenderer`, `HeatVisionBeamRenderer`, `SidedOverlayRenderer` | Semantic-preserving rename (confirmed via `RenderPipelines`/`DebugCrosshairRenderer` vanilla source) | GUI rendering (borders, panels, HUD) confirmed working. Heat Vision/side overlay: confirmed working after the §15.6b fix, with the known visual regression in §15.6c | See §15.6c |
| `CommandEncoder.mapBuffer(slice, r, w)` returning `GpuBuffer.MappedView` | `GpuBufferSlice.map(r, w)` returning `GpuBufferSlice.MappedView` (mapping moved onto the slice itself, no encoder needed) | `HeatVisionBeamRenderer`, `SidedOverlayRenderer` | Syntactic (confirmed via vanilla `CloudRenderer` source) | Verified at runtime (see §15.6b) | None beyond §15.6c |
| `RenderSystem.getSequentialBuffer(VertexFormat.Mode)` | `RenderSystem.getSequentialBuffer(PrimitiveTopology)` | Same 2 renderers | Syntactic | Verified at runtime | None |
| `RenderPass.setVertexBuffer(int, GpuBuffer)` | `RenderPass.setVertexBuffer(int, GpuBufferSlice)` — call `.slice()` on the buffer | Same 2 renderers | Syntactic | Verified at runtime | None |
| `RenderPass.drawIndexed(firstIndex, baseVertex, indexCount, instanceCount)` (4 args) | `RenderPass.drawIndexed(indexCount, instanceCount, firstIndex, baseVertex, firstInstance)` (5 args, Vulkan-style) | Same 2 renderers | **Semantic** — argument order and count both changed; confirmed against vanilla `DebugCrosshairRenderer`/`CloudRenderer` usage | Verified at runtime | None |
| `MappableRingBuffer` used once per box/position | Same class, but the original per-box/per-position draw pattern (up to 6 draws/frame for Heat Vision) violates 26.2's stricter GPU fence rules — see §15.6b | `HeatVisionBeamRenderer`, `SidedOverlayRenderer` | **Semantic regression found via manual testing, not part of the original migration pass** — required batching all geometry into one draw per frame | **Crashed, then fixed and retested clean** (§15.6b) | Heat Vision visual appearance regressed (§15.6c); `SidedOverlayRenderer`'s identical fix has not been independently stress-tested with 4+ pinned overlays |
| `VertexFormat.uploadImmediateIndexBuffer(ByteBuffer)` (for sorted translucent quads) | Removed; `MeshData.sortQuads(...)` now returns a `SortState`, and the sorted index bytes must be uploaded manually via `RenderSystem.getDevice().createBuffer(label, GpuBuffer.USAGE_INDEX, byteBuffer)` — an ephemeral buffer we now explicitly `.close()` after the draw | `SidedOverlayRenderer.java` | Semantic — required a small manual buffer-lifetime addition (`ownsIndices` flag) not present in the old code | Verified: Stefan confirmed the energy side overlay renders correctly | None |
| `Minecraft.getMainRenderTarget()` (used in `createRenderPass`) + `OptionalInt.empty()` for the clear-color param | `gameRenderer.mainRenderTarget()` + `Optional.empty()` (the clear-color param is `Optional<Vector4fc>`, not `OptionalInt` — this was silently wrong before too, just masked by the `getMainRenderTarget()` compile error) | Same 2 renderers | Semantic (type correction) | Verified at runtime | None |
| `ItemInHandRenderer.renderArmWithItem(...)` / `.renderHandsWithItems(...)` | `.submitArmWithItem(...)` / `.submitHandsWithItems(...)` (renamed, identical parameter lists) | `mixin/client/ItemInHandRendererMixin.java` | Syntactic (confirmed via javap — same descriptors, new names) | **Verified at runtime** — this exact mismatch crashed the dev client twice before the fix. **Manually confirmed** by Stefan: dual-wield offhand swing and blocking arm pose both still work correctly | None |
| `GameRenderer.extractGui()` (had a local `graphics` var, called `Gui.extractRenderState(graphics, deltaTracker)`) | Folded into `GameRenderer.extract(DeltaTracker, boolean)`, which now calls `Gui.extractRenderState(DeltaTracker, boolean, boolean)` — the `graphics` local moved *inside* that `Gui` method | `mixin/GameRendererMixin.java` (injection removed), new `mixin/client/GuiExtractRenderStateMixin.java` (injection re-created, now targeting `Gui` instead of `GameRenderer`) | **Semantic retarget** — the injection had to move to a different class entirely, since the local it captures no longer exists in `GameRenderer` | **Verified at runtime** — crashed the dev client, fixed, confirmed gameplay | Floating combat text (`CombatTextRenderer.onExtractGui`) itself wasn't specifically called out in Stefan's manual pass |
| `Minecraft;setScreen(Screen)` (mixin `@Redirect` target for `LocalPlayer.clientSideCloseContainer`) | `Gui;setScreen(Screen)` (vanilla's own `clientSideCloseContainer()` now calls `this.minecraft.gui.setScreen(null)` directly) | `mixin/client/LocalPlayerContainerMixin.java` | Syntactic | **Verified** — Stefan's pass included equipment-screen interaction with no reported screen-swap flash issue | None reported |
| `SurfaceRules.isBiome(ResourceKey<Biome>...)` | `SurfaceRules.isBiome(HolderGetter<Biome>, ResourceKey<Biome>...)` | `TotalityOverworldSurfaceRules.java` | Semantic (new required parameter) — resolved via `provider.lookupOrThrow(Registries.BIOME)`, already in scope at the call site | Verified: datagen regenerated `overworld.json` with the Flooded Forest condition block intact | None |
| `SurfaceRules.noiseCondition(key, threshold)` | Split into `noiseCondition2d`/`noiseCondition3d`; confirmed 2D is correct by finding vanilla's own analogous Swamp-puddle rule (`SurfaceRules.noiseCondition2d(Noises.SWAMP, 0.0)`) uses the same pattern | `TotalityOverworldSurfaceRules.java` | Syntactic once the correct overload was identified | Verified via datagen | None |
| `OverworldBiomeBuilder.addBiomes(...)` point count | 7593 → 7594 (vanilla added a new **Sulfur Caves** underground biome — one new `addUndergroundBiome(..., Biomes.SULFUR_CAVES)` call, confirmed by diffing decompiled `OverworldBiomeBuilder` between 26.1.2 and 26.2) | `TotalityOverworldClimate.java` (`EXPECTED_VANILLA_POINT_COUNT`, `EXPECTED_VANILLA_FINGERPRINT` constants bumped) | **Genuine vanilla behavior change**, not a migration bug — the guard fired exactly as designed | Verified: datagen succeeded after the constants were corrected using the real captured fingerprint (not guessed) | Flooded Forest's own placement is unaffected (Sulfur Caves is an underground, non-Swamp biome) |
| `PhoneItem.use()` constructing two different `Screen` subtypes in a ternary | Moved into the existing client-only `PhoneScreens` helper (`openFromHand`) | `item/energy/PhoneItem.java`, `screen/phone/PhoneScreens.java` | **Latent pre-existing bug, not a 26.2 API change** — a ternary between two `Screen` subtypes inside a class loaded on both sides (`Item`) forces the JVM verifier to resolve the client-only `Screen` class merely by loading the class, crashing a dedicated server even though the branch is `isClientSide()`-guarded | **Verified at runtime** — this crashed the dedicated server on first launch; fixed, confirmed dedicated server reaches `Done` after the fix | Confirmed no other Item/Block class in the codebase has this pattern (grepped for `new *Screen(` construction outside `client`/`screen` packages) |

## 15.4 Build results

| Stage | Error count | Result |
|---|---|---|
| Baseline (`TOTALITY_26.2_FIRST_COMPILE.log`, pre-existing) | 100 (javac display cap) | FAILED |
| Reproduction (`TOTALITY_26.2_REPRO_COMPILE.log`) | 100 (identical) | FAILED — confirms reproducibility |
| After screen/HUD/tags/mappings/datagen groups (`COMPILE_GROUP1`) | 39 (new ones revealed past the 100-cap: `screen/classes/*`, `worldgen/ModBiomes.java`, `worldgen/TotalityOverworldSurfaceRules.java`) | FAILED |
| After worldgen + remaining screen-API stragglers (`COMPILE_GROUP2`) | 26 (all in the 3 rendering files) | FAILED |
| After GPU rendering pipeline rewrite (`COMPILE_GROUP3`) | 0 | **BUILD SUCCESSFUL** |
| `gradlew.bat clean build` (`CLEAN_BUILD.log`) | 0 | **BUILD SUCCESSFUL** — jar assembled, no Mixin/AP errors, no remap failures |
| `runDatagen` (`DATAGEN_FINAL.log`) | 0 | **BUILD SUCCESSFUL** — all 9 providers ran, 349 files written |
| Post-Heat-Vision-fix recompile (`COMPILE_HEATVISION_FIX.log`) | 0 | **BUILD SUCCESSFUL** |

**Warnings remaining:**
- `Note: Some input files use or override a deprecated API` — pre-existing, not investigated, out of scope for this migration.
- A Gradle 10-incompatibility deprecation warning remains unattributed and was not investigated during this migration (no `--warning-mode all` run was performed to trace its source). It does not block the Minecraft 26.2 build.

## 15.5 Runtime results — status matrix

| Area | Status |
|---|---|
| Build compatibility | **Passed** |
| Datagen compatibility | **Passed** |
| Dedicated-server startup | **Passed** (after one fix) |
| Development-client startup | **Passed** (after two fixes) |
| Fresh/current-world gameplay smoke test | **Passed** (manual — see §15.5.1 and the companion `TOTALITY_26.2_MANUAL_SMOKE_TEST_RESULTS.md`) |
| Representative networking flows | **Partially verified — tested flows passed** (see below) |
| Heat Vision stability | **Passed** (after the §15.6b fix) |
| Heat Vision visual parity with 26.1.2 | **Known regression** (§15.6c) — not fixed, deferred |
| Fluid Tank functionality | **Passed** |
| Fluid Tank visual completion | **Known pre-existing issue, not a migration regression** (§15.6c) — deferred |
| Reproducible old-save compatibility | **Not tested** (§15.6e) |
| Mixin validation | **Passed** for the tested startup and gameplay paths (2 broken targets found and fixed; behavior of the affected Mixins manually confirmed — dual-wield, blocking arm pose, screen swap) |

### 15.5.1 Dedicated-server and development-client startup detail

| Test | Status | Notes |
|---|---|---|
| Dedicated-server startup | **Passed** (after one fix) | First attempt crashed (`PhoneItem` client-classloading bug, §15.3). Second attempt: reached `Done (2.382s)!`, fresh world generated, all registries/reload-listeners initialized, 4 of 5 in-game self-test suites passed (`ItemValueVerification` 19/19, `MerchantSellVerification` 33/33, `ProvisionerVerification` 67/67, `TradingScreenVerification` 14/14). `ProvisionerEntityBackedSmokeTest` failed 3/4 checks on a **truly empty** server (no connected player) — see §15.7, this is not a migration regression. |
| Development-client startup | **Passed** (after two fixes) | First two attempts crashed on Mixin target mismatches (`ItemInHandRenderer`, `GameRenderer` — §15.3). Third attempt: reached the title screen, joined the existing dev world, ticked normally, rendered chunks/entities, and `ProvisionerEntityBackedSmokeTest` passed 4/4 with a real connected player — confirming the server-side "failure" above was an environment condition, not a bug. |

### 15.5.2 Networking validation — corrected classification

**Partially verified — representative flows passed.** Client connection and component/state synchronization succeeded (component sync on join, quest state sync, dialogue manager state). During Stefan's manual regression pass, dialogue, quests, economy (BUY/SELL, banking), rest, and screen-opening packet-driven flows all worked correctly. The entire networking surface was **not** exhaustively tested — registration and representative runtime behavior passed, but this is not equivalent to exhaustive payload verification across every packet type, every validation branch, or every rejection path.

### 15.5.3 Screen API — corrected remaining uncertainty

Not `None`. **Low remaining uncertainty.** Representative screens and transitions (character, phone, grimoire, ability radial, spell radial, accessory inventory, dialogue, quest, trading, banking) were manually exercised successfully by Stefan with no reported errors, but the full screen-transition matrix (every possible screen-to-screen path, every entry point) was not exhaustively tested.

## 15.6 Behavior changes

Unavoidable/intentional differences from 26.1.2, all confirmed via decompiled-source diffing (not guessed):

1. **Vanilla loot-table/recipe JSON no longer serializes default values** (`bonus_rolls: 0.0`, `add: false`, `category: "misc"` are now omitted instead of written explicitly). Purely a codec/serialization format change; loaded values are identical. Affects the 17 regenerated loot tables and 4 recipes.
2. **Vanilla `biome_is` condition now serializes as a bare string instead of a single-element array** (`"biome_is": "minecraft:swamp"` instead of `"biome_is": ["minecraft:swamp"]`), and vanilla's noise-router JSON gained a new `cache_once` wrapper node type. Both are vanilla codec changes in `overworld.json`, unrelated to Totality's own surface-rule logic (which is unchanged and still present in the diff).
3. **Vanilla added a new Sulfur Caves underground biome** in `OverworldBiomeBuilder`, changing its real point count from 7593 to 7594. `TotalityOverworldClimate`'s self-verification guard (designed exactly for this scenario) caught it; the guard's expected constants were updated to the real, captured values — not guessed. Sulfur Caves is not a Swamp point, so Flooded Forest's own placement logic is unaffected.
4. **`RenderPass.drawIndexed`'s argument order changed** from `(firstIndex, baseVertex, indexCount, instanceCount)` to `(indexCount, instanceCount, firstIndex, baseVertex, firstInstance)` — a real semantic change in the rendering pipeline, not just a rename. Both custom renderers were updated to match.
5. **26.2's GPU fence validation is stricter than 26.1.2's** — see §15.6b. A rendering pattern (multiple draws per frame through a 3-slot ring buffer) that was unsafe but silently tolerated under the old backend now throws instead.

No other intentional behavior changes were introduced. Everywhere a 26.2 API required a different call shape, the smallest change that preserved the original semantics was used (documented per-row in §15.3).

## 15.6b Heat Vision crash found during manual testing, and its fix

Stefan manually tested Heat Vision and hit a real client crash:

```
java.lang.IllegalStateException: Cannot wait on a fence for the current submit
    at com.mojang.blaze3d.opengl.GlCommandEncoder.awaitSubmit
    at com.mojang.blaze3d.opengl.GlFence.awaitCompletion
    at net.minecraft.client.renderer.MappableRingBuffer.currentBuffer
    at zcylas.totality.client.renderer.ability.HeatVisionBeamRenderer.draw
```

Full crash report preserved at `Context/Audit/TOTALITY_26.2_HEATVISION_CRASH_REPORT.txt`
(original: `run/crash-reports/crash-2026-07-16_12.56.56-client.txt`).

**Root cause:** `HeatVisionBeamRenderer` rendered the beam as 6 separate boxes per frame (2 eye-offsets × 3 glow layers), each with its own `BufferBuilder` → `MappableRingBuffer` upload → `RenderPass` draw cycle. `MappableRingBuffer` only has 3 buffer slots; each slot's GPU fence is created by `rotate()` and awaited by the next `currentBuffer()` call on that same slot. With 6 draws per frame cycling through 3 slots, the 4th draw wraps back to a slot whose fence was created moments earlier in the *same, not-yet-submitted* frame. MC 26.2's GL fence implementation now throws instead of the older backend's lenient wait — a genuine behavior tightening in 26.2 that had not produced the same visible failure under the prior 26.1.2 rendering implementation, even though the underlying draw pattern predates this migration.

`SidedOverlayRenderer` had the identical latent pattern (one draw per pinned block position, unbounded by anything but how many machines a player has pinned) — not triggered in testing (Stefan's pass didn't have 4+ overlays pinned simultaneously), but would crash the same way under those conditions.

**Fix:** both renderers now build one shared `BufferBuilder` per frame (accumulating all boxes/faces into it) and issue a single upload+draw call, instead of one per box/position. Same geometry, same colors, same intended visual output — just one GPU round-trip per frame instead of several.

**Result after the fix:**
- Runtime stability: **Passed**
- Ability activation: **Passed**
- Sustained use / repeated activation: **Passed** — no further fence crash
- Crash regression: **Fixed and manually retested** (confirmed clean, `Context/Audit/TOTALITY_26.2_POSTFIX_MANUAL_TEST_CLIENT_LOG.log`, no `IllegalStateException`, no fence errors, clean session end)

`SidedOverlayRenderer`'s identical fix (applied preemptively, same session) has not been independently stress-tested with 4+ pinned overlays visible at once, but shares the exact same fix shape and the energy side overlay was confirmed working in Stefan's manual pass under normal (fewer-overlay) conditions.

## 15.6c Heat Vision visual regression (known, deferred)

Heat Vision is functionally stable under Minecraft 26.2 after batching all beam geometry into a single upload and draw call per frame. The previous GPU-fence crash no longer occurs. **The new renderer does not preserve the previous appearance and currently has reduced visual quality**, per Stefan's direct observation after retesting. The previous (26.1.2-era) beam was itself experimental and not considered visually final before this migration. Visual restoration is deferred to a dedicated renderer rework — it is not a migration-blocking system, and no attempt was made to redesign or improve the visuals as part of this documentation closeout.

- **Heat Vision stability:** Passed
- **Heat Vision visual parity with 26.1.2:** Failed / known visual regression
- **Overall Heat Vision verification:** Partially verified

## 15.6d Ability/spell hold-input conflict (deferred — separate design issue)

During the same manual testing, Stefan identified that holding the Ability key opens the Ability radial menu, and holding the Spell key opens the Spell radial menu — but some present or future abilities and spells (Heat Vision among them) also need hold-to-channel or hold-to-charge behavior of their own. The input system currently has no deliberate distinction between a tap, a radial-menu hold, a channel/charge hold, and a release.

This is a **pre-existing `TotalityKeybindHandlers` design gap**, not something introduced by this migration (the file was only touched here for the mechanical screen-API rename, §15.2). It is classified as:

**Deferred — input-system design issue.**

It was not solved as part of the 26.2 migration, is not a migration regression, and does not block migration completion. No design or implementation work toward a solution was done in this task, per its documentation-only scope.

**RESOLVED (2026-07-16), post-migration follow-up work — Phase 4 correction pass.** A modifier-chord input model (Radial Modifier, default Left Alt, rebindable) replaced the hold-threshold radial trigger: Z/X alone activate normally with the full hold/channel gesture available with no timeout; Radial Modifier+Z/X opens the respective radial without activating anything. Full detail in `TOTALITY_COMBAT_INPUT_AND_HUD.md` Section 2. This note records the resolution; it does not reopen or rewrite this migration's own conclusion (§15.9) — the fix was later, separate work, not a correction to the migration itself.

## 15.6f Post-migration HUD/notification timing regressions (found and fixed later — not part of this migration)

Two additional issues surfaced during the SAME later Phase 4 correction pass (2026-07-16) that also touched §15.6d above, unrelated to that item: Totality's custom notifications (`NotificationManager`) and the Power Attack screen-corner flash (`PowerAttackFlash`) both stayed visible for a noticeably shorter real-world duration after this migration than before it. Root-caused to a pre-existing bug in BOTH systems (their authoritative lifetime timers were decremented once per rendered HUD frame rather than once per game tick — confirmed via `git diff` against this migration's own baseline commit, `953a677`, that neither system's code nor Fabric's HUD-callback invocation mechanism actually changed during this migration). The bug was always frame-rate dependent; it became severely more noticeable specifically because this migration's GPU-pipeline rewrite (§15.3/§15.6b) legitimately increased achieved frame rates for the same scene. Both were fixed by moving each system's lifetime decrement to its own `ClientTickEvents.END_CLIENT_TICK` registration. Full root-cause analysis and fix detail in `TOTALITY_COMBAT_INPUT_AND_HUD.md` Sections 3-5. Recorded here as a genuine post-migration regression in user-visible behavior (not a migration-introduced code defect) that was later found and corrected — this note does not reopen or rewrite this migration's own conclusion (§15.9).

## 15.6e Fluid Tank (known pre-existing issue, not a migration regression)

The Fluid Tank places, loads, and its tested functionality (screen, interaction) remains operational under Minecraft 26.2. Its model and texture (`assets/totality/models/block/copper_tank.json`, `models/item/copper_tank.json`, `textures/block/copper_tank.png`) are a known pre-existing placeholder or incomplete visual implementation, sourced originally as temporary reference material and never replaced with a finished Totality-specific design. **None of these files were touched by this migration** (confirmed via `git status`). The tank's final visual design is deferred to the dedicated future Fluid API phase.

- **Fluid Tank functionality:** Passed
- **Fluid Tank screen/interactions:** Passed
- **Fluid Tank visual quality:** Known pre-existing issue
- **Migration regression:** No
- **Resolution:** Deferred to the future Fluid API work

## 15.6f Old-save compatibility (corrected classification)

The only available Minecraft 26.1.2 test world (`run/saves/New Testing World`) was opened and upgraded to Minecraft 26.2 during this migration's automated testing, before this documentation closeout was requested. That world loaded and was subsequently used for testing (including Stefan's manual regression pass) without an immediately observed migration failure.

However:
- it was the only copy;
- no untouched 26.1.2 fixture remains;
- the upgrade cannot now be repeated;
- the before-and-after component state cannot be formally compared;
- it is not a reproducible compatibility test.

**Old-save compatibility: Not formally tested / no reproducible fixture available.**

The successful opening and continued use of that world is offered only as encouraging anecdotal evidence — it is not a substitute for a formal, reproducible old-save test against an untouched copy, and is not reported as "Passed."

**Future migration-process note:** preserve at least one zipped, untouched pre-migration save fixture before opening any world in a new Minecraft version. This was not done before this migration began, which is why no reproducible fixture is available now.

## 15.7 Deferred issues

Explicitly out of scope for this migration, per the task's exclusion list — none of these were touched:

- Generic Player Resource API completion
- Unlock/Access/Entitlement API implementation
- Spell API redesign
- Class/origin completion, multiclass redesign
- Equipment death-policy redesign, Soulbound implementation
- Merchant runtime persistence redesign (see the `ProvisionerEntityBackedSmokeTest` note below)
- Provisioner Phase 4 GUI redesign
- Fluid API redesign, Thirst, Farming/Cooking, Medicine/Disease, Alchemy ecosystem redesign
- Enchantment/Smithing architecture
- Broad networking hardening, component schema migration architecture
- Broad performance optimization
- Future Fluid API, future system redesigns, performance profiling
- **Heat Vision visual rework** (§15.6c) — functional, not restored to prior appearance
- **Ability/spell hold-input redesign** (§15.6d) — input-system design issue, unsolved at
  migration close; **resolved 2026-07-16** by later, separate Phase 4 correction-pass work
  (Radial Modifier chord model) — see §15.6d's update and `TOTALITY_COMBAT_INPUT_AND_HUD.md`
- **Fluid Tank visual completion** (§15.6e) — pre-existing, not caused by this migration

**Findings surfaced during this migration that belong to separate, later work** (not fixed here, since fixing them would exceed migration scope):

- **Pre-existing "deprecated API" compiler notes** (`Note: Some input files use or override a deprecated API`) were not enumerated or investigated — they predate this migration and aren't new.
- **`ProvisionerEntityBackedSmokeTest` on a player-less dedicated server**: appears to require a connected player/session context to resolve the freshly-spawned NPC via the "real production entity lookup" — passed cleanly once a real player joined. Worth a look if Stefan ever runs this self-test suite on an empty dedicated server as part of CI, but it is not a 26.2 regression (confirmed: same test, same code, passes with a player present).
- **Gradle 10-incompatibility deprecation warning** — unattributed, not investigated (§15.4).

## 15.8 Manual testing

Automated/Claude-run testing covered compilation, build, datagen, dedicated-server startup, and bounded non-interactive client launches (no input-injection was available in that environment). All deep interactive gameplay verification — every item below — was performed by **Stefan, manually, in-game**, after the automated migration work completed. See the companion document `TOTALITY_26.2_MANUAL_SMOKE_TEST_RESULTS.md` for the itemized results table (test name, result, who performed it, automated vs. manual, evidence, deferred follow-up).

Summary of what Stefan confirmed as **Manual user verification — Passed**:

- Representative Totality GUIs open and close without errors; screen transitions during normal play work
- Totality HUD elements hide correctly with F1
- Provisioner BUY works; Provisioner SELL works
- Banking transactions work
- Rest can start; Rest can be cancelled; Rest can complete
- Dual-wield and blocking behavior still works; relevant first-person arm behavior works after the Mixin migration
- Veinminer works with the migrated ore-tag handling
- The energy machine functionality tested remains operational; the energy side overlay works
- Electric Furnace interaction and screen work
- Fluid Tank functionality and its screen work (visual quality is a known pre-existing issue, §15.6e)
- Reconnecting works
- Tested character, economy, equipment, quest, and related persistent state remains present after reconnect
- The remaining tested systems behaved normally and did not produce migration-related errors

Found during this same manual pass, and separately documented above:

- Heat Vision crashed the client (§15.6b) — root-caused, fixed, retested, confirmed no longer crashing; visual quality regressed (§15.6c), deferred
- Ability/spell hold-input conflict (§15.6d) — deferred, separate design issue, not fixed here

## 15.9 Final migration status

**Passed with documented non-blocking visual issues.**

Totality's migration from Minecraft 26.1.2 to Minecraft 26.2 is complete. Compilation, clean build, datagen, dedicated-server startup, development-client startup, world loading, and Stefan's targeted manual gameplay smoke test succeeded. The Heat Vision GPU-fence crash discovered during manual testing was fixed and retested, although its visual appearance has regressed and requires a later renderer rework. The Fluid Tank retains a pre-existing incomplete visual implementation that is deferred to the future Fluid API. Formal reproducible old-save compatibility remains untested because no untouched 26.1.2 fixture remains. None of these documented limitations blocks continuation of Totality development on Minecraft 26.2.

| Area | Status |
|---|---|
| Build compatibility | Passed |
| Datagen compatibility | Passed |
| Dedicated-server startup | Passed |
| Development-client startup | Passed |
| Fresh/current-world gameplay smoke test | Passed |
| Representative networking flows | Partially verified, tested flows passed |
| Heat Vision stability | Passed |
| Heat Vision visual parity | Known regression |
| Fluid Tank functionality | Passed |
| Fluid Tank visual completion | Deferred pre-existing issue |
| Reproducible old-save compatibility | Not tested |

**Development sequence going forward:**
1. Minecraft 26.2 migration completed.
2. Migration-related compile/runtime regressions fixed.
3. Targeted smoke testing completed with documented visual limitations.
4. Development may continue on Minecraft 26.2.
5. Return to the previously planned next implementation work after this migration closeout (not chosen or started as part of this task).

## 15.10 Review bundle

See `Context/Audit/Review Bundles/totality-26.2-migration-review-final.zip`, containing: this corrected report, `TOTALITY_26.2_MANUAL_SMOKE_TEST_RESULTS.md`, the original first-compile log, all compile-progression logs, the final clean-build log, the final datagen log, the successful dedicated-server startup log, the successful client startup log, the Heat Vision crash report and post-fix client-log excerpt, the changed-file list, this API replacement table, the final status matrix, the deferred-issues list, the old-save compatibility statement, and remaining warnings/uncertainty. Gradle caches, `build/`, `run/` worlds, `src/main/generated/.cache/`, and `/Inspiration Mods` are excluded.
