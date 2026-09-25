# Totality — Unified Code and Mining Baseline Audit (2026-09-21)

**READ-ONLY audit.** No gameplay source, dependency, balance value, Git ref or history was changed; nothing was staged or committed. Audit output is this file and one appendix under `Context/Audit/`. To obtain real runtime numbers, a **scratch copy of the repository outside the working tree** (in the session scratchpad) was built with one extra throw-away harness class (`AuditRepro`, reproduced in the appendix); it never touched this repository.

Confidence markers: **CODE** (read in this repository or the 26.2 sources), **RUNTIME** (observed by running the real code in the scratch copy or the dev suites), **DERIVED** (my calculation), **REFERENCE** (a wiki-type source, *not re-fetched in this session*: `https://minecraft.wiki/w/Breaking`, `https://minecraft.wiki/w/Pickaxe`, `https://minecraft.wiki/w/Efficiency`, `https://minecraft.wiki/w/Obsidian` — I cross-checked the vanilla numbers below only against my prior knowledge of those pages, which agree: bare-hand Stone 7.5 s, Diamond pickaxe Obsidian 9.4 s, Netherite 8.35 s).

---

## 0. Starting state and validation

| | Before | After |
|---|---|---|
| Branch / tip | `master` @ `7213407` (= `feature/block-breaking-api`) | unchanged |
| `git status --short` | empty (clean) | only the audit files below (untracked) |
| Branches | 13 refs (7 local + 6 remote), all as recorded in the integration audit | unchanged; `origin/master` still `bc16cc3` (nothing pushed) |

`git log`: `7213407 docs: record executed Block Breaking V1 integration` ← `ab9ef6a Merge master … into Block Breaking API v1` ← `1946a42 feat: implement Block Breaking API v1` / `3170e2f` (soul-gems) ← … The repository state matches the expected baseline.

**Validation run on `master` (this audit):**

| Check | Result |
|---|---|
| `bash gradlew build --offline` | BUILD SUCCESSFUL |
| JUnit (`cleanTest test`, genuinely re-executed) | **1717 tests, 0 failures, 0 errors, 0 skipped** (140 test classes) |
| Dedicated dev server start (all mixins applied, no registry/packet/codec failure) | `Done`; 0 mixin/injection errors |
| `MiningVerification` | **166/166** |
| Other dev suites | PowerAttack 12/12 · Food 42/42 · SoulGem 16/16 · ResourceFoundation 6/6 · BaselineResourceMigration 11/11 · BarbarianRageMigration 18/18 · StandardSpellSlotMigration 23/23 · HealthRecoveryDice 14/14 · CrownOfStars 11/11 · Provisioner 71/71 · TradingScreen 22/22 · MerchantSell 47/47 · ItemValue 19/19 |
| Historical failure | `OffhandAttackVerification` **3/5** — identical to before the integration (see F-15) |

All counts equal the expected baseline. No regression was found by the automated suites.

---

# PART I — Unified Totality Code Audit

## 1. Project structure (what exists)

`src/main/java/zcylas/totality` (≈ the whole mod): `api/` (94 of the 140 test classes live here) holds the domain systems; `client/`, `screen/` (UI/HUD/render), `networking/` (payloads + handlers), `mixin/` (51 configured mixins), `init/` (registration), `item/`, `block/`, `entity/`, `worldgen/`, `datagen/`, `server/` (fake player). Systems inspected: PlayerStats/Attributes (`api/rpg/stats`), Classes (`api/rpg/classes`), **Generic Player Resource** (`api/rpg/resources` + compat facades `PlayerManaManager`, `PlayerStaminaManager` …), **Food 0–100** (`api/rpg/resources/food`, 10 mixins), Rest, Combat (`api/rpg/combat`, `init/events/VanillaDamageInterceptor`, `api/combat/damage/TotalityDamage`), Power Attack, Offhand/dual wield (`networking/combat`, `DualWieldCooldownTracker`), Combat Text, **Block Breaking** (`api/mining`, `client/mining`, 3 mixins, 2 payloads), **Soul Gems** (`api/soulgem`, `item/soulgem`), Provisioner/Economy/Banking (`api/shop`, `api/economy`), Quests/Dialogue, persistence (custom `ComponentRegistry`/`ComponentKey` codecs — 25 component registries — plus exactly one `SavedData`: `BlockDamageStorage`), datagen (`datagen/`, 8 providers, output committed in `src/main/generated`). **JEI:** no JEI dependency or code exists in the mod (JEI appears only as a local jar in the dev `run/mods` folder). **Unlock/Access/Entitlement:** no such system was found in the code.

## 2. Registration / initialization

* **Entrypoints** (`fabric.mod.json`): `main` = `Totality`, `client` = `TotalityClient`, `fabric-datagen`. `depends`: loader `>=0.19.3`, `fabric-api: *`, minecraft `~26.2`, java `>=25` (the build uses Loader 0.19.5 / Fabric API 0.161.0; the declared minimums are looser — F-21, INFO).
* **Payloads:** 80 `CustomPacketPayload` types were found by scanning. Every one that is live is registered exactly once, in exactly one direction (none twice). The 5 raw "defined but not registered" hits: 3 are scan artefacts of comment text (`Payload`, `level`, `the`); **2 are genuinely dead** (`EnergyFaceConfigPayload`, `EnergyFaceConfigSyncPayload`: no references at all — F-10). The receivers for `AttunementPayload`, `UnAttunePayload`, `PhoneSetupPayload`, `OpenAccessoryInventoryPayload`, `OpenInventoryPayload` are registered in `Totality.java` (multi-line registrations my scan missed; verified), and all S2C payloads have handlers in `TotalityClientPacketHandlers`/dedicated client managers. No payload is defined-but-unhandled.
* **Ordering:** `Totality.onInitialize` registers components, items, blocks, entities, packets, handlers, skill events, ticks, then verification suites; the Block Breaking registrations (`PlayerMiningManager.register`, `MiningVerification.register`) sit at the end of that sequence and depend only on packets already registered. No fragile ordering was found beyond "packets before receivers", which holds.
* **Layering:** server code references client-package types in a few places (`MiningVerification`/`MiningFeedback` → `zcylas.totality.client.combat.CombatTextEntry`, `MiningVerification` → `client.mining.MiningHandAnimation`). All are pure-Java types with no Minecraft-client imports, so nothing crashes on a dedicated server (the dedicated server started), but the package direction is a smell (F-20). `ClientMiningController`/`PowerMiningMeterHud`/the render mixin are only referenced from client code.

## 3. Mixin audit (51 configured: 29 common, 22 client)

All 51 configured classes exist; no mixin-package file is missing from the config; every mixin applied at runtime in both the dedicated server and the dev client (`defaultRequire: 1` would have crashed otherwise). Full table (target · injection · subsystem):

