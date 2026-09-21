# Totality — Incremental Mining Technical Audit

*Read-only audit. No Totality Java/resources/configs were modified. No code or assets were copied from the inspiration mod. Decompiled output lives only in the session scratchpad, outside the repository.*

Confidence markers used below: **VERIFIED** (read directly in decompiled code or 26.2 sources), **INFERRED** (deduced from code shape/names), **UNKNOWN** (not established).

---

## 1. Executive Summary

**Headline caveat:** the JAR in `Inspiration Mods/` is **Incremental Mining 1.13 for NeoForge / Minecraft 1.21.1**. Totality is Fabric / 26.2 / Java 25. Nothing in it can be reused mechanically, and none of its hook points can be assumed to exist. It is useful only as a *behavioral/architectural* reference. Every technique below was re-checked against the 26.2 sources jar in Totality's Loom cache.

**Core technique.** Incremental Mining does *not* modify vanilla's break-progress math. It:

1. Client-side: cancels vanilla `startDestroyBlock` / `continueDestroyBlock` (so vanilla never sends START/STOP destroy packets), and instead sends its own "I am holding attack, target = X" packet every tick while LMB is down.
2. Server-side: a per-player-tick loop owns a **swing cooldown**. When the cooldown reaches 0 and the player is still holding at a valid target, the server fires one *Mining Impact*: it adds `1 / swingsNeeded` to a per-player damage float, publishes a crack stage through vanilla `ServerLevel.destroyBlockProgress`, and at ≥ 1.0 calls vanilla `ServerPlayerGameMode.destroyBlock(pos)`.
3. `swingsNeeded` is derived from vanilla hardness / tool speed (the "vanilla ticks to break" number divided by the base swing interval), with hand-tuned floors for wrong-tool cases.

**Server-authoritative?** Partially. The impact timing and damage are computed server-side, and the server re-raycasts the target. But: the mod is registered `optional()`, has no server-side mixin, and never blocks vanilla START/STOP destroy packets, so a vanilla or modified client can still mine the vanilla way. It also lets the client-declared target substitute for the server's own raycast (see §8).

**Persistent / shared damage?** No. Damage is one in-memory float per player (single block at a time), cleared the moment the player releases LMB, looks away, respawns, logs out, or the server starts. A shared per-position map exists **only when the third-party MultiMine mod is loaded**, is in-memory only, and is not saved to disk.

**Most useful idea for Totality:** the *server-owned impact loop*: input becomes a compact "holding + target hint" intent, the server owns cadence (cooldown) and damage, re-derives the target itself, and finishes by routing through the vanilla `destroyBlock` so drops/enchantments/durability/stats stay correct. Second: publishing crack stages via `destroyBlockProgress` with a synthetic id so the miner sees their own crack.

**Biggest weakness for our needs:** damage is a *player-scoped* float rather than a *block-scoped* state. There is no BlockIntegrity, no persistence, no multi-source model, no non-player actors, no mining tier beyond vanilla tags, and the "swing" is a server timer, not a physically-connecting swing. It also skips vanilla's START-time protections (§9/§10).

**Hook strategy for 26.2:** the *approach* (cancel client start/continue, run a server tick loop, call vanilla destroy) survives; the *specifics* (NeoForge events, `BreakSpeed` event, `getDigSpeed`, `ItemAbilities`, `DIG_SPEED` names, `serverLevel()` accessor) do not. The client mixins target methods that still exist in 26.2 with compatible shape. However, a cleaner Totality design does not need most of them (§12, §18).

---

## 2. Mod Metadata / License

| Field | Value | Source |
|---|---|---|
| Name | Incremental Mining (`displayName`) | `META-INF/neoforge.mods.toml` — VERIFIED |
| Mod id | `incrementalmining` | VERIFIED |
| Version | 1.13 | VERIFIED |
| Author | jDynamo | VERIFIED |
| Minecraft | `[1.21.1]` (exact) | VERIFIED |
| Loader | NeoForge (`javafml`), requires `neoforge [21.1.228,)` | VERIFIED |
| Java | Class-file major 65 = Java 21; mixin `compatibilityLevel: JAVA_21` | VERIFIED |
| Dependencies | Mandatory: `neoforge`, `minecraft`. Everything else is soft compat (see below) | VERIFIED |
| Entrypoints | `@Mod("incrementalmining")` class `IncrementalMining`; client init `IncrementalMiningClient` (only when `Dist.CLIENT`); NeoForge `@EventBusSubscriber` classes | VERIFIED |
| Mixins | `incrementalmining.mixins.json`, **12 mixins, all in the `client` list**, `required: true`, no server-side mixins | VERIFIED |
| Access wideners / transformers | None present in the JAR | VERIFIED (file listing) |
| JAR | `incrementalmining-1.21.1-1.13.jar`, 263,370 bytes, SHA-256 prefix `7a68f2d204aec696` | VERIFIED |
| Decompiler | IntelliJ Fernflower (`java-decompiler.jar` from local IntelliJ install) | — |

**Soft compat targets** (reflection / `@Pseudo` mixins, not dependencies): Better Combat, Punchy, MultiMine (two backends), TreeChop, Dynamic Trees, Simple Smithing Overhaul, MiningQuakes, Mining and Placing Animations, Sable. Their presence explains ~40% of the code size and is irrelevant to Totality.

### License

`neoforge.mods.toml` declares **`license="All Rights Reserved"`**. There is no `LICENSE` file inside the JAR (only `pack.mcmeta`, mixin config, mods.toml, manifest, lang, and assets). No source repository/licence page was consulted (no network access used).

**Consequence:** All Rights Reserved means no permission to copy, adapt, translate, or redistribute code or assets. What we may do is learn *ideas, behavior, and general architecture* and implement them independently. Ideas and behaviors are not protected; code, the specific structure/naming of the implementation, tuning tables, textures, lang strings, and sounds are.

| Safe to learn from | Must NOT copy |
|---|---|
| The high-level loop: client intent → server cooldown → impact → damage → crack → vanilla destroy | Any class, method body, or control-flow structure line-for-line |
| The fact that `destroyBlockProgress` accepts an arbitrary id and 0–9 stage | Its specific id schemes/constants (2,000,000 base, 1,000,000 + hash) as-is |
| Behavior observations (e.g. progress is fractional per swing) | Its hand-tuned tables (instant-break "buckets", wrong-tool floors 3/8/24, clamps 4/40) |
| Failure modes we should avoid | Config keys/names, lang strings, sound tables, GUI screens |
| Which vanilla methods are worth intercepting | Any texture/asset in the JAR |

This document deliberately contains only paraphrase and short pseudocode.

