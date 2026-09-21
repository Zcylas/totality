# Totality — Block Breaking API V1

> ## STATUS: ACCEPTED / IMPLEMENTED (V1)
> Block Breaking API V1 is closed. **Final live result (developer):** the current **tool mining animations are accepted**; the **bare-hand Alt Power Mining animation is accepted**; the **normal bare-hand chambered punch is accepted for V1** (it is not considered the final possible animation quality — further polish may be revisited through a future Animation API, §20; no more time is spent on it in V1).
>
> **V1 COMPLETE:** authoritative discrete impacts (server contact-frame raycast) · shared, persistent Block Integrity · Block Durability (fallback + override registries) · Mining Tier eligibility · Mining Damage (STR modifier, tool intrinsic speed, bare hands) · cadence (Efficiency/Haste/Fatigue drive speed only) · persistence (per-level SavedData, lazy recovery) · vanilla crack synchronisation (immediate join/respawn + 40-tick refresh) · final destruction through vanilla `destroyBlock` · drops/Fortune/Silk Touch integration · normal tool animation · normal bare-hand chambered punch · Power Mining (server-derived force, force bands, Force Tolerance for tools) · bare-hand body strain (V1) · Combat Text (BLOCK_DAMAGE presentation bands, INEFFECTIVE) · source identity (server and client) · durability normalisation (exactly 1 per successful tool impact) · exact-position harvest grant.
>
> **PROVISIONAL (tuning, will be rebalanced):** all numerical mining balance (`MiningTuning`) · the body-strain values (orange 2 HP, red 6 HP) · the vanilla-derived mining source calculations (intrinsic speed × 6, `getDestroySpeed`-based cadence) · Force Tolerance tuning · recovery policy.
>
> **DEFERRED (not part of V1):** authored Mining Stats · universal mining tooltips (effective values + Shift provenance) · block structural tooltip · modular tools · drills / generic Impact Cost Policy (energy, fuel, resource...) · two-handed tools · a reusable Animation API · circular/radial Power Mining HUD art · Popeye-like cosmetic/Gacha animation skin · Veinminer rewrite (50 % aggregate work) · mining source taxonomy · third-person mining animation · final large-scale block balance.

Developed on `feature/block-breaking-api` (cut from `master` @ `bc16cc3`), committed as `2193939` + `1946a42`, then integrated with the canonical line (`master` = `feature/soul-gems` @ `3170e2f`) by merge commit `ab9ef6a`; `master` was fast-forwarded to the result. Minecraft 26.2, Fabric (Loader 0.19.3 / Fabric API 0.161.0+26.2 — the bump in `gradle.properties` is intentional), Java 25, no new dependencies.

Legend: **FINAL V1 BEHAVIOR** = the rule/structure is decided for V1; **PROVISIONAL** = a tuning constant that will be rebalanced. **IMPLEMENTED** · **PROVISIONAL** (works, number/policy is a tuning placeholder) · **DEFERRED** · **OPEN** (design question).

The Incremental Mining audit (`TOTALITY_INCREMENTAL_MINING_AUDIT.md`) was used only for high-level lessons. No code, tables, config, names or assets were taken from that mod.

---

This revision follows the live-client playtests. **Reading guide:** every section is labelled FINAL V1 BEHAVIOUR (current), PROVISIONAL (tuning), DEFERRED (future), or HISTORICAL (superseded and kept only as a record). The three mining axes are now strictly separate: **Mining Power** (structural damage per impact), **Mining Tier** (may this source damage/harvest the material) and **Mining Cadence** (how often normal swings happen). No value from one axis performs the job of another.

---

## 1. Architecture (IMPLEMENTED)

```
 BlockDurability (fallback / per-block / per-material definitions)
            │
            ▼
 BlockDamageStorage  ── SavedData per dimension, keyed by BlockPos, shared by all sources
            ▲
            │
 BlockBreaking.applyImpact(ServerLevel, MiningImpact)   ← the single entry point
      ▲              ▲                 ▲
 PlayerMiningManager  (future) mobs / companions / machines / spells / explosions
 (server swing loop)
```

* **Block Durability** = max structural HP; **Block Integrity** = what remains. Damage is an *absolute* quantity (`MiningImpact.damage`), never `1/swings`, so unequal sources compose.
* **Mining Tier**, **Mining Power** and **cadence** are separate: tier = `MiningImpact.tier` vs the block's required tier; power = `MiningImpact.damage`; cadence = the server swing loop's timings.
* `applyImpact` is actor-generic: `MiningSource(kind, @Nullable Entity actor)`. Only when the actor is a `ServerPlayer` do the player permission checks and the player destruction path apply.

### Files created

| File | Role |
|---|---|
| `api/mining/BlockBreaking` | `applyImpact` — durability, tier gate, integrity arithmetic, terminal break, feedback |
| `api/mining/MiningImpact`, `MiningSource`, `MiningResult` | impact record, source/actor abstraction, outcome |
| `api/mining/BlockDurability` | Max Durability + required tier resolution; crack-stage formula; override/material registries (empty by default) |
| `api/mining/BlockDamageStorage` | persistent, position-owned state; lazy recovery; crack sync; sweep |
| `api/mining/MiningTier` | tier of a tool (probed), fallback required tier of a block |
| `api/mining/MiningTuning` | **every** provisional number/formula |
| `api/mining/PlayerMiningPower` | player + tool + force → damage / tier / kind (STR lives here) |
| `api/mining/PlayerMiningManager` | server mining sessions, swing/contact loop, intent receiver, crack sweep tick |
| `api/mining/MiningPermissions` | the vanilla START-time checks + Fabric `AttackBlockCallback` |
| `api/mining/MiningFeedback` | contact sound, particles, floating structural text |
| `api/mining/MiningVerification` | dev-only server self-test |
| `networking/mining/MiningIntentPayload` | C2S intent (`HOLD_START`, `HOLD_STOP`, `POWER_START`, `POWER_RELEASE`, `POWER_CANCEL`) — carries no force |
| `client/mining/ClientMiningController` | intent sending + Power Mining meter state and HUD |
| `mixin/mining/ServerPlayerGameModeMixin` | server: refuse vanilla START/STOP destroy for owned survival mining |
| `mixin/client/MinecraftMiningMixin` | client: stop vanilla `startAttack`/`continueAttack` on owned blocks |
| `api/mining/MiningOwnership` | the single rule "does Totality mining own this strike?" used by BOTH client and server |
| `api/mining/HarvestGrant` | exact-position harvest eligibility for a Tier-qualified bare-hand terminal break (consulted from `ServerPlayerGameModeMixin`) |
| `api/mining/MiningSourceIdentity` | the ONE "same mining source?" rule used by server (contact) and client (animation) |
| `client/mining/MiningHandAnimation` | first-person mining presentation state + timeline (pure math, no Minecraft imports) |
| `mixin/client/MiningHandRenderMixin` | layers the mining pose over vanilla first-person rendering (`ItemInHandRenderer`) |
| `networking/mining/MiningSwingPayload` | S2C: the real timings of each normal swing (cadence included); animation only |
| `client/mining/PowerMiningMeterHud` | placeholder procedural meter renderer, split from the controller so a sprite-based ring can replace it |

### Files changed

`Totality.java` (register manager + verification), `TotalityClient.java` (register controller), `networking/TotalityPackets.java` (payload), `totality.mixins.json` (3 mixins), the Combat Text files (`CombatTextEntry`, `CombatTextManager`, `CombatTextRenderer`, `CombatTextClientHandler`, `CombatTextPayload` — a trailing presentation `style`), `CodecSavedData` (a test-only `create(SavedDataStorage)` overload) and `gradle.properties` (intentional Fabric API 0.161.0+26.2).

### Mixins / hooks

| Hook | Where | Purpose |
|---|---|---|
| `ServerPlayerGameMode.handleBlockBreakAction` HEAD (cancel) | `ServerPlayerGameModeMixin` | Refuse `START_DESTROY_BLOCK`/`STOP_DESTROY_BLOCK` for Survival players on blocks with hardness > 0. Creative, Adventure, Spectator, hardness-0 (instant) and unbreakable blocks stay vanilla. `ABORT` untouched. The client gets a block update. |
| `Minecraft.startAttack` HEAD, `Minecraft.continueAttack(boolean)` HEAD | `MinecraftMiningMixin` | Cancel vanilla click/hold mining (and its own swing) when the crosshair is on an owned block. Entity attacks, Power Attack and dual-wield are unaffected. |
| `ServerPlayerGameMode.destroyBlock` → `@WrapOperation` on the `ServerPlayer.hasCorrectToolForDrops(adjustedState)` call, with `destroyBlock`'s own `BlockPos` (`@Local` arg) | `ServerPlayerGameModeMixin` | Asks `HarvestGrant.grants(player, breakPos, state)` first; otherwise the original vanilla call. `Player.hasCorrectToolForDrops` itself is never altered (§7). |
| `ItemInHandRenderer.submitHandsWithItems` → `@ModifyArg` on the main-hand `submitArmWithItem` call (ordinal 0, index 4 = swing progress) | `MiningHandRenderMixin` | Zero the vanilla main-hand swing while any mining animation owns the hand (§17) |
| `ItemInHandRenderer.submitArmWithItem` → `@WrapOperation` on `renderItem` and on `renderPlayerArm` | `MiningHandRenderMixin` | push/pop one extra transform for a MAIN-hand item (tool, or a non-tool held item) / for the bare main arm while the animation is active (§17) |
| Fabric `ServerTickEvents.END_SERVER_TICK` | `PlayerMiningManager` | Swing loop + crack sweep |
| Fabric `ServerPlayNetworking` receiver | `PlayerMiningManager` | Mining intent |
| Fabric `ClientTickEvents.END_CLIENT_TICK`, `HudElementRegistry` | `ClientMiningController` | Intent + meter |