| Mixin | Target | Injection(s) | Subsystem |
|---|---|---|---|
| `BatMixin` | Bat | TAIL | mobs |
| `CraftingMenuAccessor` | CraftingMenu | Invoker | crafting |
| `MixinServerPlayer` | ServerPlayer | `<init>`/save/read TAIL | player data |
| `EnchantedCountIncreaseFunction{Accessor,Mixin}` | EnchantedCountIncreaseFunction | Accessor; `run` HEAD | loot |
| `LootItemRandomChanceWithEnchantedBonusConditionMixin` | that condition | ModifyVariable `test` | loot |
| `item.KineticWeaponMixin` / `MaceItemMixin` / `PiercingWeaponMixin` / `TridentItemMixin` | KineticWeapon / MaceItem / PiercingWeapon / TridentItem | Inject | combat stamina costs |
| `item.SwordItemMixin` | Item | `getUseAnimation`/`getUseDuration`/`use` HEAD | sword blocking |
| `LivingEntityHurtMixin` | LivingEntity | `isBlocking` RETURN | combat |
| `LivingEntityRestSleepMixin` / `PlayerRestSleepMixin` | LivingEntity / Player | Redirect `tick` INVOKE | rest |
| `FoodDataNaturalRegenerationMixin` | FoodData | ModifyVariable `tick` STORE | food |
| `FoodDataExhaustionAuthorityMixin` | FoodData | Redirect `tick` `Math.max(II)I` | food |
| `FoodDataPeacefulExhaustionDepletionAuthorityMixin` | FoodData | ModifyVariable ×2 | food |
| `FoodDataStarvationDamageAuthorityMixin` | FoodData | Redirect `tick` | food |
| `ServerPlayerPeacefulRegenerationMixin`, `…PeacefulFoodRestoreAuthorityMixin`, `…PeacefulSaturationRestoreAuthorityMixin` | ServerPlayer | Redirect `tickRegeneration` ×3 | food/regen |
| `FoodPropertiesEatAuthorityMixin`, `CakeBlockEatAuthorityMixin`, `SaturationMobEffectEatAuthorityMixin` | FoodProperties / CakeBlock / SaturationMobEffect | Redirect INVOKE | food |
| `PlayerFoodSprintGateAuthorityMixin` | Player | `hasEnoughFoodToDoExhaustiveManoeuvres` HEAD | food/sprint |
| `InventoryCreditsMergeMixin` | Inventory | Inject | economy |
| `MultiNoiseBiomeSourceParameterListMixin` | that class | `<init>` TAIL | worldgen |
| `client.LivingEntityMixin` (in the *common* list) | LivingEntity | ModifyReturnValue `getScale` | ancestry scale |
| **`mining.ServerPlayerGameModeMixin`** | ServerPlayerGameMode | Inject `handleBlockBreakAction` HEAD; WrapOperation `destroyBlock`→`ServerPlayer.hasCorrectToolForDrops` | **mining** |
| client: `EntityDimensionsMixin`, `GameRendererMixin`, `MixinLocalPlayer`, `MouseHandlerMixin`, `ScreenAccessor`, `AbstractContainerScreen{Mixin,Accessor}`, `LocalPlayerContainerMixin`, `GuiGraphicsExtractorMixin`, `AvatarRendererMixin`, `GuiExtractRenderStateMixin`, `HumanoidModelMixin`, `MultiPlayerGameModeMixin` | various | Inject/Redirect/Invoker | UI/render/input |
| `client.MinecraftAttackMixin` | Minecraft | HEAD `startAttack`, `startUseItem`, `tick` | Power Attack / offhand |
| **`client.MinecraftMiningMixin`** | Minecraft | HEAD `startAttack`, `continueAttack` | **mining** |
| `client.ItemInHandRendererMixin` | ItemInHandRenderer | ModifyArg `submitHandsWithItems` (ordinal 1, offhand); ModifyExpressionValue `submitArmWithItem` | dual wield |
| **`client.MiningHandRenderMixin`** | ItemInHandRenderer | ModifyArg `submitHandsWithItems` (ordinal 0, main hand); WrapOperation `renderItem`, `renderPlayerArm` | **mining animation** |
| `client.chat.*` (5) | ChatComponent/ChatScreen/CommandSuggestions | ModifyConstant | chat layout |

**Same-method overlaps (5):**
1. `FoodData.tick` — 4 Totality mixins (3 Redirect + ModifyVariable) on *different* instructions. Cooperative but tightly coupled to the vanilla bytecode; `FoodDataExhaustionAuthorityMixin` relies on `Math.max(II)I` occurring exactly once (documented in its Javadoc). Risk on a Minecraft update (F-13).
2. `ServerPlayer.tickRegeneration` — 3 Redirects on different calls. Same risk class (F-13).
3. `Minecraft.startAttack` — `MinecraftAttackMixin` (cancels only for weapon + *entity* target) and `MinecraftMiningMixin` (cancels only for an owned *block* target): disjoint conditions, verified by design and by the compile/run; no bypass found.
4. `ItemInHandRenderer.submitHandsWithItems` — `@ModifyArg` on the *same* call with ordinal 1 (offhand, dual-wield) vs ordinal 0 (mining): independent call sites. Fragile if vanilla reorders the two `submitArmWithItem` calls (ordinals) — F-13.
5. `ItemInHandRenderer.submitArmWithItem` — one `ModifyExpressionValue` (dual-wield block hand) and mining `WrapOperation`s on different invocations: independent.

No two Totality mixins were found with incompatible expectations; no obsolete mixin left behind; cancellations are narrowly gated. Vanilla behaviour is *extended* by the mining mixins (both are inert unless a Totality-owned mining state is active) and *replaced* deliberately by the Food authority mixins (vanilla starvation damage, regen and sprint gate are disabled by design).

## 4. Player stats / level / attributes (implementation truth)

| Design fact | Implementation | Verdict |
|---|---|---|
| Player Level cap 150 | `PlayerStats.MAX_LEVEL = 150` | ✔ |
| Total Class Level cap 30 | documented and implemented in `PlayerClassComponent` ("cap of 30 is reached exactly at Player Level 145") | ✔ |
| Attributes STR DEX CON INT WIS CHA FTH END | `AbilityScore` enum | ✔ |
| Modifier `floor((score−10)/2)` | `AbilityScore.getModifier` = `Math.floorDiv(score − 10, 2)` | ✔ |
| No universal Attribute hard cap | no clamp other than `max(1, total)` floor in `recalculate` | ✔ |
| Character XP feeds Player Level | `addCharacterXp` loops level-ups with remainder carry-over (fixed on the canonical line) | ✔ |
| Old "+5 attribute points every level" is **superseded** | **still active**: `PlayerStats.ATTRIBUTE_POINTS_PER_LEVEL = 5`, applied in `advanceLevel()`, and `PlayerSkillsComponent` still shows `"⭐ Level N! +5 attribute points, +1 mastery point"` | ⚠ **mismatch** (F-02) |
| Skills have separate XP | `SkillsComponents`/`Skill.MINING` etc. | ✔ |

Only one attribute store exists (`PlayerStats` component); `PlayerResourceRecalculator` reads it. Block Breaking reads STR/DEX only through `StatsComponents.getStats(...).getScore/getModifier` (no duplicate store).

## 5. Generic Player Resource

Ownership is a single service (`PlayerResourceService`) with definitions in `ProductionResourceDefinitions`; `PlayerManaManager` and `PlayerStaminaManager` are explicit **compatibility facades** over it (their Javadoc says so; the 71 outside call sites mutate through the facades, which route into the service — no second authority). Sync uses full/delta payloads (`ResourceFullSyncPayload`, `ResourceDeltaSyncPayload`) plus a client-initiated resync request handled by `ResourceResyncRequestHandler`; persistence is via `ResourceStateComponents`. The resource suites (foundation, baseline migration, rage, spell slots, health-recovery dice, food) all pass on the integrated tree, and the JUnit suite has dedicated resource sync/parity tests. Health/Breath remain external adapters. Login/respawn/dimension/class-change reconciliation is covered by `ClassChangeReconciler…` tests and the migration suites; a live multi-dimension session was not exercised in this audit. **No violation of "one resource authority" was found**; stamina is spent directly by several combat mixins (Kinetic/Mace/Piercing/Trident, offhand handler) but always through the facade.

## 6. Food 0–100