---

## 3. Architecture Overview

```
CLIENT                                             SERVER
──────                                             ──────
ClientTickEvent.Post (ClientInputHandler)          PlayerTickEvent.Post (MiningServerLogic)
  read keyAttack.isDown + hitResult                  SwingManager: holding? target? cooldown?
  send HoldAttackPacket ───────────────────────────► store holding + target hint (TTL 6 ticks)
                                                     resolve target (server pick, else hint)
MultiPlayerGameMode mixin                            if cooldown<=0:
  cancel startDestroyBlock/continueDestroyBlock        SwingFeedbackPacket ──► client swing anim
  (vanilla never starts destroying)                    broadcast 3rd-person swing
                                                       performMiningImpact:
LocalPlayer.swing / LivingEntity swing-duration          damage += 1/swingsNeeded
  mixins: suppress/replace vanilla swing anim            destroyBlockProgress(crackId,pos,stage)
ParticleEngine mixin: suppress vanilla hit particles     if damage>=1: gameMode.destroyBlock(pos)
ClientSwingListener: plays anim/particles on packet      clear crack + damage; short cooldown
```

Packages: `events` (server loop `MiningServerLogic`, `MiningState`, `MiningRules`, `MiningFeedback`), `mining` (`SwingManager`, `SwingTiming`, `BlockDamageManager`, `SharedBlockDamageManager`, `MiningTargets`, `ExternalMiningSpeed`), `client` (`ClientInputHandler`, `ClientSwingListener`, `ClientState`), `network` (5 payloads), `mixin.client`, `compat`, `config`. All state is static `HashMap`s keyed by player UUID (or dimension → position).

Roughly 6,400 decompiled lines; the mining-relevant core is ≈ 1,500 lines (`MiningServerLogic` 652, `MiningRules` 182, `SwingManager` 150, `SwingTiming` 66, `MiningState` 161, damage managers ~185, `ClientInputHandler` 128).

---

## 4. Input / Swing Handling

**Does LMB still use vanilla input?** Yes, at the *key-state* level: the mod reads `options.keyAttack.isDown()` and `mc.hitResult` on `ClientTickEvent.Post`. It does not add a new keybind. VERIFIED.

**What is intercepted.** `MultiPlayerGameMode.startDestroyBlock` and `continueDestroyBlock` (HEAD, cancellable, returning `true`) when the mod is enabled, the player is not creative/spectator, and the target is not "vanilla-handled" (instant-break blocks, configured foliage/blocks/tools). Result: vanilla's client destroy state machine never runs, so **no `ServerboundPlayerActionPacket` START/STOP_DESTROY_BLOCK is sent** for those blocks. VERIFIED.

**Does holding LMB become repeated discrete actions?** Yes, but the repetition lives on the **server**. The client sends `HoldAttackPacket(holding, targetPos, hitLocation, face, impactOffsetTicks)` every tick while held and once on release. The server's per-player loop decides when an impact happens. VERIFIED.

**Is each swing a distinct mining event?** Each *cooldown expiry* is one event. It is a server timer that emits a swing animation, not a client swing that physically connected. There is no requirement that a client-side arm swing reach a contact frame; instead an optional **impact offset** (client config, 0–20 ticks) delays the *initial* impact after the hold starts, to imitate a wind-up. Subsequent impacts follow the cooldown. VERIFIED. The offset value is client-sent and not upper-clamped server-side.