The server still triggers vanilla's `swing()` (so other players see a swing); in first person that vanilla motion is suppressed while a Totality mining animation owns the main hand (§17).

---

## 2. Normal mining (IMPLEMENTED)

1. Client: LMB held on an owned block → **one** `HOLD_START`; release → **one** `HOLD_STOP`. Nothing per tick.
2. Server session per mining player (only while mining; removed on release, death, disconnect, non-Survival, item use).
3. `IDLE`: if holding (or a click was requested) and the server's own pick finds a block → `player.swing(MAIN_HAND, true)` and enter the pre-contact delay.
4. **Contact frame** (after the pre-contact delay): the server raycasts again from the player's *current* eye position, rotation and reach. Hit a block → one `MiningImpact`. Missed / entity in the way / block gone → no impact, animation already played.
5. Recovery, then back to `IDLE`; if still held, the next swing begins. A tap always produces one swing attempt.

The target is **not** locked at click time; looking away or leaving reach before the contact frame makes the swing miss; a block that vanished or was replaced is judged by what is there *now* (live-tested: retargeting from Stone to Dirt mid-swing damages Dirt, not Stone).

**Animation (HISTORICAL → CURRENT):** the first live playtest showed only vanilla's ordinary swing, which did not read as a wind-up. That is **superseded**: since the animation passes, first-person mining is a dedicated cadence-driven strike (tools) or a dedicated one-arm strike (bare hands), and Power Mining has its own charge/release/heavy-strike animation — §17. The live tester reported the tool mining animation **feels good**.

### Cadence (FINAL structure / PROVISIONAL numbers)