Coherent and documented as a *transitional bridge*: true `totality:food` (0–100) is authoritative and vanilla `FoodData.foodLevel` is kept as a proportional 0–20 mirror (`FoodVanillaCompatibilityBridge`). Vanilla starvation damage, food-based regeneration and the sprint gate are disabled by dedicated mixins; the HUD reads the generic resource (with a `foodLevel × 5` fallback only before the first sync). Remaining vanilla-0–20 assumptions are all *inside* the bridge, the debug command (`/totality … foodLevel/20`), `NativeResourceAccess.foodLevel()` ("0–20 scale", the parity comparator), and one direct `HealEffect: player.causeFoodExhaustion(2.5f)` (vanilla exhaustion units, routed through the same exhaustion authority — INFO, F-17). Block Breaking does not touch food; the vanilla terminal break still adds the vanilla 0.005 exhaustion per broken block (one call per block, not per impact). `FoodSystemVerification` 42/42.

## 7. Combat / damage / offhand / body strain

* **Damage path:** vanilla damage is intercepted (`VanillaDamageInterceptor` via `ServerLivingEntityEvents.ALLOW_DAMAGE`) and re-routed through `TotalityDamage`/`CombatResolver` with resistance/vulnerability/immune handling and Combat Text. **Body strain** (bare-hand Power orange/red) calls `player.hurtServer(level, damageSources().generic(), 2 or 6)`, i.e. an *environmental* vanilla source on a player with no attacker → the interceptor's "environmental damage on player" branch → `TotalityDamage.hurt(player, null, type, amount, …)`. So it *is* on the common damage path; final HP can legitimately differ through mapping/resistance. The test seam proves the amount handed over; actual HP loss on a real player was not measured in this audit (F-14).
* **Combat Text separation:** entity text uses `DAMAGE/RESIST/VULNERABLE/HEAL/IMMUNE/CONDITION` with a damage type; block text uses the separate `BLOCK_DAMAGE`/`INEFFECTIVE` types (no damage type, entity id −1) with an extra `style` byte defaulting to 0. `CombatTextPayload` round-trip tests prove entity text is unchanged. ✔
* **Power Attack:** 12/12. **Offhand:** 3/5 — see F-15.

## 8. Block Breaking integration re-audit

Server authority, source snapshot, shared integrity, persistence (identity check, lazy recovery, immediate + 40-tick crack sync), cadence, final vanilla destroy path, Fortune/Silk, durability normalisation, exact-position `HarvestGrant`, Power/Force Stress/body strain, source identity, networking and Combat Text were re-read on unified `master` and exercised again in the scratch runtime. Findings:

* **Authority:** the client sends only 5 intent enum values and receives one timings payload; the server derives target, force and every result. Vanilla START/STOP destroy is refused for owned blocks. ✔
* **Mixins/registrations** merged cleanly (§2–3).
* **STR/DEX:** still read via the canonical `PlayerStats` (§4). ✔
* **Food/resources/combat:** no interaction beyond vanilla `playerDestroy` exhaustion and the generic-damage body strain (§6–7). ✔
* **Offhand rendering:** the dual-wield `ModifyArg` (ordinal 1) and the mining `ModifyArg` (ordinal 0) coexist (both applied in the dev client); interactive dual-wield rendering was not exercised.
* **New finding F-09:** every 40 ticks `PlayerMiningManager` calls `BlockDamageStorage.get(level)` for **every** level; `CodecSavedData.create` → `SavedDataStorage.computeIfAbsent`, which for missing data constructs a new instance and calls `set(...)` → `setDirty()`. Result: an **empty `block_damage.dat` is written for every dimension** (the playtest world shows a 51-byte file in `the_nether`/`the_end`). Harmless but wasteful and surprising.
* **F-05 (design divergence):** Totality merges "may I damage it" with "may I harvest it" into Mining Tier; vanilla always lets *any* item break a block and only gates the *drops*. Consequence: a bare hand (or wooden pickaxe on iron ore) is `INEFFECTIVE` in Totality but breakable (slowly, no drops) in vanilla. This is the deliberate V1 design ("Tier = capability"), recorded here as a documented divergence.

### The three live observations

| # | Observation | Classification | Evidence |
|---|---|---|---|
| 1 | Cobblestone + bare hand → INEFFECTIVE | **uncertain / needs reproduction** (not a tier-mapping bug) | RUNTIME: Stone and Cobblestone have the **identical** required tier (1) and behave identically: at STR 10 / **DEX 10** *both* are `INEFFECTIVE`; at DEX 12 *both* are `DAMAGED` (Stone 34 strikes at STR 14, Cobblestone 45; both `BROKEN`). `needs_stone_tool` does not contain either block (CODE: tag file; no Totality override adds them — `ModBlockTagProvider` only adds Totality ruby ores to `needs_diamond_tool`). The observed asymmetry therefore cannot come from the block → tier fallback; the likely explanations are a different DEX (or held item) between the two tests. |
| 2 | Stone + bare hand breakable but **no drops** | **likely already fixed / needs one live confirmation** | RUNTIME (real code, real `ItemEntity` observed near the world spawn chunk): Stone + qualified bare hands (STR 14, DEX 12) → `BROKEN`, the `HarvestGrant` was consulted exactly once (`grantServed=1`) and **`1× minecraft:cobblestone` dropped**; Cobblestone likewise dropped a Cobblestone; Oak Log, Dirt, Netherrack and Deepslate (→ Cobbled Deepslate) dropped as well; iron-pickaxe control dropped normally. The "no drop" was first reported in the first playtest, *before* the scoped `HarvestGrant` existed; later live sessions concentrated on animation and did not re-confirm drops. The earlier automated test could not observe drop entities (chunk far from players is not entity-accessible — see §11) and only proved the grant was consulted; the new runtime reproduction closes that gap. If the drop is still missing live, the difference must be something a fake player lacks (a real `ServerPlayer`/connection, stats, or a different held item) — that would then be a **confirmed bug** to debug with the live client. |
| 3 | Obsidian + Netherite + Efficiency V ≈ 10 strikes, "too slow" | **provisional balance result (plus stats unknown)** | RUNTIME numbers (STR 10, DEX 10): Obsidian Block Durability **3333.3** (`50 × 66.67`), Netherite Pickaxe Mining Damage **54** per strike (`9 × 6 + STR modifier 0`) → **62 strikes**, cadence with Efficiency V = **4-tick cycle** → **12.3 s**. Vanilla: **2.15 s**. So Totality is ≈ **5.7× slower in time**, and needs **62 strikes**, not ~10. ~10 strikes would need ≈ 333 damage per strike (STR modifier ≈ +279, i.e. STR ≈ 568) — or Power Mining at very high STR; the tester's stats are unknown, so the "≈10" is **not reproducible from the stated model** and should be re-measured with the exact STR/DEX and Power state. Whichever it was, the structural finding stands: Totality's constants make baseline mining ~3.7× slower than vanilla and the cadence floor compresses Efficiency (§13). |

---

## 9. Soul Gems

Foundation only, as intended: `SoulCaptureEligibility.isEligibleForCapture(rank)` is `rank != ZERO` (Rank 0 Family-only ineligible ✔); `SoulGemAcceptanceRule.categoryUpToRank` implements Petty/Common by category and `rank.isAtMost(max)`; state lives entirely in the `CAPTURED_SOUL` item component (present = filled), `SoulCaptureService.attemptCapture` rejects non-gem, already-filled, **stacked** gems (`STACKED_VESSEL_REQUIRES_SPLIT`), ineligible/too-strong ranks. 16/16 suite. **`attemptCapture` has no production caller** (only the verification) — there is no in-game capture/drop flow yet (F-16, INFO), so duplication/exploit paths cannot exist yet; block/combat changes cannot affect it. No stale pre-Soul-Gem placeholder was found.

## 10. Persistence & networking