**Cadence determination** (`SwingTiming`, `MiningRules.getSwingDelay`): base interval config (default 12 ticks) ÷ baked-in 1.2 → ~10 ticks; divided by an Efficiency/Haste multiplier (`1 + level × step`, default step 0.25) unless a config makes those boost damage instead; multiplied up by Mining Fatigue (`1 + (amp+1)×0.3`); then divided by an "external speed ratio" (captured from other mods' break-speed modifiers via the `BreakSpeed` event); clamped 4–40, minimum 2. VERIFIED.

**Attack speed influence?** **No.** `Attributes.ATTACK_SPEED` / attack strength ticker are not read for mining. The only link to combat: an `AttackEntityEvent` hook resets the mining swing cooldown so attacking then mining doesn't double-dip. VERIFIED.

**Tool mining speed influence?** Not on cadence (only Efficiency/Haste do, by config). Tool speed instead changes *damage per swing* (via `swingsNeeded`). VERIFIED.

**Animation ↔ impact sync.** The server sends `SwingFeedbackPacket` to the miner at cooldown expiry *at the same tick it applies damage* (unless impact offset defers damage). The client then plays the swing (`player.swing`, or directly sets `swinging/swingTime` in "DIRECT" mode) and spawns 8× vanilla hit particles. Other players see `player.swing(hand, false)` broadcast. So animation is triggered *by* the impact, not the reverse: the impact is authoritative and the animation is cosmetic feedback with one network hop of latency. The client-side mixins (`LocalPlayer.swing` cancel, `LivingEntity.getCurrentSwingDuration` override) prevent vanilla's own swing (fired by vanilla `continueAttack`) from desyncing the arm. VERIFIED.

**Implication for Totality:** "swing actually connects" is achievable, but this mod does not do it. Totality would need the *animation contact frame* to trigger the impact request (client→server) or, more robustly, server-timed impacts with an animation length derived from the same cadence value (see §18).

---

## 5. Mining Damage Model

| Question | Answer (VERIFIED unless noted) |
|---|---|
| Vanilla progress math retained? | **Partly reused, not used directly.** Uses `hardness × 30 / toolSpeed` (i.e. vanilla's ticks-to-break for a correct tool) as an input, then converts to swings. Vanilla's `getDestroyProgress` itself is not used for progress. |
| Representation | **Per-hit fraction**: damage ∈ [0,1], each impact adds `1 / swingsNeeded`, capped at 1.0. Not "remaining hardness"; not an integer integrity. |
| `swingsNeeded` | `ceil((miningTicks − 1) / baseSwingDelay) + 1`, min 2 (floors configurable), max 35. `miningTicks = ceil(hardness × movementPenalty × 30 / (toolSpeed × damageMultiplier))`. |
| Hardness | `state.getDestroySpeed(level,pos)`; `< 0` = unbreakable; `0` = instant (routed to vanilla break) |
| Tool suitability | `tool.isCorrectToolForDrops(state)`; if not correct, `toolSpeed` is forced to 1, and minimum-hit floors apply by material class (dirt-like 3, wood-like 8, stone/ore 24). Vanilla's ×100 wrong-tool divisor is **not** used; the floors replace it. |
| Mining tier | **No tier concept of its own.** "Correct tier" = vanilla `correctForDrops` (tag-based). A wrong-tier-but-right-tool-type hit yields an anvil "clink" sound and is treated as wrong tool. |
| Movement/environment | Penalty multipliers: airborne 1.2, in water 1.3, swimming 1.8, climbing 1.6, elytra 1.5, underwater without Aqua Affinity 2.0. (Vanilla uses ÷5 airborne, ×0.2 submerged; the mod re-expresses these.) |
| Instant-break | A hard-coded lookup table reproduces vanilla's "does this break instantly for this tool/efficiency/haste" so instant blocks stay vanilla-fast (fragile design, see §16). |
| Client/server | **Server only.** The client holds no progress. |
| Multiple players on one block | Default: **no**, each player has an independent private float (one entry per player). Shared only with MultiMine loaded. |
| Persistence when player stops | **No.** Cleared on release / target change / look-away (`clearActiveMining`). |
| Persistence across reload/unload/logout | **No.** Static in-memory maps; cleared on `ServerStarting`, logout, respawn. |
| Recovery over time | Only in the MultiMine-shared path: after a 40-tick delay, lose one crack stage per 20 ticks (config), else purged after 200 ticks. In the default path, recovery is instant (reset to 0). |

Notes for design: `swingsNeeded` is recomputed at every impact from the *current* tool/state, so switching tools mid-block changes the per-swing increment. Progress is a fraction of the block, not an amount of integrity, which is the opposite of Totality's intended BlockIntegrity model (absolute quantity reduced by power). The math also bakes in a fixed "damage ≈ swings" relationship that would fight with STR/Mining Power scaling unless re-based on an absolute scale.

---

## 6. Block State / Persistence

- **Data structures:** `BlockDamageManager` (player UUID → single `{pos, damage, lastHit}`), `SharedBlockDamageManager` (dimension key → `Map<BlockPos, {damage,lastHit,nextFadeTick}>`), `SwingManager` (UUID → cooldowns/holding/target/TTL), `MiningState` (crack ids/positions, pending impacts, failure counters). All `static HashMap`, no Saved Data, no attachments/components, no NBT. VERIFIED.
- **Block state changes while damaged:** on `BlockEvent.EntityPlaceEvent` and `BlockEvent.BreakEvent` the mod clears that position. If the position becomes air, damage clears next impact/purge. A *different non-air* block replacing the position (e.g. piston, fluid, another mod) is **not** detected by comparing state: damage would carry over to the new block. Not handled: piston movement, explosions removing/replacing the block (no hook; only air check). INFERRED from event list.
- **Chunk unload/reload:** no handling. The shared-path fade/purge loops call `level.getBlockState(pos)` on stored positions each tick; on an unloaded chunk this can trigger a chunk load or stale processing. INFERRED / not runtime tested. The default (per-player) path is only queried for the block the player is currently hitting, so it is not affected.
- **Disconnect/reconnect:** cleared on logout; not restored. **Dimension change:** the per-player crack position is tracked by position only, so a dimension change leaves a stale crack entry until the player targets another block (INFERRED; client also expires unrefreshed cracks after 400 ticks, §7). **Respawn:** cleared.

---

## 7. Crack Rendering

- **Vanilla infrastructure is used.** The mod calls `ServerLevel.destroyBlockProgress(id, pos, stage)` (vanilla method that sends `ClientboundBlockDestructionPacket` to players within 32 blocks whose entity id differs from `id`). Stage = `min(int(damage × 10), 9)`; −1 clears. VERIFIED (mod) and re-VERIFIED against 26.2 `ServerLevel.destroyBlockProgress` (same shape, 1024 distance² check, `player.getId() != id` filter).
- **Synthetic breaker id.** Per-player id from a counter starting at 2,000,000 (not the entity id), which deliberately defeats vanilla's "skip the breaker" filter so the *miner also receives their own crack*, since vanilla's own client-side crack (driven by `MultiPlayerGameMode.destroyProgress`) is no longer running. Shared cracks (MultiMine path) use `1,000,000 + pos.hashCode()`, which can collide with other ids (entity ids, other hashes). VERIFIED (mod); collision risk INFERRED.
- **Multiplayer sync:** free from vanilla — other players in range see the crack from the same packet. New players entering range do **not** receive existing cracks until the next update (vanilla packet is not replayed). INFERRED from 26.2 `ServerLevel.destroyBlockProgress` (no join/track replay).
- **26.2 client behavior:** `ClientLevel.destroyBlockProgress` accepts stage 0–9; ≥10 or <0 removes; entries keyed by `id`; multiple ids per position are stored in a sorted set; entries not refreshed for **> 400 game ticks** are auto-removed (checked every 20 ticks). VERIFIED in 26.2 sources. This matters for persistent damage: the server must re-broadcast periodically or on chunk (re)track.
- **Custom rendering:** none of its own. Two client mixins (`BlockRenderDispatcher.renderBreakingTexture`, `LevelRenderer.renderLevel`) exist for the MiningQuakes compat (hiding the vanilla crack while a compat "quake" animation runs). Bodies were only partially inspected. INFERRED purpose; low importance.
- **Resolution limit:** 10 stages only, fractional damage is quantized. Adequate for V1 crack sync; custom fracture/rubble visuals would need custom rendering later.

---

## 8. Networking / Server Authority

| Payload | Dir | Content | Purpose |
|---|---|---|---|
| `HoldAttackPacket` | C→S | holding, target pos, hit location, face, impact offset | the only input; client *intent* |
| `SwingFeedbackPacket` | S→C | pos, block state id, correct-tool/tier flags, swing delay, anim/particle flags | trigger swing, hit particles, tier "clink" |
| `BlockSoundPacket` | S→C | pos, sound id (string), source, volume, pitch | custom impact sounds to players ≤ 48 blocks |
| `BreakEffectsPacket` | S→C | pos, block id | break particles/sound (level event 2001) |
| `ConfigSyncPacket` | S→C | foliage flag + block/tool override lists | client needs to know "vanilla-handled" targets |

Registered via `registrar.versioned("4").optional()`; a client lacking the mod can join.

**Who is authoritative?** The server computes cadence, damage, crack and the final break. It re-derives the target with `player.pick(blockInteractionRange, …)` using the *server's* copy of the player's rotation, and rejects the hit if an entity obstructs. VERIFIED.

**Weaknesses found (by code reading, not exploited):**
1. **Target hint fallback.** If the server's own pick returns MISS, it falls back to the client-declared target for up to 6 ticks, requiring only distance ≤ `range + 1.25` and no entity in the way. There is **no block line-of-sight check** for the hinted target. A modified client could mine blocks behind walls within ~7 blocks. Cost: server-side latency tolerance was traded for exploitability.
2. **No START-time vanilla checks.** The mod calls `ServerPlayerGameMode.destroyBlock` directly. In 26.2 that method checks only `canDestroyBlock`, GameMaster-block permission, and `blockActionRestricted` (VERIFIED). The vanilla protections live in `handleBlockBreakAction` (START): `isWithinBlockInteractionRange(pos, 1.0)`, max-build-height, `isUnderSpawnProtection`, `level.mayInteract`, `blockActionRestricted`, plus `EnchantmentHelper.onHitBlock` and `BlockState.attack(...)`. The mod's own code contains none of `mayInteract` / spawn-protection / `onHitBlock` / `attack(` (grep-verified). In its 1.21.1 NeoForge environment, NeoForge's `BlockEvent.BreakEvent` covers some protection mods; **Fabric 26.2 has no equivalent for a direct `destroyBlock` call**, so in Totality this becomes a real bypass unless replicated.
3. **Optional/opt-out.** Nothing server-side cancels vanilla `handleBlockBreakAction`; a vanilla or hacked client can mine with vanilla rules at will. Real authority requires the server to *reject/replace* vanilla START/STOP for survival players (Totality can do this with a server mixin).
4. **Trusted client values:** `impactOffsetTicks` unclamped (self-harming only).

**Simultaneous miners:** each player has an independent damage float and independent cooldown, so two players on one block do not interfere and do not combine (default path). With the shared path, damage is a single per-position float that any player's impacts advance; there is **no attribution** of contribution to individual players, and crack ids are position-keyed rather than player-keyed.

---

## 9. Real Block Destruction / Loot Compatibility

The finish is `player.gameMode.destroyBlock(pos)`, the same server method vanilla's STOP_DESTROY/insta-mine path uses (`destroyAndAck` → `destroyBlock`). VERIFIED in the mod and 26.2. Therefore, vanilla behavior is preserved for: drops via `Block.playerDestroy` (loot tables, Fortune, Silk Touch, block-specific overrides), `ItemStack.mineBlock` (tool durability, `Tool.damagePerBlock`, enchantment post-mine effects), `playerWillDestroy` (e.g. creative/beds/double-blocks), stats/advancement triggers fired from those calls, XP-drop handling in `spawnAfterBreak`, and custom modded blocks (they already implement these overrides). Silk Touch/Fortune work "automatically" because the held stack is unchanged. **Fabric's `PlayerBlockBreakEvents`** are expected to fire from within this same method (Fabric mixin into `ServerPlayerGameMode.destroyBlock`); the classes exist in `fabric-events-interaction-v0 5.2.8` — the injection point itself was **not** verified here, so Totality's Veinminer (BEFORE) and MiningSkillEvents (AFTER) dependence on this must be tested.

Failure handling: when `destroyBlock` returns false (protection, cancelled), the mod re-arms damage at 0.9 and counts failures; after 3 it force-calls `level.destroyBlock(pos, true, player)` ("auto vanilla fallback"), which **bypasses** the cancellation that caused the failure. That fallback is a design flaw for protected blocks (§16).

Not preserved / not invoked per impact: `BlockState.attack()` (the "punch" hook: note-block play, dragon egg, etc.), `EnchantmentHelper.onHitBlock` (fires once at vanilla START; enchantments that trigger on hit would never fire), spawn protection / `mayInteract`. The break-speed *event* path is also not used for damage (only sampled for an external multiplier).

---

## 10. Vanilla Edge Cases

| Case | Behavior (VERIFIED by code unless noted) |
|---|---|
| Creative | `shouldUseVanilla`: fully vanilla (client also skips). |
| Adventure | Server treats as vanilla and does nothing, **but the client mixin only exempts creative/spectator**, so client cancels vanilla start/continue while server ignores the hold → **Adventure-mode `can_break` mining appears broken** (INFERRED; not runtime tested). |
| Spectator | vanilla (skipped). |
| Unbreakable (hardness < 0) | Impact plays sounds/feedback but clears progress; never breaks. |
| Protected blocks | See §8/§9: only `blockActionRestricted`/GameMaster check inside `destroyBlock`. Others bypassed. |
| Requires correct tool | Wrong tool → speed 1, minimum-hit floors, block still breaks (no drops via `hasCorrectToolForDrops` in `destroyBlock`). |
| Underwater / airborne | Penalty multipliers on hardness; water-splash sound/particles. |
| Haste / Mining Fatigue / Efficiency | Modify **cadence** (default) or damage; Fatigue lengthens interval. Vanilla's own Haste/Fatigue formulas are not used for damage. |
| Attack speed | Ignored. |
| Instant-break blocks | Handed back to vanilla (client-side "vanilla-handled" table) so they break on click. |
| Very high hardness | Capped at 35 swings. Obsidian-class hardness will not exceed this cap (INFERRED from cap). |
| Block state change while damaged | See §6: only air/placement/break events clear. |
| Piston movement | Not handled (INFERRED). |
| Chunk unload / reload | Not handled (§6). |
| Disconnect / reconnect / respawn / dimension change | Logout & respawn clear; dimension change not explicit. |
| Two players same position | Independent (default) or shared float (MultiMine path). |
| Entities/machines | Player-only: every path takes `ServerPlayer`; no support for non-player actors, fake players, or automation. |
| Entity in the way | Explicitly blocks mining if a pickable entity is between eye and target. |

---

## 11. Mixins / Hooks / Version Sensitivity

**Vanilla-targeting mixins (7, all client-side):**

| Mixin | Target | Purpose |
|---|---|---|
| `MultiPlayerGameModeMixin` | `startDestroyBlock`, `continueDestroyBlock` | Cancel vanilla destroy state machine (**the essential one**) |
| `LocalPlayerMixin` | `swing(InteractionHand)` | Suppress vanilla swing while mining |
| `LivingEntityMixin` | `getCurrentSwingDuration` (private) | Override local swing duration |
| `ParticleEngineMixin` | `addBlockHitEffects` | Suppress vanilla hit particles |
| `ItemInHandRendererMixin` | `tick` | Optional "flat tool raise" |
| `BlockRenderDispatcherMixin` / `LevelRendererMixin` | `renderBreakingTexture` / `renderLevel` | MiningQuakes compat (INFERRED) |

Plus 5 `@Pseudo` mixins into third-party mods (irrelevant). **Server-side: zero mixins.** All server behavior rides NeoForge events: `PlayerTickEvent.Post`, `LevelTickEvent.Post`, `BlockEvent.EntityPlaceEvent`, `BlockEvent.BreakEvent`, login/logout/respawn/`ServerStarting`, `AttackEntityEvent`, `PlayerEvent.BreakSpeed`, and client `ClientTickEvent.Post` / `PlaySoundEvent`.

**Invasiveness:** low on the server, moderate on the client. It layers on top of vanilla (calls vanilla destroy, uses vanilla crack packet) instead of replacing core logic, but it *disables* the vanilla client mining state machine.

**Survivability across MC updates:** the layered structure survives well; the specific hooks are the fragile part. `getCurrentSwingDuration` is private (still private in 26.2), and `renderBreakingTexture`/`renderLevel` signatures are the most rendering-refactor-prone in modern Minecraft (26.x moved rendering to a submit/extract model, and Totality's own mixin list already shows adapted names like `submitHandsWithItems`, `GuiGraphicsExtractor`). NeoForge-only APIs (`ItemAbilities`, `BreakSpeed`, event bus) have no direct Fabric equivalent.

**APIs exposed to other mods:** none. Compat is reflection into others; the mod exposes no public API/events.

---

## 12. Minecraft 26.2 Equivalent Hooks

Checked against the Loom 26.2 merged sources jar and cached Fabric API 0.160.0.

| Technique | What IM does | 26.2 status (VERIFIED in sources) | What changed / notes | Suitable for Totality? |
|---|---|---|---|---|
| Cancel client vanilla mining | Mixin HEAD-cancel `MultiPlayerGameMode.startDestroyBlock/continueDestroyBlock` | Both still exist: `startDestroyBlock(BlockPos, Direction)`, `continueDestroyBlock(BlockPos, Direction)`. Client flow is `Minecraft.startAttack()` → `gameMode.startDestroyBlock`, and `continueAttack()` → `gameMode.continueDestroyBlock`, then `player.swing`. | `startAttack` also handles piercing weapons/spectator; `continueAttack` skips when the item has `PIERCING_WEAPON`. Still returns boolean; cancellation semantics unchanged. | **Yes** (clean, small). Alternative: cancel earlier in `Minecraft.startAttack/continueAttack` (Totality already mixes `Minecraft.startAttack`). |
| Intent packet | Custom payload each tick | Fabric `ClientPlayNetworking` / payload registration already used by Totality (`PowerAttackPayload`, `OffhandAttackPayload`). | — | **Yes**, but design as a request (`start/hold/stop + hit result`), not a per-tick spam (§18). |
| Server mining loop | `PlayerTickEvent.Post` | Fabric: `ServerTickEvents`/`ServerPlayer.tick` mixin or a per-level manager. `ServerPlayerGameMode.tick()` already drives vanilla's `isDestroyingBlock` progress. | Fabric has no `PlayerTickEvent.Post`; use a global tick event with a tracked-miners set, or mix into `ServerPlayerGameMode.tick`. | **Yes.** |
| Break-speed inputs | `getDigSpeed` + NeoForge `BreakSpeed` event | 26.2: `Player.getDestroySpeed(BlockState)` is attribute-driven: `ItemStack.getDestroySpeed` (from `Tool` component rules), `+ MINING_EFFICIENCY`, Haste ×(1+0.2·(amp+1)), Mining Fatigue table (0.3/0.09/0.0027/8.1E-4), `× BLOCK_BREAK_SPEED`, `× SUBMERGED_MINING_SPEED` underwater, `÷5` airborne. | The former enchantment special-casing became attributes; **no event**. Totality can supply Mining Power via attribute modifiers or its own compute. | **Yes**; the attribute route is a natural place for STR/Physiology/Buffs. |
| Tool suitability / tier | `isCorrectToolForDrops` | `Player.hasCorrectToolForDrops(state)` = `!requiresCorrectToolForDrops || stack.isCorrectToolForDrops(state)`; backed by `Tool.Rule(blocks, speed, correctForDrops)` lists. Vanilla wrong-tool divisor 100 vs 30 in `BlockBehaviour.getDestroyProgress`. | Tier is *tag-based rules on the Tool component* (data-driven), not a numeric harvest level. | Reference only: Totality's numeric **MiningTier** must be its own layer (Totality's `BreakEffect` already invents numeric harvest levels ad hoc). |
| Crack packet | `ServerLevel.destroyBlockProgress(id,pos,stage)` | Exists, same semantics; `ClientboundBlockDestructionPacket(int id, BlockPos, byte progress)`; client handling and 400-tick expiry verified. | — | **Yes** for V1. |
| Final destruction | `ServerPlayerGameMode.destroyBlock(pos)` | Exists. `destroyAndAck` wraps it with ack/update packet. Checks: `canDestroyBlock`, GameMaster, `blockActionRestricted`; then `playerWillDestroy`, `removeBlock`, `destroy`, `mineBlock`, `playerDestroy`. | Prediction/sequence acknowledgment (`BlockStatePredictionHandler`) is part of the client flow; a server-only break needs an explicit block update to clients (normal `removeBlock` sync covers it). | **Yes**, as the terminal call, preceded by replicated START-checks. |
| Non-player breaking | (none) | `Level.destroyBlock(pos, dropBlock, entity[, limit])` for entity/explosion-style breaks; Totality's `GroundSlamImpact`, `BreakEffect`, `VeinminerAbility` already do variants of this. | These paths skip the `ServerPlayerGameMode` protections and Fabric player events. | Needed: a single *actor-agnostic* damage entry point (§18). |
| Break events | NeoForge `BlockEvent.BreakEvent` | Fabric `PlayerBlockBreakEvents.BEFORE/AFTER/CANCELED`, `AttackBlockCallback` exist in `fabric-events-interaction-v0 5.2.8`. Totality already uses BEFORE (Veinminer) and AFTER (Mining XP). | Injection site inside `destroyBlock` not verified here. | Verify with a test before relying. |
| Swing animation length | `getCurrentSwingDuration` override | Still private in `LivingEntity`; now `handStack.getSwingAnimation().duration()` **item-data driven**, with Haste reducing / Fatigue increasing by `(amp+1)` (`×2` for fatigue). | The swing duration is now a per-item component (`SWING_ANIMATION`), and Totality already reads it (`DualWieldTracker`). | **Yes**, and it's a better source of truth for animation ↔ cadence coupling than a mixin override. |
| Attack cadence | (ignored) | `Player.getCurrentItemAttackStrengthDelay() = 1/ATTACK_SPEED × 20`; `getAttackStrengthScale`; item component `MINIMUM_ATTACK_CHARGE`. | Available if we decide attack speed drives mining cadence. | Design decision (§19). |

---

## 13. Existing Totality Mining / Block-Breaking Code

Nothing changed. What exists today, with migration/integration impact:

| Location | What it does | Future impact |
|---|---|---|
| `mixin/client/MinecraftAttackMixin` (`startAttack` HEAD, `tick` HEAD) | Power-Attack hold/charge. Cancels `startAttack` **only** when a weapon is held **and** the crosshair entity is a valid Power Attack target; explicitly designed not to interfere with block mining ("correction pass, Part C"). | **Integration point.** A mining-input hook in `startAttack`/`continueAttack` must coexist with it; the block-vs-entity gate already exists and is a good pattern. `tick` HEAD already runs per-tick client logic. |
| `mixin/client/MultiPlayerGameModeMixin` | Only `releaseUsingItem` cancel (blocking). Does **not** touch `startDestroyBlock`/`continueDestroyBlock`. | No conflict. A new destroy-cancel injection can live here or in a new mixin. |
| `mixin/client/ItemInHandRendererMixin`, `HumanoidModelMixin`, `mixin/client/LivingEntityMixin` (`getScale`), `client/combat/DualWieldTracker` | Offhand/dual-wield swing visuals; `HumanoidModelMixin` injects after `setupAttackAnimation`. | Mining swing animation will interact with these (a second swing consumer). Not conflicting today. |
| `api/ability/impl/VeinminerAbility` | Registers `PlayerBlockBreakEvents.BEFORE`; when Veinminer is held, manually collects drops with `Block.getDrops`, `removeBlock`, awards Mining XP, hurts the tool, stamina cost, BFS queue, one block/tick; returns `false` to cancel vanilla. | **Needs migration:** it bypasses `destroyBlock`'s normal `playerDestroy`/stats path and would apply *instant* AoE-like breaks with no BlockIntegrity. Will need to route each extra block through the new damage API (or an explicit "force break" path). |
| `api/rpg/skills/mining/MiningSkillEvents` | `PlayerBlockBreakEvents.AFTER` → Mining XP (pickaxe only, survival only, via `MiningXpTable`). | Works only if the final break still fires the Fabric event. Could be replaced by an impact/break callback that also credits partial contributors. |
| `api/rpg/skills/mining/MiningXpTable`, `ProspectorGemTable` | XP per block; Prospector drop table (comment says call from MiningSkillEvents when active; not yet wired). | Data; independent. |
| `item/magic/rune/effect/BreakEffect` | Spell "Break": calls `playerWillDestroy/removeBlock/destroy/playerDestroy` with a synthetic pickaxe tool built from a numeric `harvestLevel`; non-player casters use `level.destroyBlock`. Contains its own `getRequiredHarvestLevel` logic. | **Migration + conflict of concepts:** an ad-hoc numeric harvest level already exists. This is a prototype MiningTier and a spell-as-damage-source consumer. |
| `api/core/movement/GroundSlamImpact` | AoE `level.destroyBlock(pos,false)` gated by `hardnessLimit` using `getDestroySpeed`. | Consumer of "hardness"; future: explosion/impact damage source. |
| `entity/magic/OrbitProjectileEntity` | Checks `getDestroySpeed >= 0` before affecting blocks. | Minor consumer. |
| `api/ability/impl/HarvestAbility` + `api/ability/harvest/*` handlers | Crop/plant right-click harvesting, unrelated to breaking. | None. |
| `server/TotalityFakePlayer` | Stub `ServerPlayer` for machine automation (`gameMode`, interactions). | Direct precedent for "arbitrary actors" and machines; but a fake player inherits player-only assumptions. Relevant to Actor abstraction. |
| Block registration (`init/blocks/*`, `TotalityRegistry`) | `.strength(...)`, `.requiresCorrectToolForDrops()` used extensively; `datagen/ModBlockTagProvider` writes `MINEABLE_WITH_PICKAXE/AXE`, `NEEDS_DIAMOND_TOOL`. | These define hardness and tiers today via vanilla tags. Integrity/Tier data can be derived from hardness + tags at first; explicit per-block overrides later. |
| Item definitions | `BasicWeaponItems` uses `ToolMaterial.*` for repair/enchantability only. No custom digger items found. | No tool items to migrate yet. |
| `api/rpg/stats/StatAttributeApplier` | DEX → `ATTACK_SPEED` etc. | The proper pattern: STR could apply to an attribute the mining calc reads. No mining-related attribute (`MINING_EFFICIENCY`, `BLOCK_BREAK_SPEED`) is touched today. |
| `TotalityHarvestHandlers`, `RitualBlocks` (`strength(0f)`) | Unrelated `destroy` hits (registry). | None. |

Not found: any `startDestroyBlock`/`continueDestroyBlock` mixin, any `AttackBlockCallback`, any `getDestroySpeed`/`getDestroyProgress` override, any `Attributes.BLOCK_BREAK_SPEED/MINING_EFFICIENCY` use, any `destroyBlockProgress` call, any per-position block-damage state, or any STR-driven bare-hand mining logic. The current `MiningSkillEvents` comment ("Not bare hands") is the only bare-hand-related rule (XP gating, not eligibility). Existing project audits mention Punchy!, which has an *animation* relationship (not analyzed here): mining swings will need to be reconciled with that architecture.

---

## 14. Strongly Relevant Ideas

1. **Intent-only client, server-owned impact loop.** Client says "I'm holding attack at this target"; server owns cadence and applies impacts. Fits "server-authoritative damage" directly.
2. **Cadence as a per-actor cooldown value** computed server-side and *also* sent to the client as animation length, so hand animation and impact share one number.
3. **Routing the terminal action through vanilla `destroyBlock`** to keep drops, Silk Touch/Fortune, durability, stats, custom blocks.
4. **`destroyBlockProgress` with a synthetic id** for crack visuals (works for any number of sources, and the miner can see their own).
5. **Re-derive the target on the server** rather than trusting the client's block position (do this *better* than IM: no unchecked fallback).
6. **Server-computed correct-tool/tier flags returned to the client** for feedback (the "clink" on wrong tier), presentation hint only.
7. **Clean early-out for modes** (creative/spectator) and for instant-break blocks (§16 caveat on how).

## 15. Useful Reference Ideas

- Movement/environment modifiers as multipliers (airborne/water/climbing) — conceptually useful, but 26.2 already has `BLOCK_BREAK_SPEED`, `SUBMERGED_MINING_SPEED`, airborne ÷5; prefer attributes.
- Fractional/per-hit progress *as UI language* ("stage" crack) even if integrity is absolute.
- Fade-based recovery (delay, then step down one stage per interval) as a *shape* for block damage recovery.
- Impact offset / wind-up delay to align contact frames; useful as a data point for animation contact timing.
- Sampling a modifier ratio from other systems to scale cadence (their `BreakSpeed` capture) — the idea "external multipliers apply as one ratio" is worth keeping; the *event-capture* technique is not portable.
- "Entity obstruction" check: refusing to mine through pickable entities.
- Config-driven "vanilla-handled" block/tool overrides (tags) for exemptions.

## 16. Unsuitable Ideas

- **Per-player damage float keyed by UUID** (wrong ownership model for shared, persistent, multi-source integrity).
- **Damage as fraction of a block** (`1/swingsNeeded`) rather than an absolute integrity reduced by power; blocks scaling and tier/STR interplay.
- **Hard-coded instant-break tables and material-class floors** (3/8/24 hits, hardness "buckets" for vanilla blocks): brittle, version-sensitive, wrong for modded blocks; replace with data (tags/components) or a formula.
- **Client-declared target fallback without line-of-sight** (exploitable; §8).
- **Direct `destroyBlock` with no START-time checks** (bypasses spawn protection, `mayInteract`, range/height checks, `attack` hook, on-hit enchantments).
- **"Auto vanilla fallback" force-break after 3 failures** (defeats protection/cancel logic).
- **Optional/client-cooperative design** (server does not stop vanilla mining).
- **Per-tick hold packet + trusted `impactOffset`.**
- **Static in-memory maps for all state, no Saved Data/persistence, no chunk/dimension lifecycle.**
- **`1,000,000 + pos.hashCode()` crack id scheme.**
- **Swing = pure server timer with no physical connect.**
- **Client swing-duration mixin overrides** (heavy, version-sensitive) when the item component (`SWING_ANIMATION`) is now data-driven.
- All compat mixins/reflection for third-party mods, sound tables, GUI/config screens, NeoForge-specific APIs.
- **Adventure-mode handling** (appears broken; do not imitate).

## 17. Missing Features Totality Still Needs

Verified absent from Incremental Mining:

- **BlockIntegrity** as an absolute, per-block quantity (there is only per-player fractional progress).
- **Shared block state** as the default (only under an optional third-party mod).
- **Persistent damage** (disk/chunk/attachment) and **damage recovery** as a first-class, tunable system.
- **Multiple simultaneous damage sources** with attribution (XP credit, loot rights).
- **Mining Power** (an absolute per-impact quantity) and **Mining Tier** (numeric gating independent of vanilla tags).
- **STR-driven bare-hand eligibility** or any actor-stat input; it reads only tool/enchant/effects.
- **Arbitrary actors** (mobs/companions/machines/spells/explosions): every path requires `ServerPlayer`.
- **Server rejection of vanilla mining** (true authority).
- **A physically-connected swing** (animation contact frame → impact).
- **Block replacement/piston/explosion/chunk-lifecycle handling** for damaged blocks.
- **Custom fracture/rubble rendering** (only vanilla 10-stage cracks).
- **Public API/events** for other systems (Stamina cost, Physiology, Buffs, Skills, Enchantments) to hook impacts.
- **Late-joining players** receiving existing cracks (no replay).
- Compatibility with Fabric `PlayerBlockBreakEvents` semantics (NeoForge model only).

---

## 18. Preliminary Totality V1 Recommendation

*(Based only on this audit; not implemented; challenges to the current candidate are marked ⚑.)*

**Candidate set is sound: `BlockIntegrity`, `MiningImpact`, `MiningPower`, `MiningTier`, per-position `BlockDamageState`, server-authoritative damage, vanilla crack-stage sync, real break via normal destruction.** The audit supports it and shows IM's biggest gap is exactly the *block-scoped* state.

Smallest useful foundation:

1. **`BlockDamageState` (server, per `ServerLevel`, keyed by `BlockPos`)**: `remainingIntegrity` (float), `maxIntegrity` (captured at first hit), `lastImpactTick`. A `MiningImpact` lands on it; no player identity in the state itself (attribution is a separate optional field, deferred).
2. **`MiningImpact` record**: `actor` (nullable `Entity`/`LivingEntity`, so players, mobs, machine fake-players and explosions can all produce it), `pos`, `power` (absolute float), `tier` (int), `source kind`. **One entry point** `applyImpact(level, impact)` that all sources call. ⚑ *Challenge:* IM couples damage to `ServerPlayer`; keeping the *actor optional at the type level* is the key difference.
3. **`BlockIntegrity`**: derived from `state.getDestroySpeed` (hardness) via a single formula for V1 (data overrides later). ⚑ *Challenge:* do not adopt fraction-per-swing; use integrity minus power so multiple sources and unequal hits compose. Crack stage = `floor(9 × damageTaken / maxIntegrity)`.
4. **`MiningPower`** for a player actor = a function of tool speed (`ItemStack.getDestroySpeed` / `Tool` rules), an STR-derived attribute, Efficiency (`MINING_EFFICIENCY`), Haste/Fatigue and `BLOCK_BREAK_SPEED` — i.e. reuse `Player.getDestroySpeed(state)` (VERIFIED as attribute-driven in 26.2) as the *base* and layer Totality attributes on top, rather than re-deriving vanilla math.
5. **`MiningTier`**: numeric comparison `impact.tier >= block.requiredTier`, where required tier is derived from vanilla `NEEDS_*_TOOL` tags for V1 and from Totality data later. Insufficient tier ⇒ ineffective (zero or heavily reduced) impact + feedback, mirroring IM's "clink" idea; do not lean on `correctForDrops` alone.
6. **Cadence**: one value per actor (`ticks between impacts`), from the item swing-animation duration and modifiers, used by the server as cooldown and sent to the client as animation length. ⚑ *Challenge:* IM's swing is a server timer with no physical connect. Recommended V1 stance: **server timer + animation whose contact frame is scheduled to coincide with the impact** (impact fires at contact tick), *not* client-reported contact. Client-reported contact is the more "physical" model but adds latency/authority problems; defer it.
7. **Input**: client mixin cancels vanilla `startDestroyBlock`/`continueDestroyBlock` for survival (IM's essential trick, Totality already has `MinecraftAttackMixin` on `startAttack`); client sends `start/stop + BlockHitResult` **only on change**, not each tick (⚑ improvement over IM). **Server rejects vanilla `handleBlockBreakAction` START/STOP for survival** (server mixin) so authority is real. ⚑ *Challenge:* IM does not.
8. **Server validation at each impact (do the vanilla START checks IM skips)**: `isWithinBlockInteractionRange`, build height, spawn protection, `mayInteract`, `blockActionRestricted`, entity obstruction, **server raycast must hit the block** (no unchecked client fallback), then call `BlockState.attack` + `EnchantmentHelper.onHitBlock` semantics if we want parity.
9. **Crack sync**: `destroyBlockProgress(id, pos, stage)` with a stable id per *position-owner* (e.g. a per-level `BlockPos → id` that cannot collide with entity ids) and periodic re-broadcast **< 400 ticks** while damage persists; replay to players entering range. Only send when the stage changes.
10. **Break**: on `remainingIntegrity <= 0`, route through `ServerPlayerGameMode.destroyBlock` when the actor is a `ServerPlayer` (keeps drops/durability/stats/Fabric events), and through `Level.destroyBlock(pos, true, entity)` (or a `TotalityFakePlayer` path) for others. Clear state.
11. **Explicitly deferred from V1**: persistence to disk (design the state class so it can be attached to chunk data later), recovery (a single tick-driven decay function can be added), attribution/XP-split, custom fracture rendering, machines/spells (only the entry point exists), Stamina.

**Persistence caveat to decide early (§19):** if damage is meant to persist, state should not live in a static map. Storing it on the level/chunk (Saved Data or Fabric attachments) is a V1-shaping decision even if V1 doesn't recover damage.

---

## 19. Risks / Open Questions

1. **Persistence scope.** Memory-only (like IM) vs. saved-with-chunk from day one. Impacts data model, chunk unload handling, and late-join sync. *(Design decision.)*
2. **"Connect" semantics.** Server timer aligned to animation vs. client-reported contact frame; how much latency to tolerate. *(Design decision; IM offers no contact model.)*
3. **Does attack speed (`ATTACK_SPEED`, DEX-driven in Totality) drive mining cadence?** IM says no (only Efficiency/Haste). Totality's DEX→ATTACK_SPEED mapping means this choice changes balance. *(Design decision.)*
4. **Fabric `PlayerBlockBreakEvents` injection point** inside `ServerPlayerGameMode.destroyBlock` — **UNKNOWN** here; must be tested because Veinminer and Mining XP depend on it.
5. **Client prediction.** With vanilla's `startPrediction`/sequence flow bypassed, does the client show a ghost block or misalign on server-driven break? Requires testing (block update on `removeBlock` should suffice; UNKNOWN).
6. **Instant-break blocks** (grass, flowers, torches, `hardness 0`) and **creative/`Tool.canDestroyBlocksInCreative`**: keep them vanilla (single-click) or route through impacts? IM keeps them vanilla via a brittle table; Totality should decide by a simple rule (e.g. `hardness == 0`).
7. **Block replacement while damaged** (piston/explosion/fluid): need a state-identity check (store the `BlockState`/`Block` at first hit) — not solved by IM.
8. **Chunk unload behavior of stored damage** and server-tick cost of iterating damaged positions (IM's shared path indicates the risk).
9. **Multiple crack ids per position**: 26.2 client stores a sorted set per position — verify the *highest progress* wins visually so multiple sources don't flicker (UNKNOWN; not checked in `LevelRenderer`).
10. **Adventure mode**: decide up front; IM's handling appears broken.
11. **Punchy! relationship**: swing animation ownership needs to be reconciled with the existing Punchy audit (mining swing vs. attack swing).
12. **Not inspected in depth:** compat classes, GUI/config screens, sound logic, `BlockRenderDispatcher`/`LevelRenderer` mixin bodies. None affect the conclusions above.
13. **No runtime testing** of Incremental Mining was performed (static analysis only, and it cannot run on 26.2/Fabric).

---

## 20. Exact Files / Classes Inspected

**Inspiration mod (decompiled to scratchpad, outside repo):**
`Inspiration Mods/incrementalmining-1.21.1-1.13.jar` → `META-INF/neoforge.mods.toml`, `META-INF/MANIFEST.MF`, `incrementalmining.mixins.json`, `pack.mcmeta`; `com.jdynamo.incrementalmining.`
`IncrementalMining`, `IncrementalMiningClient`, `events.{MiningServerLogic, MiningState, MiningRules, MiningFeedback, MiningEvents, ClientMiningEvents}`, `mining.{SwingManager, SwingTiming, BlockDamageManager, SharedBlockDamageManager, MiningTargets, ExternalMiningSpeed}` (`MiningOverrideConfig`, `MiningSoundLogic` only skimmed), `client.{ClientInputHandler, ClientSwingListener}`, `network.{ModNetwork, packet.HoldAttackPacket, packet.SwingFeedbackPacket, packet.BreakEffectsPacket}` (`BlockSoundPacket`, `ConfigSyncPacket` by signature), `mixin.client.{MultiPlayerGameModeMixin, LocalPlayerMixin, LivingEntityMixin, ParticleEngineMixin, ItemInHandRendererMixin}` (`BlockRenderDispatcherMixin`, `LevelRendererMixin`, compat mixins: partial), `config.MiningConfig` (defaults only), `compat.ModCompatibility`/`MultiMineCompat` (signature/grep only).

**Minecraft 26.2 (Loom cache `minecraft-merged-…-26.2-sources.jar`):**
`client/multiplayer/MultiPlayerGameMode`, `client/multiplayer/ClientLevel`, `client/Minecraft` (`startAttack`, `continueAttack`, `startUseItem`), `server/level/ServerPlayerGameMode`, `server/level/ServerLevel` (`destroyBlockProgress`), `server/level/BlockDestructionProgress`, `network/protocol/game/ClientboundBlockDestructionPacket`, `world/entity/player/Player` (`getDestroySpeed`, `hasCorrectToolForDrops`, `blockActionRestricted`, attack-strength methods), `world/entity/LivingEntity` (`getCurrentSwingDuration`, swing), `world/level/block/state/BlockBehaviour` (`getDestroyProgress`), `world/item/component/Tool`. Fabric API 0.160.0+26.2 `fabric-events-interaction-v0 5.2.8` (class listing only).

**Totality (read-only):**
`mixin/client/MinecraftAttackMixin`, `mixin/client/MultiPlayerGameModeMixin`, `totality.mixins.json`, `api/ability/impl/VeinminerAbility`, `api/rpg/skills/mining/{MiningSkillEvents, MiningXpTable}`, `item/magic/rune/effect/BreakEffect`, `api/core/movement/GroundSlamImpact`, `server/TotalityFakePlayer`, `entity/magic/OrbitProjectileEntity` (grep), `datagen/ModBlockTagProvider` (grep), `init/blocks/*` (grep), `init/items/BasicWeaponItems` (grep), `api/rpg/stats/StatAttributeApplier` (grep), plus repo-wide greps for the terms listed in §13. Existing `Context/Audit/TOTALITY_PUNCHY_*` documents were listed only.