* `cadenceRatio = Player.getDestroySpeed(state) / ItemStack.getDestroySpeed(state)` (full vanilla break speed over the item's own intrinsic speed), clamped to `[0.05, 20]`. It is used **only** here and never for damage.
* Inherited from `Player.getDestroySpeed` (verified in the 26.2 source): the `MINING_EFFICIENCY` attribute (only added when the intrinsic speed is > 1), Haste (`×(1 + 0.2·(amp+1))`), Mining Fatigue (`×0.3 / 0.09 / 0.0027 / 8.1E-4`), the `BLOCK_BREAK_SPEED` attribute, `SUBMERGED_MINING_SPEED` (eyes in water) and airborne `÷5`.
* `windUp = max(2, round(max(2, round(swingDuration × 0.5)) / ratio))` (`swingDuration` = the item's `SWING_ANIMATION.duration()`), `recovery = max(1, round(7 / ratio))`. Floors make the shortest cycle 3 ticks whatever the Efficiency; the clamp bounds Fatigue stalls.
* **Power swings ignore cadence** (their wind-up is `2 × base`, recovery 7) and the Power meter sweeps at a fixed speed: Efficiency/Haste/Fatigue never scale the force or the damage of a deliberate Power swing.
* Mining cadence deliberately does **not** read combat Attack Speed (DEX).
* Measured (automated, iron pickaxe on Stone, ground): baseline cycle 10 ticks; Efficiency V 3; Haste II 7; Efficiency+Haste 3; Mining Fatigue I 33.

## 3. Durability, Integrity, Tier (IMPLEMENTED / PROVISIONAL)

* **Fallback Max Durability** = `hardness × 100/1.5` (stone → 100). PROVISIONAL constant `DURABILITY_PER_HARDNESS`.
* Resolution order: per-block definition → first matching material tag definition → fallback. No definitions are registered; no block tables exist.
* **Required tier fallback:** `!requiresCorrectToolForDrops` → 0; else 1; `NEEDS_STONE_TOOL` → 2; `NEEDS_IRON_TOOL` → 3; `NEEDS_DIAMOND_TOOL` → 4.
* **Source classification (FINAL, corrected):** `MiningTier.isToolSource(stack)` = the stack has the real `TOOL` data component. Axes, shovels, hoes, shears and modded tools are tool sources; the empty hand and arbitrary items (a stick, food) are `BARE_HANDS`. This is deliberately **separate** from Tier.
* **Tool Tier:** `MiningTier.ofTool` probes the tool's own rules against stone / iron ore / diamond ore / obsidian → 0…4 (no item list). It is pickaxe/material oriented: an axe or shovel probes 0 and can only work Tier-0 blocks, yet is still a `PLAYER_TOOL`. Netherite is not distinguishable from diamond by vanilla rules (DEFERRED).
* **The active source alone decides the Tier (FINAL):** `PLAYER_TOOL` → the tool's Tier only; `BARE_HANDS` → the DEX-derived Tier only. There is **no** `max(toolTier, handTier)`: high DEX never upgrades a held wooden pickaxe (verified).
* Hardness 0 → breaks on any effective impact (vanilla keeps instant blocks on its own path anyway). Hardness < 0 → invalid target.
* Impact below required tier → `INEFFECTIVE`: no integrity lost, contact sound/particles + "INEFFECTIVE" floating text.

## 4. Mining Power: STR, tools, bare hands (FINAL structure / PROVISIONAL constants — rewritten after live test)

**Live-test failures of the previous formulas (ground truth):** the tool path multiplied `Player.getDestroySpeed` (which contains Efficiency, Haste, Fatigue, water, airborne) by a raw-STR percentage, so at STR 100 red zone the same Netherite Pickaxe measured ~1.6k (plain), ~5.9k (Efficiency V), ~2.5k (Haste II), ~9.4k (both), ~453 (Fatigue I); STR 14 → 16 changed tool damage from ~58 to ~60 instead of by exactly the STR modifier step; bare-hand damage ignored STR at 12/14 (showed 1). All corrected below.

Common rule (`MiningTuning.impactDamage`): `damage = max(0.1, base + STRmod) × powerDamageMultiplier`, where `STRmod = PlayerStats.getModifier(STR)` = `floor((STR−10)/2)` (Totality's authoritative modifier) and the 0.1 floor keeps negative modifiers from ever producing negative damage.

* **Tool:** `base = intrinsic tool speed × 6`, intrinsic speed = `ItemStack.getDestroySpeed(state)` = the item's own `TOOL` rules (verified: `Item.getDestroySpeed` returns `Tool.getMiningSpeed` or 1.0 — no Efficiency, Haste, Fatigue, water or airborne). Example: Netherite Pickaxe on Stone: `9 × 6 = 54`; STR 14 → 56, STR 16 → 57 (the step is exactly the +2 → +3 modifier difference; automated-tested).
* **Bare hands (or a non-tool item):** `base = 1`, so damage = `max(0.1, 1 + STRmod)`: STR 10 → 1, 12 → 2, 14 → 3, 16 → 4 (automated-tested). The old quadratic curve and the STR-100 Stone one-shot are intentionally gone (STR 100 bare hands = 46). Extreme physical scaling will be rebalanced with the wider RPG redesign.
* **Efficiency, Haste and Mining Fatigue never touch damage** — they only feed cadence (§2). Automated proof: identical damage for Efficiency V / Haste II / both / Fatigue I, for normal and Power swings.
* **Bare-hand Tier is DEX-derived** (PROVISIONAL, will later become Physiology/techniques/Permanent Buffs/DEX): `bareHandTier(DEX) = clamp((DEX−8)/4, 0, 5)`: DEX 10 → 0, 12 → 1, 16 → 2, 20 → 3, 24 → 4, 28 → 5. High STR with low DEX has Tier 0 and stays INEFFECTIVE against Stone.
* A base of 1 (not 0) means an eligible hand can still scratch materials that need no Tier; Tier still decides whether a material can be damaged at all.

## 5. State ownership and persistence (IMPLEMENTED)

* Owner: `BlockDamageStorage`, a `SavedData` (via the repo's `CodecSavedData`) on each `ServerLevel`'s data storage, id `totality:block_damage`. Not a static map, not player-keyed.
* Entry: `pos, block (registry id), max, integrity, lastImpactTick` (all persisted). `crackId` and `sentStage` are transient.
* **Identity:** any read whose block registry id differs from the stored one discards the entry (and clears its crack); damage cannot transfer to a replacement block. Different *state* of the same block keeps the damage.
* **Recovery (PROVISIONAL, policy OPEN):** none for 600 ticks after the last impact, then linear, full in 60 s. Evaluated **lazily** from timestamps on read; nothing ticks a block to heal it. Game time is persisted with the level, so it survives restarts.
* **Sweep:** every 40 ticks, only entries in *loaded* chunks are visited (prune replaced/air/recovered, re-send current crack). Cost is O(damaged blocks) per 40 ticks, never per tick; nothing at all while the storage is empty. Unloaded chunks are skipped, so unloading needs no work.

### Persistence — live-test finding, diagnosis and fix

**Manual live result:** damaged Stone, Save & Quit, re-entered the world: the Stone appeared fully repaired.

**Findings (evidence, not assumption):**
* The world's `dimensions/minecraft/overworld/data/totality/block_damage.dat` from that playtest **contains both damaged blocks** (Stone 32.8/100, Dirt 16.5/33.3, correct `last_impact` ticks, correct block ids). Saving worked.
* The client log timeline: Save & Quit at 10:34:08, re-entered at 10:34:10, the game was then paused ~4 s later and stayed paused; world time advanced only **81 ticks** after the impact (61386 → 61467). No legitimate recovery could have happened (delay is 600 ticks).
* A dev server started on a **copy of that world** loaded both entries and both survived a maintenance sweep at game time 61467 (`2 persisted damaged block(s) loaded, 2 after sweep`).
* A real `SavedDataStorage` save → new storage instance → load round trip (new automated test) preserves block identity, max, integrity and last-impact tick, and does not heal on reload.

**Root cause of the observed symptom:** the *data* was not lost; the *presentation* was. Cracks were only re-broadcast by the periodic sweep (then every 100 server ticks, counted from server start) and never when a player joined. With a session of only ~81 ticks before pausing, the persisted crack was never sent, so the block looked repaired. (The 100-tick counter also restarts every session.) This is the most consistent explanation of the evidence; it was **not** re-verified in a live client after the fix.

**Fix (IMPLEMENTED):** `BlockDamageStorage.syncTo(player)` sends every persisted crack within 32 blocks on player join (`ServerPlayConnectionEvents.JOIN`) and respawn (`ServerPlayerEvents.AFTER_RESPAWN`); the sweep interval was lowered 100 → 40 ticks (covers dimension changes and chunk load-ins; still ≪ the 400-tick client expiry). **Hardened in the second pass:** the join/respawn path now validates each entry exactly like the sweep (shared `validate`): a replaced block, air, or a fully recovered entry is pruned (crack cleared, SavedData marked dirty) instead of being sent; the entry list is copied first so pruning cannot cause concurrent modification. `cracksNear(...)` is tested. Note: `CodecSavedData` builds its `SavedDataType` with a `null` `DataFixTypes`; vanilla's `readTagFromDisk` would dereference it, but the round-trip test proves loading works on this Fabric API — flagged as a latent risk for future Minecraft updates, not changed here.

**Live retest: PASS** — partial damage persists across Save & Quit / reload (developer-reported).

## 6. Crack sync (IMPLEMENTED)

Vanilla `ServerLevel.destroyBlockProgress`, stage `floor(10 × damageTaken/max)` clamped 0–9, −1 clears. Each damaged block gets one **negative** synthetic breaker id (`Integer.MIN_VALUE + n`), which cannot collide with entity ids and, being ≠ any player id, is also sent to the miner. One id per position → one coherent crack for every source. Re-sent every 40-tick sweep (well under the client's ~400-tick expiry) and immediately, validated, on join and respawn, which also covers cracks that persist. There is no waiting period for late joiners any more. Cleared on break, replacement, recovery. (Custom fracture/rubble: DEFERRED.)

## 7. Final break and server validation (IMPLEMENTED)

At **every impact** by a player actor (`MiningPermissions.mayStrike` — the checks vanilla only does at START): interaction range (+1.0), max build height, spawn protection, `mayInteract`, `blockActionRestricted`, and Fabric `AttackBlockCallback` (what protection mods hook). Then `EnchantmentHelper.onHitBlock` and `BlockState.attack` (the vanilla START hooks).

Terminal break (integrity ≤ 0): a `ServerPlayer` goes through `ServerPlayerGameMode.destroyBlock` (loot, Silk Touch, Fortune, tool durability, XP, stats, `PlayerBlockBreakEvents`); other actors use `Level.destroyBlock(pos, true, actor)`. If refused: integrity is left at 0.001, the block stays, **no retry counter and no force-break fallback**.

### Verified Fabric break-event injection (26.2 / `fabric-events-interaction-v0 5.2.8`)

`ServerPlayerGameModeMixin` in Fabric injects into `ServerPlayerGameMode.destroyBlock`:

* `PlayerBlockBreakEvents.BEFORE` (+`CANCELED` if false, returning `false` from `destroyBlock`): at `INVOKE Block.playerWillDestroy` — i.e. *after* `canDestroyBlock`, GameMaster and `blockActionRestricted` checks, *before* the block is removed.
* `PlayerBlockBreakEvents.AFTER`: at `INVOKE Block.destroy`, which vanilla only calls `if (changed)` (the block was really removed).
* `AttackBlockCallback` fires at `handleBlockBreakAction` HEAD for `START_DESTROY_BLOCK` only — irrelevant to our path, hence called explicitly in `mayStrike`.

Consequences: Mining XP (`MiningSkillEvents`, AFTER) works unchanged on the terminal break. Veinminer (BEFORE) is triggered by it; it removes the block itself and returns `false`, so `destroyBlock` reports `false`. `applyImpact` therefore treats "the block at `pos` is no longer the original block" as `BROKEN`, not as a denial — the compatibility adjustment for Veinminer, no Veinminer code change required.

### Per-impact tool wear (FINAL V1 rule, corrected)

**Every successful (DAMAGED) tool impact costs exactly 1 base durability, regardless of the tool's vanilla `Tool.damagePerBlock`** (`MiningTuning.BASE_WEAR_PER_IMPACT`; the previous pass wrongly read `damagePerBlock`). The terminal break goes through vanilla `Item.mineBlock`, which would charge `damagePerBlock` (2 for swords, or any tool whose component says so); for that one synchronous call `BlockBreaking` normalises the component's `damagePerBlock` to 1 **whenever it is not 1 (0 would otherwise make the terminal hit free, 2 too costly)** and always restores it in `finally`, so the terminal hit costs exactly 1 too and is never double-charged. Result: a block that needs 6 successful impacts costs exactly 6 durability. MISS, DENIED, INEFFECTIVE and INVALID cost nothing; hands never wear. Power swings add Force Stress on DAMAGED and BROKEN outcomes on top: partial = `1 + stress`, final = `1 + stress`. If a future source/tool wants a different base wear it must become an explicit Totality mining rule.

### Mid-swing source snapshot (FINAL)

At swing start the server records a copy of the held source (`Session.swingSource`); the **target block is deliberately not snapshotted** (retargeting stays dynamic and live-tested). At contact `PlayerMiningManager.sameSource` compares the snapshot with the current main-hand stack through `MiningSourceIdentity` (`ItemStack.matchesIgnoringComponents`, ignoring durability and swap-irrelevant components) — the **same rule the client animation uses**, so a swap the server cancels is never visually finished. So ordinary wear on the same tool is not a swap, but a different item (or hands ↔ tool) voids that swing entirely: no damage, wear, stress, crack, text or break. If LMB is still held the next swing starts with the new item's own cadence/power/tier. Closes the exploit of combining Efficiency cadence from tool A with tier/power of tool B.

### Bare-hand harvest eligibility (FINAL V1 rule, exact-position)

Vanilla `ServerPlayerGameMode.destroyBlock(BlockPos)` runs the harvest/drop path only when `ServerPlayer.hasCorrectToolForDrops(adjustedState)` is true, and an empty hand is never the "correct tool" for a tool-requiring block (live: strong hands destroyed Stone with no Cobblestone). `hasCorrectToolForDrops(BlockState)` receives **no position**, so a grant consulted *inside that method* could never prove which block a query is about (an earlier revision tried to infer it from "the position no longer holds the block" — that was a weaker guarantee than the text claimed, and is **HISTORICAL/removed**).

Now the grant is consulted at the one place the position IS known: `ServerPlayerGameModeMixin` wraps the `hasCorrectToolForDrops` call **inside `destroyBlock`** and passes `destroyBlock`'s own `pos`. `Player.hasCorrectToolForDrops` is left completely alone (`PlayerHarvestMixin` was deleted), so no other caller anywhere can be affected. A query is granted only if **all** hold: a `HarvestGrant` is open on the calling thread (opened only by `BlockBreaking` around its synchronous `destroyBlock` for a `BARE_HANDS` source whose Tier already passed the block's gate); the querying player **is** the granted player; the break position **equals** the granted `BlockPos` (and the granted level); the state is the granted block type; and the grant has not already answered (**single use**). Grants live in a per-thread stack (the client thread never sees the server's), each `close()` removes exactly its own entry, nested breaks keep separate player+position scopes, and try-with-resources guarantees cleanup after an exception. Plain vanilla bare-hand mining is untouched; bare hands have no Silk Touch/Fortune. Verified end to end through real `destroyBlock` calls (§15): the granted position is answered exactly once; a same-type block elsewhere, another player, another block type, a second query, and everything after the scope closes are refused.

### Ownership symmetry (FINAL)

`MiningOwnership` is the single rule used by the client (to cancel vanilla `startAttack`/`continueAttack`) and by the server mixin (to refuse vanilla START/STOP): Survival only, **not** an item with `PIERCING_WEAPON`, hardness > 0. `PIERCING_WEAPON` (spears) was excluded on the client because in 26.2 `startAttack` stabs and `continueAttack` skips such items, so vanilla never mines with them — but the server previously had no such exception. Both sides now hand that case to **vanilla**. (Client input additionally requires "not using an item"; the server session loop already drops sessions while an item is used.)
## 8. Floating structural damage (IMPLEMENTED)

The existing Combat Text pipeline is reused, not duplicated. `TextType` gained `BLOCK_DAMAGE` (`-24`) and `INEFFECTIVE`. `CombatTextPayload` is unchanged (damage type null, entity ids −1); the server sends it with the same 32-block audience as combat text, at the actual `BlockHitResult` location. `CombatTextManager.spawnBlockDamage/spawnIneffective` skip the entity-height offset and use a small jitter; the renderer picks a colour by type. Entity combat text paths are untouched.


**Force presentation (FINAL structure):** still ONE `BLOCK_DAMAGE` semantic type. `CombatTextPayload` gained a trailing `style` byte (a presentation band, default 0; the old 11-argument constructor keeps every existing sender — entity `DAMAGE`, `HEAL`, `IMMUNE`, `RESIST`, `VULNERABLE`, `CONDITION` — on style 0, verified by a round-trip test). `MiningTuning.presentationBand(force, overloaded)`: 0 default (normal mining), 1 low force (< 0.35), 2 mid (< 0.65), 3 high, 4 danger (red zone ≥ 0.8 or the tool load exceeds its tolerance). The renderer maps a band to a colour for `BLOCK_DAMAGE` only; the band comes from `MiningImpact.presentationBand` and is never read by gameplay math, so future special structural hits can reuse it.
## 9. Power Mining (FINAL V1 mechanics; HUD art DEFERRED)

* Input: LMB pressed **while** the Radial Modifier (`ModKeybinds.RADIAL_MODIFIER`, Left Alt by default) is held and a block is targeted. Alt alone does nothing; Alt+Z/X/radials are untouched (live-tested). Normal LMB mining never needs it. Releasing Alt before LMB cancels.
* **Server authority (unchanged, re-verified):** the client sends only `POWER_START`, `POWER_RELEASE`, `POWER_CANCEL` — no force exists on the wire (asserted by reflection on the payload record). The server derives `force = meterValue(heldTicks)` from its own clock, ignores a release without a server-seen start (or after a cancel, or after `MAX_POWER_HOLD_TICKS`), and force is always within [0,1]. Efficiency/Haste/Fatigue never change the meter speed.
* **Power damage (FINAL, replaces the double-dipping formula):** `damage = max(0.1, base + STRmod) × (1 + clamp(force, 0, 1))`, so the multiplier is bounded **1.0 … 2.0** (0 → ×1, 0.25 → ×1.25, 0.5 → ×1.5, 0.75 → ×1.75, 1 → ×2). STR is already inside `base + STRmod` and is not applied again.
* **Force load / tool stress (separate from damage):** `forceLoad = 1 + force × STR/10` (STR 10 → up to 2, STR 100 → up to 11). It depends only on force, STR and the tool — never on damage, Efficiency, Haste or Fatigue.
* **Force Tolerance (material via the tool's repair rule; PROVISIONAL):** gold 2.0 · wood 2.5 · stone 3.5 · iron 6.0 · diamond 9.0 · netherite 16.0 (fallback by tier for unknown tools). Gold is deliberately the softest transmitter, not ranked by durability or speed.
* **Stress formula (PROVISIONAL constants):** `extraWear = ceil( maxDurability × [ 0.0625 × min(load/tolerance, 1)² + 0.2 × max(0, load − tolerance) ] )`; load 1 costs nothing. Applied on top of the base per-impact wear (§7) on DAMAGED/BROKEN outcomes only.

| Pickaxe (durability) | tolerance | STR 10 red-zone extra wear | STR 100 normal | STR 100 red zone (load 11) |
|---|---|---|---|---|
| Wooden (59) | 2.5 | 3 | 0 | 104 → destroyed |
| Golden (32) | 2.0 | 2 | 0 | 60 → destroyed |
| Stone (131) | 3.5 | 3 | 0 | 205 → destroyed |
| Iron (250) | 6.0 | 2 | 0 | 266 → destroyed |
| Diamond (1561) | 9.0 | 5 | 0 | 722 (46 %) → severely worn, survives |
| Netherite (2031) | 16.0 | 2 | 0 | **60** → noticeable, survives |

The previous table (netherite ≈ 10 wear, live-observed "too low") is superseded. Netherite is still overloadable: STR 300 in the red zone destroys more than half of it (tested).
* **Presentation status:** a dedicated first-person Power Mining animation exists for tools and for bare hands (§17). Still deferred: the **final circular/radial crosshair HUD art** — the current meter is a functional placeholder in `PowerMiningMeterHud`.
* **Force bands (FINAL structure / PROVISIONAL boundaries), one mapping:** `MiningTuning.presentationBand(force, overloaded)` is the single force → band function: 0 default (no force), 1 low `< 0.35`, 2 mid `< 0.65` (both "safe"), **3 ORANGE `0.65 … < 0.8`**, **4 RED `≥ 0.8` (`RED_ZONE`) or an overloaded tool**. It drives the floating-text colour, the bare-hand body strain and the future circular HUD. Gameplay damage and the band colour are independent.
* **Bare-hand Power body strain (PROVISIONAL V1 test values, NOT final balance):** a bare-hand Power swing has no tool to stress. On a *successful damaging contact* (DAMAGED or BROKEN) the player takes flat self-damage by band through the normal server damage path (`hurtServer`, generic source; Totality's existing damage interception applies): safe bands **0**, ORANGE **2 HP** (display 10), RED **6 HP** (display 30) (`MiningTuning.BODY_STRAIN_ORANGE/RED`). A miss, out-of-reach, lost target, DENIED, INEFFECTIVE or INVALID never hurts. Tool Power hits never strain the body (the tool takes Force Stress instead). The body is deliberately **not** modelled as a Force Tolerance; future modifiers (Physiology, CON, Permanent Buffs, gloves/gauntlets, techniques, resistances, supernatural bodies) are DEFERRED.
* Stamina cost: DEFERRED.

## 10. Existing systems

| System | Status |
|---|---|
| Mining XP (`PlayerBlockBreakEvents.AFTER`) | Works through the terminal `destroyBlock` (verified by source). Only credits the final break — no partial XP. |
| `VeinminerAbility` | Still compatible as described in §7 (BEFORE handler, treated as BROKEN). Extra vein blocks are still removed directly. Rewrite is DEFERRED until the corrected core is live-retested; the decided future design is recorded below. |
| `MinecraftAttackMixin` (Power Attack) / dual-wield | Disjoint: it only cancels for entity targets, mine only for block targets. The server still triggers the ordinary main-hand `swing()` (third person / other players), while first person is fully owned by `MiningHandAnimation`, so no second animation controller exists; `DualWieldTracker`/offhand paths are not involved. |
| `BreakEffect`, `GroundSlamImpact`, `OrbitProjectileEntity`, `HeatVision`, `SmeltEffect`, harvest handlers | Unchanged; they remove blocks directly. A block removed that way is pruned by the identity/air checks (crack cleared within one sweep). DEFERRED: migrate spells/Ground Slam to `applyImpact`; `BreakEffect`'s ad-hoc numeric harvest level is the obvious future producer of `MiningImpact.tier`. |
| `TotalityFakePlayer` | Not touched; used by `MiningVerification`. |
| `StatAttributeApplier` | Unchanged (STR→mining is read from `PlayerStats` in `PlayerMiningPower`). |
| Creative / Spectator / Adventure | Client and server both gate on Survival. Creative and Adventure keep vanilla behaviour (Adventure's `can_break` rules therefore keep working); Spectator does not mine. Adventure policy: OPEN. |


### Decided future Veinminer design (NOT implemented)

Veinminer must stop instantly deleting the whole vein after one break. Future behaviour: discover the eligible connected vein; sum the **total remaining Block Integrity** of the selected blocks; the required work is `VeinminerRequiredWork = TotalRemainingVeinIntegrity × 0.50` (≈ 2× mining efficiency across the vein; e.g. 800 remaining → 400 work). It must be delivered by repeated Block Breaking impacts, preserving each block's own identity/integrity, break blocks individually through the normal authoritative destruction path (loot, Fortune, Silk Touch, XP, protection), let Power Mining contribute, stay meaningfully faster than mining every block manually, and never bypass the Block Breaking API. It will not be built until the corrected core has been live-retested.
## 11. Provisional tuning (all in `MiningTuning`)

Structure is final for V1; every number below is a rebalance candidate. `DURABILITY_PER_HARDNESS`; `TOOL_STRUCTURAL_PER_SPEED` (6); `BARE_HAND_BASE_DAMAGE` (1); `MIN_IMPACT_DAMAGE` (0.1); `bareHandTier` thresholds; `CONTACT_FRACTION`, `RECOVERY_TICKS`, `MIN_WINDUP_TICKS`, `MIN_RECOVERY_TICKS`, `MIN/MAX_CADENCE_RATIO`, `POWER_WINDUP_MULTIPLIER`; `METER_PERIOD_TICKS`, `RED_ZONE`, `MAX_POWER_HOLD_TICKS`; `forceLoad`; `TOLERANCE_*` per material; `STRAIN_FRACTION` (0.0625), `OVERLOAD_FRACTION_PER_LOAD` (0.2); presentation band thresholds; `RECOVERY_DELAY_TICKS`, `RECOVERY_FRACTION_PER_TICK`, `SWEEP_INTERVAL_TICKS` (40), `DENIED_BREAK_INTEGRITY`.

## 12. Deferred

* Non-player producers (mobs, companions, machines, spells, Ground Slam, explosions) — only the entry point exists.
* **Final circular, sprite-based crosshair Power Mining HUD** (the user will supply the reference/assets; it is not an Ability; likely a dedicated mining HUD renderer). `PowerMiningMeterHud` is a placeholder.
* **Third-person mining/power animation** (first person only for now; other players still see the vanilla swing), tool-specific animation refinement beyond the pick/axe/shovel/hand classes, and further feel polish after live testing.
* **Mining source taxonomy** (literal bare hands, pick, axe, shovel, hoe, shears, improvised held item, natural weapon/physiology, magic/tool-like source...). Currently any item without a `TOOL` component (a stick) falls through to `BARE_HANDS` behaviour. That is NOT the final design; it is deliberately not redesigned yet.
* **Veinminer 50 % aggregate-work rewrite** (§10).
* First-swing client prediction (the first swing animation arrives one round-trip late).
* Data-driven durability/tolerance authoring (registries exist, empty); custom fracture/rubble; Stamina cost; XP attribution/split among contributors; Netherite tier distinct from diamond.
* Food 0–100 integration: branch/base work, **not part of this pass** (§16).

* **Future animation skins (design only):** cosmetic skins for the SAME gameplay action, e.g. a bare-hand Power Mining "Popeye-like / exaggerated wind-up" (one arm winds far back in a large circular motion, visible shoulder/body loading, explosive single strike, exaggerated follow-through, recovery). It stays **one punch and one Mining Impact**. A skin must never add damage, change Mining Tier, Mining Speed or Power force, move the authoritative contact earlier, or grant any gameplay advantage — presentation only. It may later be a cosmetic/Gacha reward; no Gacha system or dependency is implemented.
* **Impact cost is NOT universally item durability (architectural rule, design only):** the current pickaxe/tool behaviour — exactly 1 item durability per successful impact — is a **V1 tool policy**, not a permanent assumption for every mining source. A drill may cost **energy** per impact and **zero** ordinary item durability; a powered tool may use battery/fuel/heat; a magical tool may use Mana or another resource; bare hands normally cost no durability (their Power overload is body strain / health damage today). Future mining-source logic should expose an explicit **Impact Cost Policy** instead of Block Breaking universally calling `hurtAndBreak()`. Conceptually a mining source will own: Mining Damage, Mining Speed, Mining Tier, Force Tolerance, **Impact Cost Policy** (`ITEM_DURABILITY`, `ENERGY`, `FUEL`, `RESOURCE`, `BODY_STRAIN`, `NONE`, or an authored/custom policy), Animation Profile and effective material/block rules. **Not implemented**; `MiningTuning.baseWear` and `PlayerMiningManager.strike` are the V1 tool policy.
* **Future authored Mining Stats (design only, not implemented):** mining sources/tools will eventually carry real authored stats instead of deriving identity from vanilla destroy-speed behaviour: **Mining Damage, Mining Speed, Mining Tier, Force Tolerance** — for pickaxes, axes, shovels, hoes, shears where relevant, drills, hammers, magical tools, modular/Tinkers-like tools, future two-handed pickaxes, special sources and bare hands/physiology. Numbers and balance are deferred until interaction/animation work is stable.
* **Future universal tooltip (design only):** NORMAL view shows *effective* primary values (e.g. `Mining Damage: 150`, `Mining Speed: 2.6/s`, `Mining Tier: 4`). Holding **Shift** reveals provenance, using the **real source name** for every row, never a generic "Enchantment": e.g. `Mining Damage 150 = Base Tool 100 + STR +20 + Sundering III +30`; `Mining Speed 2.6/s = Base Tool 2.0/s + Efficiency V +0.5/s + Haste +0.1/s`; `Mining Tier 4 = Base Tool 3 + Reinforced Head +1`; `Force Tolerance 12 = Base Material 9 + Reinforcement +3`. Force Tolerance is an ADVANCED stat shown only under Shift.
* **Future modular tools (design only):** effective stats are built from parts — tool head, handle, binding, material, reinforcement, enchantments, player STR, buffs, permanent modifiers. Conceptually `Base Value + named contributions = Effective Value`, and the contribution system will likely need operations **ADD, MULTIPLY, SET, MINIMUM, MAXIMUM** (Mining Tier in particular is not simple addition). No framework is built yet.
* **Future block structural tooltip (design only):** blocks should universally expose Block Durability and Required Mining Tier (e.g. Obsidian: `Block Durability: 3000`, `Required Mining Tier: 4`) inside the universal tooltip framework. No values are mass-authored; the fallback durability system stays.
* **Future two-handed mining tools (design only):** a supported archetype with higher Mining Damage, different Mining Speed, higher Force Tolerance, a both-hands requirement, a heavier animation profile and possibly a different Power Mining presentation. Their animation must still respect effective Mining Speed. Not implemented.
* **Architectural invariant for the future Mining Speed stat:** `Effective Mining Speed = tool base + modifiers + Efficiency + Haste − Mining Fatigue + other contributions`; it determines NORMAL cadence and the animation follows the resulting cadence (normalised proportions, currently ~40% draw-back / 60% stroke within the pre-contact time, contact at the end of it, then recovery — whatever feels good is preserved). A slow heavy tool must LOOK slow and heavy, a fast one strike visibly faster. **Power Mining charge/release stays its own deliberate mechanic:** Mining Speed must never accelerate the Power charge or raise Power force.
## 13. Open questions

1. Recovery policy (delay, rate, does it need a player nearby, per material?).
2. Adventure mode mining policy (currently vanilla on both sides; live-tested unbroken).
3. Should cadence additionally use Attack Speed/DEX or a dedicated mining-speed attribute? (Currently only the vanilla break-speed stack.)
4. Should ineffective hits cost the actor anything (stamina, tool wear)? (Currently free.)
5. Final Mining Power curve (extreme STR), Force Tolerance balance, and the final Physiology/techniques/Permanent-Buff model behind bare-hand Tier.
6. Instant-break (hardness 0) blocks stay vanilla — confirm they should not become impacts.
7. Shared damage attribution for Mining XP / loot rights (multiplayer XP attribution).
8. Drops/Fortune/Silk Touch for bare hands beyond eligibility (currently none: no fake enchantments).

## 14. Known limitations

* Sweeps only refresh cracks for loaded chunks; a crack for a damaged block in an unloaded chunk reappears on the next ≤40-tick sweep after it loads. Join and respawn are synced immediately.
* Entity obstruction uses bounding-box clip; interaction range is `blockInteractionRange()` at the contact frame with the server's copy of rotation — no lag compensation.
* `CodecSavedData` passes a `null` `DataFixTypes` (works on the current Fabric API, proven by the round-trip test; a latent risk for future Minecraft/Fabric updates).
* The server-derived force can differ slightly from the client-drawn meter by network jitter.
* A tool source with a per-block wear other than 1 (e.g. a sword's 2) pays that amount per impact, consistent with its vanilla terminal break.
* Headless tests cannot observe dropped item entities in a chunk with no player nearby; drop eligibility is verified through the decisive vanilla predicate (see §7).

* Bare-hand self-damage goes through the generic vanilla damage path and Totality's existing damage interception, so resistances/effects may change the final HP lost; the test seam verifies the exact amount handed to that path (test fake players are invulnerable, so their health cannot be asserted).
* The bare-hand strike pivots about a fixed shoulder point (0.64, −0.6, −0.72) in camera space; extreme FOV/hand-size settings were not checked.
## 15. Verification and live-test record

### MANUAL LIVE-CLIENT RESULTS (developer-reported)

**First playtest** — Block Breaking basically worked; persistence appeared broken (diagnosed in §5 as crack presentation, not data loss); Power Mining at STR 100 red zone destroyed a Stone **and** a Netherite pickaxe (too flat); STR 100 bare hands too weak (old formula); Food HUD `20/20` (§16).

**Second playtest** (of the persistence fix and V1 behaviour):

| Result | Item |
|---|---|
| PASS | Normal mining feels like discrete repeated swings |
| PASS | Looking away before contact → the swing misses, no block damage |
| PASS | Moving out of reach before contact → the swing misses, no block damage |
| PASS | Retargeting during a swing (Stone → Dirt before contact): Dirt takes the impact with the correct tool/block context, Stone does not |
| PASS | Partial damage remains while the world is loaded; lazy regeneration works while loaded |
| PASS | **Persistence after Save & Quit / reload** |
| PASS | Wood, Glass, Torch and Grass Block behave correctly (block variety) |
| PASS | Floating structural-damage text at the actual impact location |
| PASS | Power Mining meter position changes damage |
| PASS | Copper Pickaxe vs Diamond Ore: INEFFECTIVE shown, no integrity damage |
| PASS | Fortune and Silk Touch on the final break |
| PASS | Creative and Adventure modes still work |

**Newly discovered failures (old formulas) and their corrections — all corrected in code (the developer has since accepted V1 after live testing; the animations were explicitly accepted, the individual numeric checks below were not each reported):**

| Observation | Cause | Correction |
|---|---|---|
| Bare-hand damage wrong (STR 12/14 showed 1) | quadratic formula on the modifier | bare hand = `max(0.1, 1 + STRmod)` |
| Tool STR used a raw-STR percentage (STR 14 → ~58, 16 → ~60) | `1 + 0.02·(STR−10)` multiplier | additive STR modifier; +1 exactly between STR 14 and 16 |
| Bare-hand Tier came from STR | design change | DEX-derived Tier (same curve) |
| Sufficient-Tier bare hands destroyed Stone with no drop | vanilla treats the empty hand as the wrong tool | scoped `HarvestGrant` |
| Tool durability dropped only on the final break | vanilla continuous-mining accounting | 1 wear per successful impact, no terminal double-charge |
| Efficiency/Haste/Fatigue changed structural damage enormously (Power Netherite at STR 100: ~1.6k plain, 5.9k Eff V, 2.5k Haste II, 9.4k both, 453 Fatigue I) | `Player.getDestroySpeed` was the damage base | intrinsic `TOOL`-rule speed for damage; the vanilla stack feeds cadence only |
| Power damage absurd (×11 at STR 100) | `1 + force·STR/10` on top of STR | `1 + force`, max ×2 |
| Netherite overload wear ~11 | tolerance/stress formula too flat | strain + overload formula: ~60 at STR 100 red zone |
| Axe/Shovel could be classified as bare hands | `ofTool() > 0` used as the source test | `TOOL` component decides the source |
| Client/server disagreement on `PIERCING_WEAPON` | client-only exception | shared `MiningOwnership` |

### VERIFIED — automated (this pass)

* `bash gradlew build --offline`: **BUILD SUCCESSFUL**. No JUnit source set exists; the convention is dev-environment `*Verification` self-tests.
* `MiningVerification` on a dedicated dev server (throwaway run directory outside the repo, a copy of the live-playtest world): **`All 166 self-test checks passed.`** (30 → 66 → 111 → 136 → 162 → 166). Earlier suites are all retained (persistence round trip, corrected invariants, tolerance matrix, wear, ownership, crack sync, text payload, mid-swing swap, animation timeline).
* **New this pass (162 → 166):** normal bare-hand punch shape (small backward chamber z > 0.1 / pitch < 20°, then the fist drives forward to z < −0.45 with pitch > −30° — i.e. not a downward hammer-fist); bounded, finite follow-through and neutral at the end; bare-hand **Power poses unchanged** (charge pitch 72 / z 0.55, heavy strike pitch −78 / z −0.68 = −0.70 plus the existing 0.02 contact kick) and clearly distinct from the normal punch; the pickaxe stroke pose unchanged (−72° at contact). The mode entry, one contact per cycle, cadence-driven duration, cancel-recovery, mirroring and source-swap checks from the previous pass still apply to the new punch.
* **New in the previous pass (136 → 162):** terminal wear for `damagePerBlock` = 0, 1 and 2 (partial impacts 1 each, terminal exactly 1, total 3, component restored, no leak); **exact-position `HarvestGrant`** (intended player+position answered exactly once; same player at another position, another player, another block type, a second query and everything after close refused; through real `destroyBlock`: a same-type block elsewhere is NOT covered and the granted break is; `Player.hasCorrectToolForDrops` never altered; exception cleanup; nesting without cross-answering); **source identity** (durability change = same source; different enchantments/components/item/empty hand = different; server and client share the rule); **bare-hand animation** (enters NORMAL as HAND, duration follows cadence, contact bounded and exactly one per swing, slower/faster cycle ordering, chamber → forward/down strike, mirrored components finite, Power charge holds a much larger chamber, release → one heavy strike with one contact → recovery → neutral, cancel eases without snap and a new swing can start); **force bands** (safe < 0.65, ORANGE from 0.65, RED from 0.8) and **body strain** (safe 0, orange 2, red 6 applied once through the server path; miss/lost target, INEFFECTIVE and tool Power hits cause none).
* Server start-up with all mixins applied (`ServerPlayerGameModeMixin` incl. the new `destroyBlock` `@WrapOperation`): `Done`, no mixin error. **Dev client start-up** (throwaway run directory, a copy of the playtest world, `--quickPlaySingleplayer`): the client started, applied the client mixins (`MiningHandRenderMixin` with `@ModifyArg` and `@WrapOperation` on `renderItem` and `renderPlayerArm`) without a mixin error, **joined the world** and rendered in-world for about 80 seconds until the timeout, with no crash or exception. Nothing drove keyboard/mouse input, so the animation itself was never triggered: only the inactive pass-through path of the wraps ran with real frames. **Bare-hand animation visuals, Power charge/strike, mirroring and everything in the live checklist remain Stefan's test.**
* Pre-existing, unrelated: `PowerAttackVerification` (1) and `OffhandAttackVerification` (3) fail identically before this work (visible in the developer's own live log).

### CODE INSPECTION (not executed)

Fabric `PlayerBlockBreakEvents` injection points (§7); 26.2 `Player.getDestroySpeed`, `Item.getDestroySpeed`, `Item.mineBlock`, `ServerPlayerGameMode.destroyBlock`, `Tool` rules and `PIERCING_WEAPON` client handling were read from the 26.2 sources.

### LIVE RESULT — first-person tool animation (developer-reported)

The current pickaxe/tool mining animation **feels good** in live gameplay and was deliberately preserved. The bare-hand **Alt Power** animation also **feels good** (live) and is unchanged. The first normal bare-hand animation (hammer-fist) felt awkward and was replaced by a chambered forward punch (§17.6), which still needs its live check.

### STILL REQUIRES LIVE-CLIENT RETEST (developer) — updated

**Core corrections**
1. Bare hand STR 10/12/14/16 → 1/2/3/4 damage.
2. DEX tier gates (DEX 10 vs Stone INEFFECTIVE; DEX 12 damages Stone).
3. A qualified bare-hand Stone break drops Cobblestone (now via the exact-position grant).
4. Netherite Pickaxe, same block: STR 14 vs STR 16 differ by exactly 1.
5. Efficiency changes speed, not damage.
6. Haste changes speed, not damage.
7. Mining Fatigue changes speed, not damage.
8. N successful hits cost exactly N durability (terminal hit included, also for `damagePerBlock` 0/2 tools if you can make one).
9. STR 100 red-zone material stress.
10. Axe on Wood acts as a tool.
11. Shovel on Dirt acts as a tool.
12. Spear/piercing weapon stays vanilla-owned.
13. Force-band damage colours.
14. Persisted cracks appear immediately on join.

**Tool animation (already live-approved; regression only)**
15. Pickaxe animation still feels the same/good; axe and shovel not regressed.
16. Efficiency V / Haste / Mining Fatigue still change the animation speed.
17. Tool swap mid-swing cancels that impact cleanly **and** the animation recovers to neutral, including swapping between two tools of the SAME item type with different enchantments; retargeting with the same tool still works.
18. Tool Power Mining (charge / release / heavy strike / recovery / cancel) unchanged.

**Bare hands (new)**
19. Normal bare-hand mining shows a dedicated one-arm block-breaking strike (chamber, forward/down strike, recovery), not the vanilla punch.
20. One visual strike = one damage event; only the main/dominant arm moves; a left-handed player is mirrored correctly (if testable).
21. Bare-hand cadence visually matches mining speed (Efficiency/Haste/Fatigue change the punch cycle).

**Bare-hand Power Mining (new)**
22. Alt+LMB enters a large one-arm wind-up and the charged pose stays stable while held.
23. Release produces one heavy punch; visual contact aligns with the structural hit (expect it to lead the damage number by about the round trip); recovery is clean.
24. Cancelling (release Alt first, open a screen, swap item) returns cleanly to neutral.
25. A miss causes no self-damage; an INEFFECTIVE hit causes no self-damage.
26. A successful ORANGE-band hit costs the provisional 2 HP (10 display); a successful RED-band hit costs the provisional 6 HP (30 display).

**Regression**
27. Entity attacks and Power Attack unaffected.
28. Off hand / dual-wield rendering unaffected.
29. Shields, eating/drinking, bows/crossbows, maps and spears unaffected.

**Final small-pass checks (normal bare-hand punch)**
30. Normal bare-hand mining looks like a forward punch, not a hammer-fist.
31. A small wind-up/chamber is visible; the fist then drives forward.
32. The punch's contact matches the structural-damage contact; repeated punches flow cleanly.
33. Faster cadence (Efficiency/Haste) visibly speeds the punch; Mining Fatigue visibly slows it.
34. One punch produces exactly one impact.
35. No strange camera/hand clipping; left-handed mirroring if practical.
36. Regression: pickaxe/axe/shovel animations, the bare-hand Alt Power animation, orange/red strain, tool Power animation, combat/Power Attack/off hand/item-use rendering are all unchanged.

**Still outstanding from before:** two real players damaging the same block; Veinminer compatibility/rewrite; protection/claim denial; any earlier untested entries.

## 16. Food `20/20` HUD observation (DIAGNOSED — no code changed)

**Conclusion: a branch-base artifact, not a Block Breaking bug.** `feature/block-breaking-api` was created from `master` @ `bc16cc3`. The Food 0–100 migration (`5ea2bc6 feat(food): add authoritative food system and totality food items`, reachable from `feature/food-system` and `feature/soul-gems`) is **not** an ancestor of this branch. On this branch `TotalityHudRenderer` still reads vanilla `getFoodData().getFoodLevel()` (range 0–20) and divides by a literal `20` (`TotalityHudRenderer.java` lines ~109/114/164), which renders `20/20`. On `feature/soul-gems` the same HUD resolves the generic `totality:food` resource (0–100, vanilla ×5 only as a pre-sync fallback). There is no independent HUD bug: the display would be `100/100` once the completed Food work is present.

**Dependency chain (why it is not cherry-picked):** the HUD change sits on top of the Generic Player Resource API (~22 commits touching `api/rpg/resources`), notably `0cd2b66 Add health and food resource adapters`, `69717c6 Migrate client resource presentation consumers`, `07d5200 Polish player HUD and chat layout`, then `5ea2bc6`. Safest future integration: merge/rebase this branch after (or together with) the resource/food line, not the reverse. Food code was deliberately left untouched here.

## 17. First-person mining animation (Totality-native) and reference audit

### 17.1 Reference audit (read-only; nothing copied; nothing added as a dependency)

Local files under `Inspiration Mods/` (git-ignored, never modified, never bundled): `bettercombat-fabric-3.2.2+26.3.jar`, `HMI 5.0L3 1.21.6+.jar`, `Spear-tridend-animation-main.zip`. The two JARs were only listed/`javap`-inspected in a scratch directory outside the repository; no decompiled output exists in the project.

| | Hold My Item (HMI) | Spear Trident Pose | Better Combat |
|---|---|---|---|
| Version / target found locally | `lua-test-hmi` "HMI 5.0", built for 1.21.6+ (Mojang intermediary names, old `VertexConsumerProvider` renderer — **not** 26.2). License file: CC0. | Source for 1.21.11 (Yarn) and 26.1, 26.1.1, 26.1.2, **26.2** (Mojang names). No Fabric API needed. | 3.2.2 for Minecraft **≥ 26.3** (not 26.2), Fabric, **All Rights Reserved**; depends on the PlayerAnimator library (`player_animation_library`) and Cloth Config. |
| Core idea | Replaces the first-person item pose with data-driven Lua scripts that receive swing progress/hand/item and apply matrix transforms over vanilla; ships an easing library (`cubic`, `inOutCubic`, `inOutSine`, `outBack`, `elastic`, ...). | One tiny mixin on `ItemInHandRenderer` that swaps the `ItemUseAnimation` a spear charges with (spear → trident pose), leaving the model and gameplay untouched. | Attacks have an *upswing* (`upswing` fraction of the cooldown) before the hit; the hit fires from `attackFromUpswingIfNeeded` only if the same stack is still held (`upswingStack`), `cancelUpswing` exists; a `TransmissionSpeedModifier` with "gears" time-warps the animation to fit the upswing/cooldown; animations play through the PlayerAnimator stack. |
| Item / tool detection | Tags/Lua item tables; per-item `transform`/`swingSpeed` state stored on the `ItemStack` (mixin accessor) | `ItemTags.SPEARS` | weapon attributes data per item |
| 26.2 hook facts (from the Spear source + 26.2 sources) | — | The 26.2 renderer method is **`ItemInHandRenderer.submitArmWithItem(AbstractClientPlayer, float frameInterp, float xRot, InteractionHand, float attack, ItemStack, float inverseArmHeight, PoseStack, SubmitNodeCollector, int)`** (renamed from `renderArmWithItem` in 26.1). Main hand is ordinal 0, off hand ordinal 1 of the call in `submitHandsWithItems`; hand selection is by the `InteractionHand` argument. | uses PlayerAnimator, not the vanilla first-person transform |
| Adopted **conceptually** | Layer a transform *over* vanilla instead of replacing the renderer; drive it from a swing progress; ease with cubic/sine curves; keep animation state separate from rendering. | Hook `submitArmWithItem` (26.2 name), gate by hand/item, touch nothing else. | Contact is a fixed point inside a pre-contact phase; the source held at the start must equal the source at contact (Totality's mid-swing source snapshot); animation length must follow the real cadence, not the reverse. |
| Explicitly **NOT** adopted | Lua/scripting, resource-pack pose files, per-item state on stacks, its own renderer overhaul, particles/camera hooks. | The use-animation swap trick itself (we do not touch use animations). | PlayerAnimator/third-person animation stack, attack-cooldown-driven cadence (Totality mining cadence is separate from Attack Speed), combo system, hitboxes. |
| Runtime dependency in Totality | **none** | **none** | **none** |

Verified locally in the 26.2 source: `submitArmWithItem` applies `applyItemArmTransform(...)` then, for ordinary items, `swingArm(attack, ...)` (WHACK) before `renderItem(...)`; empty hands go through `renderPlayerArm(..., attack, arm)`.

### 17.2 Architecture (FINAL V1 structure / PROVISIONAL feel)

* **Hook (26.2), summary — full list in §1:** `MiningHandRenderMixin` on `ItemInHandRenderer` — (1) `@ModifyArg` on the *main-hand* `submitArmWithItem` call inside `submitHandsWithItems` (ordinal 0, argument index 4 = swing progress): returns 0 while any mining animation owns the hand (so vanilla's swing does not stack on ours); (2) `@WrapOperation` on `renderItem` inside `submitArmWithItem` (an item held in the main hand; and (3) the same on `renderPlayerArm`, the bare arm): for `MAIN_HAND` and an active animation it does `pushPose()`, applies one extra transform (`translate`, then vanilla's `Y(45) / Z / X / Y(-45)` wrapper convention so pitch happens about the arm diagonal), calls the original, and `popPose()` in `finally`. Left-handed players are mirrored through the arm's `invert`. Off hand, maps, eating, bows, crossbow charging, shields, spears/`PIERCING_WEAPON` and vanilla equip transitions are not touched; nothing applies unless `MiningHandAnimation.isActive()`, which is only true while the Totality controller owns a swing/power presentation (never just because LMB is down).
* **State:** `MiningHandAnimation` (pure Java, unit-checkable) owns modes `NONE / NORMAL / POWER_CHARGE / POWER_STRIKE / POWER_RECOVERY / RETURN`, a normalised timeline and easing (`easeOutCubic`, `easeInCubic`, `smoothstep`). Every mode start blends 2 ticks from the pose currently shown (nothing snaps). The mixin only asks `sample(partialTick)`. All feel constants are in `MiningHandAnimation.Tuning`.
* **Timeline source:** for NORMAL swings the server sends `MiningSwingPayload(windUp, recovery)` at the moment it starts each swing, so the animation runs the server's real schedule including Efficiency/Haste/Fatigue; the client never re-derives cadence. Power timings are deterministic (`windUpTicks(power) = 2 × base`, recovery 7, cadence never applies), so the strike is predicted locally at the moment of release.
* **Style:** broad visual class by item tags only: pickaxe/other tools = overhead diagonal strike, axe = stronger sideways chop, shovel = forward-down scoop, non-tool item or empty hand = `HAND`, a dedicated **one-arm chambered punch** (§17.6). No animation taxonomy project.
* **Third person / other players:** unchanged in this pass (vanilla swing).

### 17.3 Normal mining animation

`READY → draw-back/lift → committed stroke → CONTACT → short recovery → repeat while the server keeps swinging`. With `W` = pre-contact ticks and `R` = recovery ticks from the server: draw-back over the first `0.4·W` (ease-out), stroke over the rest of `W` (ease-in cubic, accelerating into contact), **visual contact at tick `W`** (a 0.02-unit punch decaying over 3 ticks; `Tuning.CONTACT_PUNCH = 0` disables it), recovery over `R` (ease-out). Total cycle = `W + R` = the gameplay cycle: baseline 10 ticks, Efficiency V 3, Haste II 7, Efficiency+Haste 3, Mining Fatigue I 33 — so Efficiency and Haste visibly repeat faster and Fatigue slower, with no effect on damage and no extra impacts (rendering never produces impacts; only the server does).

*Contact alignment / honest limits:* the server contacts at `S + W`. The animation starts when the payload arrives (`S + latency`), so visual contact is at `S + latency + W` — the same delay the floating damage number has (both are server packets sent at contact/swing start), i.e. visual contact and the damage number line up, and both trail the authoritative contact by the one-way latency. For the power strike the client predicts from release, so visual contact leads the damage number by roughly the round trip. Authority never moves to the client; if the server misses or rejects, the animation simply completes.

### 17.4 Power Mining animation

Input and protocol unchanged (Alt + hold LMB; `POWER_START / POWER_RELEASE / POWER_CANCEL`; no force on the wire; force derived by the server). Visuals: **POWER_CHARGE** lifts into a larger backswing pose (more pitch/height than a normal draw-back) over 8 ticks and holds it while the hold lasts (no jitter); **release** starts **POWER_STRIKE**, a heavy stroke with more travel/rotation than a normal stroke over the power pre-contact ticks (6), **contact** at its end (this is when the server applies the impact and the damage number appears), then **POWER_RECOVERY** eases back over the gameplay recovery (7) plus 5 settle ticks. Cancel (Alt released, a screen opens, the item changes, mining ownership ends) → `RETURN`, easing to neutral over 6 ticks, and the client also sends `POWER_CANCEL` if a hold was in progress. Efficiency/Haste/Fatigue do not change the charge, the meter or the strike.

### 17.5 Source identity (FINAL)

The client stores a *copy* of the source that began the presentation and compares it with the held stack every tick through `MiningSourceIdentity.same` — the identical rule the server applies at contact (item + components ignoring durability). A durability change on the same tool is not a swap; a different item, or the same item with different enchantments/components, is, and the animation eases back to neutral (`RETURN`) while the server voids that swing. (Previously the client remembered only the `Item` type and could visually finish a strike the server had cancelled.)

### 17.6 Bare-hand mining animation (FINAL V1 structure / PROVISIONAL feel)

* **Live result:** the first bare-hand version (a downward *hammer-fist*) felt awkward and is **HISTORICAL / replaced**. It is now a **compact chambered forward punch**. The bare-hand Alt Power animation was live-tested and feels fine; it is unchanged.
* **One arm, never a combo:** the main arm only (mirrored for left-handed players through the arm's `invert`), because one visual strike must equal one Mining Impact; alternating punches would imply two impacts. Combat punch combos are a separate, later design.
* **Normal punch phases** (`MiningHandAnimation`, `Style.HAND`, on the server's real schedule): **neutral → small backward chamber** (arm pulled back a little: z +0.20, pitch +10°) over the first **40 %** of the pre-contact time (ease-out) → **the fist drives forward** toward the block (z −0.55, pitch −14°: almost no downward tilt, easing *in* so it accelerates into contact) over the remaining **60 %** → **CONTACT at 100 % of the pre-contact time** (normalised point = 1.0 × `windUp`, the same tick the server contacts) → **small follow-through** (the fist carries 0.05 units further forward over the first 35 % of the recovery, no back-kick) → **recovery** to neutral over the server's recovery ticks → repeats while the server keeps swinging. Tools keep their own poses and the tool contact kick; only the bare-hand normal poses and its recovery changed.
* **Cadence:** durations come from `MiningSwingPayload(windUp, recovery)`; the proportions above are normalised, so the punch stays readable from Efficiency V (3-tick cycle) to Mining Fatigue (33 ticks). The animation creates no impacts.
* **Rendering:** the mixin wraps `renderPlayerArm` and applies the transform about a shoulder-ish pivot (0.64, −0.6, −0.72). Vanilla's own arm punch is suppressed (`attack = 0`) while the animation owns the hand. A held non-tool item (e.g. a stick) uses the same pose through the item wrap.

### 17.7 Bare-hand Power Mining animation (FINAL V1 structure / PROVISIONAL feel)

Same input/protocol (`POWER_START / POWER_RELEASE / POWER_CANCEL`, no force on the wire, force derived by the server). **Charge:** the arm is pulled much farther back and up than a normal chamber (pitch ≈ +72° vs ≈ +34°, with a shoulder twist and roll) over 8 ticks and held. **Release:** one large committed punch (pitch ≈ −78°, much more travel) over the power pre-contact ticks (6); **contact** at its end — exactly one visible impact = one gameplay impact; then a heavier recovery (7 + 5 settle ticks) back to neutral. Cancel → `RETURN`. It is a strong, readable one-arm power punch; it is **not** the future "Popeye / Gacha" skin (§12, design only). Efficiency/Haste/Fatigue never change it.

### 17.8 What is NOT done

No sprite HUD art (the procedural meter is still a placeholder; final direction unchanged: circular, crosshair-centred, custom sprites, segmented force ring, driven by the same force bands); no third-person animation; no dependency; no change to server authority.

## 18. Change summary (latest passes)

| Item | Status |
|---|---|
| Terminal hit = exactly 1 durability for ANY `damagePerBlock` (0, 1, 2, ...), component restored | **FINAL V1 BEHAVIOUR**, automated-tested |
| Harvest grant consulted inside `destroyBlock` with its own `BlockPos` (exact player + position + block type + single use); `PlayerHarvestMixin` removed | **FINAL V1 BEHAVIOUR**, automated-tested |
| Client source identity = server rule (`MiningSourceIdentity`) | **FINAL V1 BEHAVIOUR**, rule automated-tested; visual effect is a live test |
| Live-approved tool animation preserved | done (regression-only live test) |
| Dedicated one-arm bare-hand mining animation on the real cadence (now a chambered forward punch; the hammer-fist is HISTORICAL) | implemented; **feel constants PROVISIONAL**; timeline automated-tested, rendering not verifiable headlessly |
| Bare-hand Power Mining animation (large chamber, heavy strike) | implemented; **PROVISIONAL feel** |
| Bare-hand Power body strain: ORANGE 2 HP, RED 6 HP flat, only on successful contact | **PROVISIONAL V1 test values** (structure FINAL) |
| One force-band mapping (safe < 0.65, ORANGE ≥ 0.65, RED ≥ 0.8) | **FINAL structure / PROVISIONAL boundaries** |
| Animation skins, authored Mining Stats, tooltips, modular tools, block structural tooltip, two-handed tools, Mining Speed → animation invariant | **DEFERRED (documented only, §12)** |
| Mining source taxonomy; Power HUD art; third person | **DEFERRED** |


## 19. Integration status (EXECUTED)

Block Breaking V1 was accepted after the final live visual test and **integrated**: committed (`2193939` build bump, `1946a42` feature), `master` fast-forwarded to `feature/soul-gems` (`3170e2f`), `master` merged into the feature branch (`ab9ef6a`, two additive conflicts in `gradle.properties` and `totality.mixins.json`, resolved keeping both sides), validated (build, 1717 JUnit tests, `MiningVerification` 166/166, all other dev suites except the historical `OffhandAttackVerification` failures, dedicated server and dev client start), and `master` fast-forwarded to the integrated result. Details: `Context/Audit/TOTALITY_BRANCH_INTEGRATION_AUDIT_2026-09-21.md` §9. Nothing was pushed; no branch was deleted.