**Persistence.** Player state uses the custom component registry (25 registries with codecs and sync); world state uses exactly one `SavedData` (`BlockDamageStorage`, per dimension, dirty-marked on every mutation, identity-checked by block registry id, lazy recovery from timestamps; a real save → reload → attach round trip is tested and the playtest world's file reloaded correctly). Concerns: F-09 (empty files); the codec passes `null` `DataFixTypes` (works on this Fabric; latent on updates — recorded earlier). No missing dirty-marking or in-memory-only authoritative state was found in the sampled systems; deeper per-component review (quests, banking) was not exhaustive.

**Packet inventory (80 payload type definitions found; 75 registered: 49 C2S + 26 S2C; none bidirectional; the other 5 are 3 scan artefacts and 2 dead payloads).** Trust review of the gameplay-relevant ones:

| Payload (dir) | Client-supplied values | Server validation | Verdict |
|---|---|---|---|
| `MiningIntentPayload` (C2S) | enum only (no target, no force) | server session, raycast, ownership, source snapshot | ✔ authoritative |
| `MiningSwingPayload` (S2C) | — | timings only | ✔ |
| `PowerAttackPayload`, `OffhandAttackPayload` (C2S) | target entity id, power flag | re-resolves entity, `isValidTarget`, legality, cooldown, stamina | ✔ |
| `SpendAttributePointPayload` (C2S) | attribute id | server unspent points | ✔ |
| `DepositCreditsPayload` / `WithdrawCreditsPayload` (C2S) | long amount | physical payment / wallet spend; **no check that the player is at a bank teller** (session/proximity); withdraw builds one stack list of arbitrary size | ⚠ LOW (F-11) |
| `MovementStaminaPayload`, `PowerSprintStatePayload`, `ToggleFlightPayload` (C2S) | mode / active flag | unlocked-ability check, stamina check; **the *cost* is only charged when the client reports the movement** (client-triggered, evadable) | LOW/INFO (F-12) |
| `ContainerSortPayload` (C2S) | `SORT` carries **the sorted `ItemStack` list itself** | none: `InventorySortHelper.applyMapping` does `container.setItem(i, sortedStacks.get(i))` — **the server writes client-supplied stacks into the Component Pouch** | 🔴 **HIGH (F-01)** — item creation/duplication |
| Others sampled (`SelectClass`, `UnlockMastery`, `Buy/SellItem`, `TradeSession*`, `ActivateAbility`) | ids | resolved server-side; not exhaustively audited | not exhaustively verified |

## 10b. Test quality

| Suite | What it actually proves | Blind spots |
|---|---|---|
| **JUnit 1717 / 140 classes** (part of `gradle build`) | pure/unit logic; **31 classes are source-text regression tests** (they read `.java` source and assert strings/structure); 33 reference Minecraft runtime types (mostly pure-Java model code) | **no JUnit coverage of mining at all**; source-text tests prove a string exists, not that the behaviour works |
| `MiningVerification` 166 (dev-only, at server start, **not part of `gradle build`**) | integration-ish on a real dedicated server with a real `ServerLevel`, real `SavedDataStorage`, real vanilla `destroyBlock`, real mixins | uses `TotalityFakePlayer`, which is **invulnerable, has a no-op `awardStat`, no connection**; entity queries in a chunk with no player return nothing (drops unobservable); no network, no renderer, no client; persistence tests do not restart the JVM |
| Other `*Verification` suites | actual Minecraft runtime checks with fake players | same fake-player limits; only run when a dev server starts |
| Animation tests | pure math on `MiningHandAnimation` | prove nothing about how it looks or renders |
| Manual | live client: animation feel, drops, HUD, multiplayer | not repeatable |

**Why the Stone/bare-hand test passed while live gave no drop:** (1) the test measured that the *grant was consulted* (`served`), not that an item spawned — the chosen test chunk (100, y, 100) is not entity-accessible, so drops never appear there even for an iron pickaxe control; (2) `awardStat` is a no-op on the fake player, so the `BLOCK_MINED` stat could not be used as a substitute; (3) the run was a fake player, not a real client session. The new scratch runtime reproduction (chunk near spawn, real `ItemEntity`s) does show the drop, which supports "the live observation predates the fix", but only a live re-test can settle it.

## 11. Part I findings

**Counts:** CRITICAL 0 · HIGH 1 (F-01) · MEDIUM 4 (F-02, F-03, F-04, F-06) · LOW 7 (F-05, F-09, F-10, F-11, F-12, F-13, F-19) · INFO 9 (F-07, F-08, F-14, F-15, F-16, F-17, F-20, F-21, F-22) — **21 findings**.

Severity legend: CRITICAL / HIGH / MEDIUM / LOW / INFO. "Status": CONFIRMED (evidence in code or runtime), LIKELY, or DOC.

| ID | Sev | System | Status / type | Evidence | Why it matters |
|---|---|---|---|---|---|
| F-01 | **HIGH** | Networking / Component Pouch | **CONFIRMED by code reading (not exploited)** — confirmed bug | `ContainerSortHandler` `SORT` → `InventorySortHelper.applyMapping(container, payload.sortedStacks())` writes client stacks straight into `container.setItem`; only `ComponentPouchMenu` implements `SortableContainerMenu` | A modified client can create/duplicate arbitrary items in a Pouch (economy/progression exploit in multiplayer) |
| F-02 | MEDIUM | Player progression | **implementation vs design mismatch** (stale code) | `ATTRIBUTE_POINTS_PER_LEVEL = 5` used by `advanceLevel()`; `PlayerSkillsComponent` message "+5 attribute points" | Designated-superseded reward system is still live; affects balance and any new progression work |
| F-03 | MEDIUM | Block Breaking balance | **balance issue** | RUNTIME table §13: baseline mining ≈ 3.3–3.7× vanilla time; Efficiency V ≈ 4.7–6.2×; hand 6.6× | The next balance pass's headline finding |
| F-04 | MEDIUM | Testing | **testing gap** | mining/dev suites not in `gradle build`; 0 JUnit mining tests; fake-player blind spots (§10b) | Regressions in mining/dev suites are invisible to a plain `build` |
| F-06 | MEDIUM | Block Breaking / harvest | **LIKELY resolved; unconfirmed live** | RUNTIME drop reproduction (§8 row 2) | If the live drop is still missing, it is a real, unexplained bug |
| F-05 | LOW | Block Breaking design | intentional/provisional (documented divergence) | vanilla lets any item break any block; Totality gates by Tier | Different feel from vanilla for hands and under-tier tools; decision for the authored-stats pass |
| F-09 | LOW | Persistence | confirmed minor bug | empty `block_damage.dat` per dimension (`the_nether`/`the_end` files exist in the playtest world) | wasteful writes; surprising files |
| F-10 | LOW | Networking | stale code | `EnergyFaceConfigPayload`, `EnergyFaceConfigSyncPayload`: 0 references | dead payload definitions |
| F-11 | LOW | Economy | likely design gap | Bank deposit/withdraw have no teller-session/proximity check; withdraw of a large amount creates a large stack list | remote banking / minor lag vector; wallet-limited |
| F-12 | LOW | Movement | design/architecture | movement stamina cost is charged only when the client sends the payload | cost evadable by a modified client |
| F-13 | LOW | Mixins | architecture debt | FoodData.tick ×4, tickRegeneration ×3, ItemInHandRenderer ordinals 0/1, `Math.max(II)I` assumption | breaks silently-loudly on a Minecraft update (loudly: `defaultRequire 1`) |
| F-19 | LOW | Testing | testing gap | 31/140 JUnit classes read source text | false confidence about behaviour |
| F-07 | INFO | Block Breaking | see §8 row 1 | uncertain | — |
| F-08 | INFO | Block Breaking | see §8 row 3 | unknown tester stats | — |
| F-14 | INFO | Combat | provisional | body strain uses the generic vanilla source; HP may differ; `PlayerMiningManager.bodyStrainObserver` is a test seam in production code | document / decide later |
| F-15 | INFO | Combat tests | historical | see below | — |
| F-16 | INFO | Soul Gems | foundation | `attemptCapture` has no caller | no capture flow yet |
| F-17 | INFO | Food | transitional | `HealEffect` → vanilla `causeFoodExhaustion(2.5f)` | vanilla units through the bridge; verify scale when Food is next touched |
| F-20 | INFO | Layering | architecture | common code references client package types (pure Java) | package direction smell |
| F-21 | INFO | Build metadata | doc mismatch | `fabric.mod.json` allows loader ≥0.19.3 / any Fabric API; build pins 0.19.5 / 0.161.0 | none today |
| F-22 | INFO | Mining | note | `AttackBlockCallback` fires per impact (vanilla fires it once per START) | may repeat protection-mod messages |

**F-15 — what the Offhand failures are.** The three failing checks (legal offhand Power Attack spends Power stamina; legal ordinary offhand attack spends ordinary stamina; insufficient stamina gracefully downgrades) all report "stamina before = after" (100→100, 29→29). The handler only spends stamina at its end (`removeStamina` after `resolveAttack`), so an early `return`, or an attack that never reaches that line for the fake player, produces exactly this. The fixture is a fake dual-wielding two iron swords (`isDualWielding` ✔) with a fresh UUID (cooldown gate passes). The cause was **not established** in this audit; the failure signature is identical to before the integration (also to the *previous* base, where PowerAttack had one such failure that is now fixed), so the merge did not change it. It is a **historical isolated problem**; treat as a testing/real-bug investigation of its own.

**Part I summary**
1. *Confirmed correctness issues:* F-01 (HIGH, item creation via `ContainerSortPayload`), F-09 (empty SavedData files), F-10 (dead payloads).
2. *Likely issues needing reproduction:* F-06 (Stone no-drop live), F-07/F-08 (live observations), F-11/F-12.
3. *Architectural debt:* F-13, F-20, F-21, the transitional Food bridge, static test seams in production code.
4. *Testing gaps:* F-04, F-19, fake-player blind spots, no client/network/render tests, JVM-restart persistence.
5. *Balance-only:* F-03 (plus Force Tolerance/strain values, all provisional).
6. *Checked and healthy:* registration (no duplicate/missing live payloads, 51/51 mixins present and applied), packet direction/ownership, mining authority (intent-only client, server-derived force/target), STR/DEX read path, level cap 150 / class cap 30 / modifier formula, Resource facades → single service, Food 0–100 bridge coherence, Combat Text entity/block separation, SavedData round trip and block-identity check, HarvestGrant/durability/source-identity behaviour, Soul Gem rank rules, build and 1717 JUnit tests, all dev suites except the historical one.

---

# PART II — Vanilla Minecraft 26.2 Mining Baseline

## 12. How vanilla decides (CODE: 26.2 sources and data)

Three separate questions:

**1. Can I break it?** Anything with hardness ≥ 0 can be broken in Survival by any item, including bare hands; `hardness = -1` (bedrock) cannot. Creative breaks instantly; Adventure/Spectator restrictions come from `blockActionRestricted`/`can_break` predicates and game mode; spawn protection and `mayInteract` apply. **There is no tier gate on breaking.**

**2. How fast?** `BlockBehaviour.getDestroyProgress = player.getDestroySpeed(state) / hardness / (30 if hasCorrectToolForDrops else 100)` per tick; the block breaks when the accumulated progress reaches 1 (`ticks = ceil(1/progress)`, 20 ticks/s; ≥ 1 per tick = instant). `Player.getDestroySpeed(state)`:
1. `speed = heldItem.getDestroySpeed(state)` = the `TOOL` component's first matching rule speed, else `defaultMiningSpeed` (1.0). A pickaxe's rules are `deniesDrops(incorrect_for_<material>_tool)` (no speed) then `minesAndDrops(#mineable/pickaxe, materialSpeed)`; so a pickaxe mines *every* pickaxe-mineable block at its material speed **even when it is too weak to get drops**. Bare hands have no `TOOL` component → 1.0.
2. `if (speed > 1) speed += MINING_EFFICIENCY attribute` — Efficiency level N adds `N² + 1` (`levels_squared`, `added: 1.0` in `enchantment/efficiency.json`): I=+2, III=+10, V=+26. Not added to bare hands/speed-1.
3. Haste/Conduit Power: `× (1 + 0.2 × (amplifier + 1))` (Haste I ×1.2, II ×1.4).
4. Mining Fatigue: `× 0.3 / 0.09 / 0.0027 / 0.00081` for amplifier 0 / 1 / 2 / ≥3.
5. `× BLOCK_BREAK_SPEED` attribute (base 1.0); underwater (eyes in water) `× SUBMERGED_MINING_SPEED` (0.2 default); airborne `÷ 5`.

**3. Do I get drops?** `hasCorrectToolForDrops = !block.requiresCorrectToolForDrops || heldItem.isCorrectForDrops(state)`; `Tool.isCorrectForDrops` = the first rule with `correct_for_drops`: a pickaxe denies drops for blocks in `incorrect_for_<material>_tool` and grants them for `#mineable/pickaxe`. Tier tags (26.2 data): `needs_stone_tool` (iron/copper/lapis ores & blocks, copper blocks…), `needs_iron_tool` (gold/redstone/diamond/emerald ores & blocks), `needs_diamond_tool` (obsidian, crying obsidian, ancient debris, netherite block, respawn anchor); `incorrect_for_wooden/gold` = stone+iron+diamond tiers; `incorrect_for_stone/copper` = iron+diamond; `incorrect_for_iron` = diamond; `incorrect_for_diamond/netherite` = none. (**26.2 adds Copper tools**: speed 5.0, durability 190, stone-level tier.) Drops are decided in `ServerPlayerGameMode.destroyBlock`: `canDestroy = hasCorrectToolForDrops(adjustedState)`; `playerDestroy` (loot, stats, exhaustion) runs only if true — the exact call Totality's `HarvestGrant` wraps.

Tool material speeds/durability (`ToolMaterial`): wood 2.0/59, stone 4.0/131, copper 5.0/190, iron 6.0/250, diamond 8.0/1561, **gold 12.0/32**, netherite 9.0/2031. Vanilla break time in this document: `ceil(1/progress)/20 s`, on ground, not underwater, ignoring the 5-tick delay between consecutive blocks.

## 13. Sample set (CODE)

| Block | Hardness | Tool | Tier to get drops | Bare hand breaks? | Bare-hand drops? |
|---|---|---|---|---|---|
| Dirt | 0.5 | shovel (any) | none | yes | **yes** (does not require a tool) |
| Grass Block | 0.6 | shovel | none | yes | yes (dirt) |
| Oak Log | 2.0 | axe | none | yes | yes |
| Netherrack | 0.4 | pickaxe | any pickaxe (`requiresCorrectToolForDrops`) | yes | **no** |
| **Stone** | **1.5** | pickaxe | any pickaxe | yes (7.5 s) | **no** |
| **Cobblestone** | **2.0** | pickaxe | any pickaxe | yes (10.0 s) | **no** |
| Deepslate | 3.0 | pickaxe | any pickaxe | yes | no |
| Cobbled Deepslate | 3.5 | pickaxe | any pickaxe | yes | no |
| Coal Ore | 3.0 | pickaxe | any pickaxe | yes | no |
| Iron / Copper / Lapis Ore | 3.0 | pickaxe | stone (`needs_stone_tool`) | yes | no |
| Gold / Redstone / Diamond / Emerald Ore | 3.0 | pickaxe | iron (`needs_iron_tool`) | yes | no |
| Nether Gold Ore | 3.0 | pickaxe | any pickaxe | yes | no (needs a pickaxe only) |
| Deepslate ores | 4.5 (deepslate diamond) | pickaxe | as their normal ore | yes | no |
| Ancient Debris | 30.0 | pickaxe | diamond (`needs_diamond_tool`) | yes (150 s) | no |
| Obsidian | 50.0 | pickaxe | diamond | yes (250 s) | no |
| Crying Obsidian | 50.0 | pickaxe | diamond | yes | no |

## 14. Vanilla break-time matrix (DERIVED from the formula above)

`✓` = drops, `✗` = breaks but no drops; `0.00 s` = instant (progress ≥ 1 per tick). Columns are total time in seconds.

| Block | hard | needs | hand | wood | stone | copper | iron | diamond | netherite | netherite+Eff V | diamond+Eff V | netherite+Eff V+Haste II |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| STONE | 1.5 | any pickaxe | 7.50 s ✗ | 1.15 s ✓ | 0.60 s ✓ | 0.45 s ✓ | 0.40 s ✓ | 0.30 s ✓ | 0.25 s ✓ | 0.10 s ✓ | 0.10 s ✓ | 0.00 s ✓ |
| COBBLESTONE | 2.0 | any pickaxe | 10.00 s ✗ | 1.50 s ✓ | 0.75 s ✓ | 0.60 s ✓ | 0.50 s ✓ | 0.40 s ✓ | 0.35 s ✓ | 0.10 s ✓ | 0.10 s ✓ | 0.10 s ✓ |
| DEEPSLATE | 3.0 | any pickaxe | 15.00 s ✗ | 2.30 s ✓ | 1.15 s ✓ | 0.90 s ✓ | 0.75 s ✓ | 0.60 s ✓ | 0.50 s ✓ | 0.15 s ✓ | 0.15 s ✓ | 0.10 s ✓ |
| COBBLED_DEEPSLATE | 3.5 | any pickaxe | 17.50 s ✗ | 2.65 s ✓ | 1.35 s ✓ | 1.05 s ✓ | 0.90 s ✓ | 0.70 s ✓ | 0.60 s ✓ | 0.15 s ✓ | 0.20 s ✓ | 0.15 s ✓ |
| NETHERRACK | 0.4 | any pickaxe | 2.00 s ✗ | 0.30 s ✓ | 0.15 s ✓ | 0.15 s ✓ | 0.10 s ✓ | 0.10 s ✓ | 0.10 s ✓ | 0.00 s ✓ | 0.00 s ✓ | 0.00 s ✓ |
| COAL_ORE | 3.0 | any pickaxe | 15.00 s ✗ | 2.30 s ✓ | 1.15 s ✓ | 0.90 s ✓ | 0.75 s ✓ | 0.60 s ✓ | 0.50 s ✓ | 0.15 s ✓ | 0.15 s ✓ | 0.10 s ✓ |
| IRON_ORE | 3.0 | stone+ | 15.00 s ✗ | 7.50 s ✗ | 1.15 s ✓ | 0.90 s ✓ | 0.75 s ✓ | 0.60 s ✓ | 0.50 s ✓ | 0.15 s ✓ | 0.15 s ✓ | 0.10 s ✓ |
| COPPER_ORE | 3.0 | stone+ | 15.00 s ✗ | 7.50 s ✗ | 1.15 s ✓ | 0.90 s ✓ | 0.75 s ✓ | 0.60 s ✓ | 0.50 s ✓ | 0.15 s ✓ | 0.15 s ✓ | 0.10 s ✓ |
| LAPIS_ORE | 3.0 | stone+ | 15.00 s ✗ | 7.50 s ✗ | 1.15 s ✓ | 0.90 s ✓ | 0.75 s ✓ | 0.60 s ✓ | 0.50 s ✓ | 0.15 s ✓ | 0.15 s ✓ | 0.10 s ✓ |
| GOLD_ORE | 3.0 | iron+ | 15.00 s ✗ | 7.50 s ✗ | 3.75 s ✗ | 3.00 s ✗ | 0.75 s ✓ | 0.60 s ✓ | 0.50 s ✓ | 0.15 s ✓ | 0.15 s ✓ | 0.10 s ✓ |
| REDSTONE_ORE | 3.0 | iron+ | 15.00 s ✗ | 7.50 s ✗ | 3.75 s ✗ | 3.00 s ✗ | 0.75 s ✓ | 0.60 s ✓ | 0.50 s ✓ | 0.15 s ✓ | 0.15 s ✓ | 0.10 s ✓ |
| DIAMOND_ORE | 3.0 | iron+ | 15.00 s ✗ | 7.50 s ✗ | 3.75 s ✗ | 3.00 s ✗ | 0.75 s ✓ | 0.60 s ✓ | 0.50 s ✓ | 0.15 s ✓ | 0.15 s ✓ | 0.10 s ✓ |
| EMERALD_ORE | 3.0 | iron+ | 15.00 s ✗ | 7.50 s ✗ | 3.75 s ✗ | 3.00 s ✗ | 0.75 s ✓ | 0.60 s ✓ | 0.50 s ✓ | 0.15 s ✓ | 0.15 s ✓ | 0.10 s ✓ |
| ANCIENT_DEBRIS | 30.0 | diamond | 150.00 s ✗ | 75.00 s ✗ | 37.50 s ✗ | 30.00 s ✗ | 25.00 s ✗ | 5.65 s ✓ | 5.00 s ✓ | 1.30 s ✓ | 1.35 s ✓ | 0.95 s ✓ |
| OBSIDIAN | 50.0 | diamond | 250.00 s ✗ | 125.00 s ✗ | 62.50 s ✗ | 50.00 s ✗ | 41.70 s ✗ | 9.40 s ✓ | 8.35 s ✓ | 2.15 s ✓ | 2.25 s ✓ | 1.55 s ✓ |
| CRYING_OBSIDIAN | 50.0 | diamond | 250.00 s ✗ | 125.00 s ✗ | 62.50 s ✗ | 50.00 s ✗ | 41.70 s ✗ | 9.40 s ✓ | 8.35 s ✓ | 2.15 s ✓ | 2.25 s ✓ | 1.55 s ✓ |

Selected extra combinations:

```
OBSIDIAN        diamond   eff0 haste0 fat0 -> speed=8.0 ticks=188 = 9.40s drops=True
OBSIDIAN        netherite eff0 haste0 fat0 -> speed=9.0 ticks=167 = 8.35s drops=True
OBSIDIAN        diamond   eff5 haste0 fat0 -> speed=34.0 ticks=45 = 2.25s drops=True
OBSIDIAN        netherite eff5 haste0 fat0 -> speed=35.0 ticks=43 = 2.15s drops=True
OBSIDIAN        netherite eff5 haste1 fat0 -> speed=42.0 ticks=36 = 1.80s drops=True
OBSIDIAN        netherite eff5 haste2 fat0 -> speed=49.0 ticks=31 = 1.55s drops=True
OBSIDIAN        netherite eff0 haste2 fat0 -> speed=12.6 ticks=120 = 6.00s drops=True
OBSIDIAN        iron      eff0 haste0 fat0 -> speed=6.0 ticks=834 = 41.70s drops=False
OBSIDIAN        hand      eff0 haste0 fat0 -> speed=1.0 ticks=5000 = 250.00s drops=False
STONE           netherite eff0 haste0 fat1 -> speed=2.7 ticks=17 = 0.85s drops=True
DIAMOND_ORE     iron      eff0 haste0 fat0 -> speed=6.0 ticks=15 = 0.75s drops=True
DIAMOND_ORE     wood      eff0 haste0 fat0 -> speed=2.0 ticks=150 = 7.50s drops=False
ANCIENT_DEBRIS  diamond   eff5 haste0 fat0 -> speed=34.0 ticks=27 = 1.35s drops=True
ANCIENT_DEBRIS  netherite eff5 haste0 fat0 -> speed=35.0 ticks=26 = 1.30s drops=True
```

Haste I/II and Fatigue: multiply the tool speed by 1.2 / 1.4 / 0.3 (already reflected above where shown).

## 15. Stone vs Cobblestone (vanilla, Totality, and the live result)

* **Vanilla:** Stone hardness **1.5**, Cobblestone **2.0**. Both are `mineable/pickaxe`, both `requiresCorrectToolForDrops`, **neither** is in any `needs_*_tool` tag, so both need *any* pickaxe for drops and both are *breakable* by hand (Stone 7.5 s, Cobblestone 10.0 s, no drops). The only meaningful difference is hardness (Cobblestone takes 33 % longer).
* **Totality (CODE + RUNTIME):** `BlockDurability` fallback: durability = `hardness × 66.67` → Stone **100**, Cobblestone **133.3**; required tier = `1` for any `requiresCorrectToolForDrops` block with no `NEEDS_*` tag (both). Bare-hand tier = `clamp((DEX−8)/4, 0, 5)` → DEX 10 → 0 (**INEFFECTIVE** for *both*), DEX ≥ 12 → 1 (damageable, drops via `HarvestGrant`).
* **Conclusion:** Totality makes **no** eligibility distinction between Stone and Cobblestone. The live "Cobblestone INEFFECTIVE / Stone breakable" cannot be produced by the mapping; see §8 row 1.

## 16. Obsidian baseline

| Combination | Vanilla | Totality (STR 10, DEX 10, ordinary human; hand DEX 12) |
|---|---|---|
| Bare hand | 250.0 s, no drops | DEX 12 → tier 1 < 4 → **INEFFECTIVE**; DEX 28 (tier 5) → possible: dmg = 1 + STR mod → 3334 strikes at STR 10, 556 at STR 20, 159 at STR 50, **73 at STR 100** |
| Iron pickaxe | 41.7 s, **no drops** | tier 3 < 4 → **INEFFECTIVE** |
| Diamond pickaxe | **9.40 s** | Block Durability 3333.3; damage 48; **70 strikes**; 10-tick cycle → **34.65 s** (3.7×) |
| Netherite pickaxe | **8.35 s** | damage 54; **62 strikes**; **30.65 s** (3.7×) |
| Diamond + Efficiency V | **2.25 s** | ratio 4.25 → 4-tick cycle; 70 strikes → **13.90 s** (6.2×) |
| Netherite + Efficiency V | **2.15 s** | ratio 3.89 → 4-tick cycle; 62 strikes → **12.30 s** (5.7×) |
| Netherite + Efficiency V + Haste I / II | 1.80 s / 1.55 s | Haste ratio 1.2 / 1.4 → cycle 9 / 7 at Eff 0 (Eff V + Haste not run; cycle already at the 3–4-tick floor) |
| Netherite, Haste II, no Efficiency | 6.00 s | cycle 7 → 62 strikes ≈ 21.5 s |

Current Totality inputs (all CODE/RUNTIME): `DURABILITY_PER_HARDNESS = 100/1.5`; Netherite intrinsic speed on Obsidian = 9 (`minesAndDrops` rule) × `TOOL_STRUCTURAL_PER_SPEED 6` = 54 + STR modifier; cadence = `MiningTuning.cycleTicks(6, ratio)` with `ratio = Player.getDestroySpeed / intrinsic`, wind-up floor 2, recovery floor 1 (**minimum cycle 3 ticks**), base recovery 7.

**Diagnosis:** the Totality time is `strikes × cycle`; at the baseline cadence that is `(hardness × 66.67 / (speed × 6)) × 10 ticks = hardness × 111 / speed` ticks versus vanilla `hardness × 30 / speed` ticks — a constant **≈3.7×** for every block and tool at Efficiency 0. With Efficiency the ratio *grows* (5.7–6.2×) because the cycle cannot go below ~3–4 ticks while vanilla time keeps falling with speed. So the "too slow" feeling is **mainly a combination of too much Block Durability relative to Mining Damage (the 66.67 and ×6 constants) and the cadence floor**, not a mechanics bug. At equal total time the strike count matters separately: e.g. Netherite/Obsidian at vanilla's 8.35 s would be ≈ 16 strikes of 10 ticks, or ≈ 42 strikes of 4 ticks.

## 17. Strikes versus time

Totality time = `windUp + (strikes − 1) × cycle` ticks. The two levers are independent: *strikes* (Block Durability ÷ Mining Damage — how repetitive) and *cycle* (Mining Speed — how fast). Equal seconds can feel very different (5 strikes × 0.5 s vs 10 × 0.25 s). The translation table lists both. A vanilla time target does not by itself fix feel: the future balance pass should pick a desired *strike count* per block class first (e.g. a handful for stone, more for ores, dozens only for deliberately heavy blocks) and derive Durability from it.

## 18. Totality-vs-vanilla translation table (RUNTIME + DERIVED)

Representative stats: **STR 10 / DEX 10** for tools (modifier 0, no Efficiency unless stated); **bare hands use DEX 12** (tier 1) with STR 10 (damage 1). Cycle = windUp + recovery ticks from the real `MiningTuning`. Vanilla = §14 formula.

| Block | Vanilla hardness | Vanilla req. | Reference tool | Vanilla time (drops) | Totality Block Durability | Totality Req. Tier | Source Tier | Mining Damage / strike | Cycle (ticks) | Strikes | Totality seconds | Totality ÷ vanilla | Feel note |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| STONE | 1.5 | any pickaxe | hand (DEX 12) | 7.50 s (no) | 100.0 | 1 | 1 | 1 | 10 | 100 | 49.65 s | 6.6× | much slower than vanilla |
| STONE | 1.5 | any pickaxe | iron | 0.40 s (yes) | 100.0 | 1 | 3 | 36 | 10 | 3 | 1.15 s | 2.9× |  |
| STONE | 1.5 | any pickaxe | netherite + Eff 5 | 0.10 s (yes) | 100.0 | 1 | 4 | 54 | 4 | 2 | 0.30 s | 3.0× |  |
| COBBLESTONE | 2.0 | any pickaxe | hand (DEX 12) | 10.00 s (no) | 133.3 | 1 | 1 | 1 | 10 | 134 | 66.65 s | 6.7× | much slower than vanilla |
| COBBLESTONE | 2.0 | any pickaxe | iron | 0.50 s (yes) | 133.3 | 1 | 3 | 36 | 10 | 4 | 1.65 s | 3.3× | ≈3–4× vanilla |
| DEEPSLATE | 3.0 | any pickaxe | iron | 0.75 s (yes) | 200.0 | 1 | 3 | 36 | 10 | 6 | 2.65 s | 3.5× | ≈3–4× vanilla |
| DEEPSLATE | 3.0 | any pickaxe | netherite | 0.50 s (yes) | 200.0 | 1 | 4 | 54 | 10 | 4 | 1.65 s | 3.3× | ≈3–4× vanilla |
| DEEPSLATE | 3.0 | any pickaxe | netherite + Eff 5 | 0.15 s (yes) | 200.0 | 1 | 4 | 54 | 4 | 4 | 0.70 s | 4.7× | ≈3–4× vanilla |
| DIAMOND_ORE | 3.0 | iron+ | wood | 7.50 s (no) | 200.0 | 3 | 1 | 12 | 10 | — | INEFFECTIVE | n/a | vanilla still breaks it (no drops); Totality gates by Tier |
| DIAMOND_ORE | 3.0 | iron+ | iron | 0.75 s (yes) | 200.0 | 3 | 3 | 36 | 10 | 6 | 2.65 s | 3.5× | ≈3–4× vanilla |
| DIAMOND_ORE | 3.0 | iron+ | netherite | 0.50 s (yes) | 200.0 | 3 | 4 | 54 | 10 | 4 | 1.65 s | 3.3× | ≈3–4× vanilla |
| DIAMOND_ORE | 3.0 | iron+ | netherite + Eff 5 | 0.15 s (yes) | 200.0 | 3 | 4 | 54 | 4 | 4 | 0.70 s | 4.7× | ≈3–4× vanilla |
| OBSIDIAN | 50.0 | diamond | iron | 41.70 s (no) | 3333.3 | 4 | 3 | 36 | 10 | — | INEFFECTIVE | n/a | vanilla still breaks it (no drops); Totality gates by Tier |
| OBSIDIAN | 50.0 | diamond | diamond | 9.40 s (yes) | 3333.3 | 4 | 4 | 48 | 10 | 70 | 34.65 s | 3.7× | ≈3–4× vanilla |
| OBSIDIAN | 50.0 | diamond | netherite | 8.35 s (yes) | 3333.3 | 4 | 4 | 54 | 10 | 62 | 30.65 s | 3.7× | ≈3–4× vanilla |
| OBSIDIAN | 50.0 | diamond | diamond + Eff 5 | 2.25 s (yes) | 3333.3 | 4 | 4 | 48 | 4 | 70 | 13.90 s | 6.2× | much slower than vanilla |
| OBSIDIAN | 50.0 | diamond | netherite + Eff 5 | 2.15 s (yes) | 3333.3 | 4 | 4 | 54 | 4 | 62 | 12.30 s | 5.7× | much slower than vanilla |
| ANCIENT_DEBRIS | 30.0 | diamond | diamond | 5.65 s (yes) | 2000.0 | 4 | 4 | 48 | 10 | 42 | 20.65 s | 3.7× | ≈3–4× vanilla |
| ANCIENT_DEBRIS | 30.0 | diamond | netherite | 5.00 s (yes) | 2000.0 | 4 | 4 | 54 | 10 | 38 | 18.65 s | 3.7× | ≈3–4× vanilla |
| ANCIENT_DEBRIS | 30.0 | diamond | netherite + Eff 5 | 1.30 s (yes) | 2000.0 | 4 | 4 | 54 | 4 | 38 | 7.50 s | 5.8× | much slower than vanilla |

Full runtime table (all 18 blocks × 7 tools × Efficiency 0/5): see the appendix.

## 19. Bare-hand rule (current design, unchanged)

Bare hands: Mining Damage = `max(0.1, 1 + STR modifier)`; Mining Tier = `clamp((DEX − 8) / 4, 0, 5)`. If the DEX-derived tier meets the block's required tier the source is *eligible* and, through the exact-position `HarvestGrant`, receives the normal drops (RUNTIME-verified above). No physiology/technique/buff gating was introduced. The values are far from vanilla (a bare hand at STR 10 needs 100 strikes ≈ 50 s for Stone, 6.6× vanilla's 7.5 s).

## 20. Findings that the balance pass should start from (no values changed here)

* Baseline strike-time ≈ 3.7× vanilla for every block/tool (constant-factor issue: durability-per-hardness and damage-per-speed).
* Efficiency helps only up to the cadence floor (Netherite + Eff V: 4-tick cycle); vanilla continues to scale (35 vs 9 speed = 3.9×).
* Hand mining is 6–7× slower than vanilla *and* tier-gated, unlike vanilla (which lets it break anything slowly without drops).
* Tier mapping: Totality tier 1 = any pickaxe (including wood and **gold**), 2 = stone/**copper**, 3 = iron, 4 = diamond/netherite; consistent with the vanilla tags, but breaking ≠ harvesting is collapsed (F-05).
* Netherite pickaxe is only 12 % faster than diamond in vanilla (speed 9 vs 8) and Totality preserves that (54 vs 48 damage) — gold (12.0) is vanilla's fastest base speed yet its Totality Force Tolerance is the lowest by design.
* Copper pickaxe (26.2) is not in the Force Tolerance material list (falls back to the tier value).

## 21. Executive conclusions

1. **Is unified `master` healthy?** Yes. Build, 1717 JUnit tests and every dev suite match the baseline; all 51 mixins apply on a real dedicated server and client; registration is clean.
2. **Confirmed integration regression?** None found. The two merge conflicts were resolved additively and `git diff master -- <auto-merged files>` shows only the mining lines.
3. **Most important correctness findings:** F-01 (HIGH — client-supplied `ItemStack`s written into the Component Pouch), F-02 (superseded +5 attribute points still live), F-09 (empty SavedData files), F-10 (dead payloads).
4. **Most important test gaps:** dev suites/mining outside `gradle build`; no JUnit for mining; fake-player blind spots (invulnerable, no stats, no connection, drops unobservable in far chunks); 31 source-text tests; no network/render/client integration tests; the historical Offhand failure.
5. **Stone bare-hand no-drop:** a **reproduction issue** — in the runtime reproduction it drops; **likely already fixed** by the scoped grant; if it still happens live it becomes a confirmed bug in a path the tests cannot see.
6. **Cobblestone INEFFECTIVE with hands vs Stone breakable:** Totality treats both identically (tier 1, DEX ≥ 12 needed); the live difference must come from DEX/held item, not the mapping.
7. **Obsidian:** mainly a **balance issue**, not a mechanics bug (constants + cadence floor).
8. **Numbers:** Netherite/Obsidian: Totality 62 strikes, 30.65 s (Eff 0) and 12.30 s (Eff V) vs vanilla 8.35 s / 2.15 s.
9. **Most misbalanced (versus vanilla):** anything with Efficiency (5–6×), bare hands (6–7×), and every hard block (Obsidian 3333, Ancient Debris 2000 Durability) at baseline cadence (3.7×); gold vs netherite ordering is intentional.
10. **Fix BEFORE balancing:** F-01 (security, independent), confirm/close F-06 live, decide F-05 (breakability vs harvesting), remove or account for F-02 in progression, make the dev suites/mining tests run in CI (or a documented command), F-09.
11. **Wait for authored Mining Stats:** the Durability/Damage/Speed/Tier/Force Tolerance numbers themselves, per-block Required Tier, copper/gold materials, hand rules, Impact Cost Policy.
12. **Useful vanilla targets:** Stone 0.40 s iron / 0.25 s netherite; Deepslate 0.75 s / 0.50 s; Diamond Ore 0.75 s iron; Obsidian 9.4 s diamond / 8.35 s netherite / 2.15 s netherite+Eff V; Ancient Debris 5.65 s / 5.0 s (see §14 as the starting reference, as *time* targets; pick strike counts separately, §17).
13. **Unrelated to mining, own future work:** F-01, F-02, F-10, F-11, F-12, F-13, the Offhand failure (F-15), F-19.
14. **Safe to continue from `master`?** Yes — apart from F-01 (a multiplayer exploit that should be fixed soon, independent of mining).

## 22. Ordered priorities

**FIX BEFORE MINING BALANCE**
1. F-01 `ContainerSortPayload` trusts client stacks (security).
2. Live re-test of bare-hand Stone drop (F-06) — close or open a bug.
3. Decide breakability-vs-harvest semantics (F-05) so balance targets are meaningful.
4. Put mining/dev verification where a normal build sees it, or document the run command (F-04).
5. Reconcile F-02 (+5 attribute points) so progression-dependent tuning uses the intended rules.
6. F-09 (empty SavedData files) — minor cleanup.

**ADDRESS DURING MINING BALANCE**
Block Durability constants and per-block values; Mining Damage per tool/STR; Mining Speed and the cadence floor/Efficiency scaling; Required Mining Tier per block (incl. copper/gold); strike-count and seconds targets per block class; Haste/Fatigue feel; hand mining rules; Force Tolerance/strain values.

**DEFER**
Animation API, modular tools, drills and the generic Impact Cost Policy, two-handed tools, radial Power HUD, Popeye/Gacha skin, Veinminer rewrite, physiology/technique gating, anything depending on the wider D&D/RPG foundation.
