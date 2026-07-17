TOTALITY — COMBAT INPUT AND HUD CORRECTIONS
(New document, 2026-07-16. Covers the Phase 4 correction pass's
combat-input work and its follow-up HUD/notification regression fixes.
Kept deliberately separate from `TOTALITY_ECONOMY_PRICING_AND_PROVISIONER.md`
Section 14, which covers that same pass's economy/entity corrections —
the task instructions explicitly asked for these to be documented
apart from each other.)

================================================================================
1. POWER ATTACK — VALID-TARGET REQUIREMENT
================================================================================
ADOPTED, IMPLEMENTED (2026-07-16).

  ROOT CAUSE: `MinecraftAttackMixin.totality$interceptAttack` cancelled
  vanilla's `Minecraft.startAttack()` unconditionally whenever the
  player held a weapon-tagged item, regardless of what was under the
  crosshair. Holding LMB on a block (e.g. chopping wood with an axe)
  therefore always began the Power Attack hold-charge, and
  `totality$tickHold` sent `PowerAttackPayload` at the hold threshold
  with NO target check at all — Stamina was consumed for a "power
  attack" that could never actually land on anything.

  FIX — shared client/server predicate. `PowerAttackManager.isValidTarget
  (Player, @Nullable Entity)` (new, `api/rpg/combat/`): true only for a
  non-null `LivingEntity`, alive, `isAttackable()`, not the player
  themself, within melee reach (`MELEE_TARGET_RANGE = 4.5` — the SAME
  constant `OffhandAttackHandler`'s existing offhand power attack
  already uses; Power Attack is the mainhand analogue of that exact
  mechanic, not a second invented reach value). Takes the common
  `Player` supertype specifically so `LocalPlayer` (client) and
  `ServerPlayer` (server) share the identical check — never two
  copies that could drift apart.

  CLIENT (`MinecraftAttackMixin`): `totality$interceptAttack` only
  cancels vanilla's attack (and begins the hold-charge) when
  `totality$hasValidPowerAttackTarget(client)` is true at the moment of
  the initial press — with no valid target, vanilla's own
  `startAttack()` proceeds completely untouched, so mining/interaction
  is never interfered with. `totality$tickHold` re-checks validity
  EVERY tick during the charge (`targetStillValid`), not just at press
  time — if the player looks away, the target dies, or leaves range
  mid-charge, the charge cancels via the SAME code path an early mouse
  release already used (a normal, non-power attack/swing against
  whatever's currently under the crosshair, if anything) — no
  `PowerAttackPayload` is ever sent for an attempt that was never or is
  no longer valid, so no Power-Attack-specific Stamina is spent.

  SERVER (`TotalityServerPacketHandlers`): `PowerAttackPayload` now
  carries `targetEntityId` (the entity the client's crosshair resolved
  at hold-completion) — the receiver re-resolves that entity via
  `player.level().getEntity(id)` and re-validates it with the SAME
  `PowerAttackManager.isValidTarget` before ever calling
  `onPowerAttackReceived` (which is what actually spends Stamina and
  marks advantage). A modified/malicious client sending the payload
  directly cannot bypass this — server-authoritative, matching this
  codebase's established pattern (`TradeSessionManager` never trusts a
  client-supplied price either).

  Explicitly NOT changed: Power Attack damage, timing, animation,
  Stamina cost, or balance (existing `PowerAttackManager` mechanics
  untouched beyond the new gate); the offhand (RMB) power attack path
  (`OffhandAttackHandler`) already gated on `crosshairPickEntity
  instanceof LivingEntity` before this pass and needed no change.

  VERIFICATION: new `PowerAttackVerification` (`api/rpg/combat/`,
  server-side, `VerificationReporter` convention) — 9 checks: valid
  hostile living target and valid player target both permit Power
  Attack (PvP/team/invulnerability rules are enforced downstream,
  unchanged, by vanilla attack resolution — this predicate does not
  re-implement or bypass them); null (block/air/no pick), self, a
  non-living entity, a dead entity, and an out-of-range entity are all
  rejected; an entity exactly AT the reach boundary is still accepted
  (inclusive); `onPowerAttackReceived` itself still unconditionally
  spends Stamina when invoked, confirming the fix is the upstream gate,
  not a change to what happens once a Power Attack legitimately fires.
  What this suite cannot verify (documented, not skipped): the actual
  client-side hold/charge/cancel state machine, which requires a live
  input-injection tool this environment doesn't have — remains manual.

  MANUAL TESTING: Stefan confirmed, 2026-07-16 — chopping wood no
  longer triggers Power Attack or spends Stamina; a valid combat
  target still permits Power Attack normally.

================================================================================
2. RADIAL MODIFIER — CHORD INPUT MODEL
================================================================================
ADOPTED, IMPLEMENTED (2026-07-16). Resolves the hold-input conflict the
26.2 migration's manual smoke test found and explicitly deferred
(`TOTALITY_26.2_MIGRATION_REPORT.md` §15.6d/§15.7, and the companion
`TOTALITY_26.2_MANUAL_SMOKE_TEST_RESULTS.md` row 21) — NOT a migration
regression itself (the file was only touched during migration for the
mechanical screen-API rename), but a pre-existing design gap the
migration's testing surfaced.

  OLD BEHAVIOR: holding Z (Ability) or X (Spell) for
  `HOLD_THRESHOLD` (10) ticks opened the respective Favorites radial —
  which meant a channeled ability/spell's own hold-to-channel gesture
  got hijacked into opening the radial menu roughly one second in,
  interrupting it.

  NEW MODEL — modifier chord, decided once at the press edge:
    - Z/X alone: activates the equipped Ability/Spell exactly as
      before; holding the key remains entirely available to the
      ability/spell's own hold/channel semantics for as long as it's
      held, with no timeout.
    - Radial Modifier + Z/X: opens the respective radial immediately,
      and does NOT activate/cast anything.
    - The modifier must already be held at the exact tick Z/X
      transitions from up to down for that press to be treated as a
      radial chord — decided once, never re-evaluated for the rest of
      that hold. Pressing the modifier AFTER Z/X has already started
      does not convert a running activation into a radial request;
      releasing the modifier mid-chord does not un-convert it back to
      an activation either. Opening the radial never consumes Ability
      resources, spell slots, Stamina, Mana, or cooldowns — no
      activation packet is ever sent for a radial-chord press.

  NEW KEY MAPPING: `ModKeybinds.RADIAL_MODIFIER`
  (`key.totality.radial_modifier`, localized "Radial Modifier"),
  default Left Alt, rebindable (Left Alt can conflict with external
  overlays — Discord, Nvidia/AMD, Steam — on some systems, which is
  exactly why it must be rebindable rather than hardcoded). Queried via
  `ModKeybinds.RADIAL_MODIFIER.isDown()` in `TotalityKeybindHandlers`
  — never a hardcoded `GLFW_KEY_LEFT_ALT` check anywhere in the input
  handler.

  `abilityRadialOpened`/`spellRadialOpened` (existing fields, reused
  rather than renamed) now mean "this press is a radial chord" instead
  of "the old hold-threshold fired" — `registerVeinminerKeyKeybind`'s
  existing `if (abilityRadialOpened) return;` guard (which suppresses
  channeled-ability start) still works unchanged, and now suppresses
  from the FIRST tick of a radial-chord press instead of only after the
  old ~1-second threshold — this is what actually fixes the interrupt
  bug, as a side effect of deciding chord-vs-activation immediately
  rather than after a delay.

  `AbilityRadialScreen`/`SpellRadialScreen` were NOT modified — both
  already close on the tracked key's release (raw GLFW poll in their
  own `tick()`) and equip the hovered favorite on click or release,
  which needed no adjustment for the new trigger model. The two
  existing unused keymappings `OPEN_RADIAL`/`OPEN_ABILITY_RADIAL`
  (already `GLFW_KEY_UNKNOWN`, unreferenced anywhere) were left alone
  — unrelated dead code from an earlier abandoned plan, not touched
  by this pass.

  VERIFICATION: new `KeybindVerification` (`init/`, client-only,
  `runIfDev()` convention matching `ProvisionerRendererVerification`)
  — 3 checks: `RADIAL_MODIFIER` is registered under the Totality
  category; its default key is Left Alt; it uses its own translation
  key, not a reused one. What this suite cannot verify (documented,
  not skipped): the actual chord state machine in
  `TotalityKeybindHandlers` — holding Z past the old threshold,
  modifier-before-vs-after-Z ordering, release clearing state, and
  live rebinding — all require driving real keyboard input against a
  live client window, which this environment has no tool to do.
  Remains Stefan's manual checklist.

  MANUAL TESTING: Stefan confirmed, 2026-07-16 — Z/X still activate
  normally; holding Z no longer opens the radial and no longer
  interrupts a channeled ability; Modifier+Z opens the Ability radial
  without activating; Modifier+X opens the Spell radial without
  casting; the rebindable modifier works as intended.

================================================================================
3. NOTIFICATION DURATION — POST-MIGRATION REGRESSION AND FIX
================================================================================
FOUND AND FIXED (2026-07-16), during the SAME correction pass as
Sections 1-2, after Stefan's manual test of those two items passed.

  SYMPTOM: Totality's custom notifications (`NotificationManager`)
  stayed visible for noticeably longer before the MC 26.2 migration;
  after migration they disappear much sooner.

  ROOT CAUSE — NOT a migration-introduced code change. `git diff
  953a677 -- .../notification/NotificationManager.java` shows the file
  is byte-for-byte identical pre- and post-migration except the
  unrelated, already-documented `client.options.hideGui` →
  `client.gui.hud.isHidden()` rename (`TOTALITY_26.2_MIGRATION_REPORT.md`
  §15.3). Disassembling both the 26.1.2 (`fabric-rendering-v1
  23.0.4`) and 26.2 (`fabric-rendering-v1 25.3.1`) Fabric API jars
  confirms `HudLayer.class` (the class that actually invokes a
  registered `HudElementRegistry` callback) is ALSO byte-for-byte
  identical between the two versions. Neither Totality's code nor
  Fabric's HUD-element invocation mechanism changed.

  The bug itself is pre-existing and always frame-rate dependent:
  `Notification.ticksLeft` was decremented once per invocation of the
  `HudElementRegistry.addLast` callback — which fires once per
  RENDERED FRAME, not once per game TICK. `LIFETIME_TICKS = 80` was
  always intended as "80 ticks at the fixed 20-tick/sec game rate ≈ 4
  seconds," but was actually being consumed as "80 RENDERED FRAMES."
  This was always technically wrong, but was much less noticeable
  under 26.1.2's older, heavier rendering pipeline (naturally lower
  achieved FPS in comparable scenes). 26.2's extensive GPU-pipeline
  rewrite (§15.3/§15.6b of the migration report — batched draws,
  Vulkan-style command encoding) legitimately achieves a substantially
  higher sustained frame rate for the same scene, so the identical
  per-frame decrement now burns through the 80-"tick" budget far
  faster in real time — a genuine, user-visible post-migration
  regression, even though zero notification-timing code changed. The
  same root cause affects Section 4's Power Attack flash.

  FIX — decouple authoritative lifetime from rendering entirely.
  `NotificationManager.tickActive()` (new) is the SOLE mutator of
  `ticksLeft`, called only from a new `ClientTickEvents.END_CLIENT_TICK`
  registration — exactly once per game tick, regardless of frame rate.
  The `HudElementRegistry` render callback (`renderActive`, new) now
  only READS current state (`computeAlpha(ticksLeft)`, pure) to draw —
  it never mutates `ticksLeft`. Both the tick and render callbacks
  share the exact same `client.player == null ||
  client.gui.hud.isHidden()` guard the original single callback used,
  so F1 continues to pause the countdown while the HUD is hidden —
  unchanged visible behavior, faithfully restored rather than
  reinvented. A new `ClientPlayConnectionEvents.DISCONNECT` handler
  clears all active notifications, so a notification whose 80-tick
  lifetime hadn't yet expired at disconnect can never survive into the
  next world/server session (previously nothing cleared this static
  list on disconnect at all).

  TIMING MODEL: game/client ticks (`ClientTickEvents.END_CLIENT_TICK`)
  — the SAME model the original 80/20 constants always assumed, now
  actually delivered. `LIFETIME_TICKS = 80` (~4 seconds), `FADE_TICKS
  = 20` (~1 second) unchanged. CONFIRMED (via `git diff`) the original
  26.1.2 design has NO fade-in phase — alpha is immediately 1.0 the
  instant a notification is added, holds at 1.0 until the final
  `FADE_TICKS`, then fades linearly to 0. This is preserved exactly,
  not invented — flagged explicitly because the task's own generic
  manual-checklist wording ("confirm fade-in works") doesn't match
  what the original code actually does; the original code, not the
  checklist wording, is the source of truth here per task instruction.

  VERIFICATION: new `NotificationTimingVerification`
  (`client/renderer/hud/notification/`, client-only, `runIfDev()`
  convention) — 9 checks, all driving `NotificationManager`'s new
  package-private test hooks directly (no live `Minecraft`/render
  context needed): one `tickActive()` call decrements by exactly 1;
  1000 simulated "reads" (`computeAlpha` calls) never advance
  `ticksLeft`; low-vs-high simulated frame rate produces identical
  tick-based expiry (frame count is never consulted anywhere in the
  fixed path); hold-phase alpha is 1.0 before the fade window; the
  original has no fade-in (documented, not invented); removal happens
  only once `ticksLeft` reaches exactly 0, never mid-fade; an expired
  notification is removed exactly once and further ticks against the
  now-empty list are safe; two queued notifications retain fully
  independent countdowns; `clear()` (the disconnect-reset path)
  removes all stale state. What this suite cannot verify (documented,
  not skipped): whether the real callbacks are wired correctly at
  runtime, F1 visually hiding the text, and actual real-time visible
  duration at a given frame rate — all require a live client window.

  MANUAL TESTING: Stefan confirmed, 2026-07-16 — fixes work.

================================================================================
4. POWER ATTACK SCREEN-CORNER FLASH — POST-MIGRATION REGRESSION AND FIX
================================================================================
FOUND AND FIXED (2026-07-16), same pass as Section 3 — identical root
cause pattern, found independently, NOT assumed to share one root
cause until confirmed.

  SYMPTOM: after Section 1's Power Attack gameplay mechanic was
  confirmed working, the original screen-edge/corner flash that used
  to accompany a completed Power Attack no longer visibly appeared.

  ROOT CAUSE — confirmed independently, not assumed from Section 3.
  `git diff 953a677 -- .../hud/TotalityHudRenderer.java` shows the
  Power Attack flash block is ALSO byte-for-byte identical pre- and
  post-migration (same lone unrelated `hideGui` rename touching that
  method). `PowerAttackFlash.trigger()` was confirmed still correctly
  called from both completion sites in `MinecraftAttackMixin`
  (mainhand and offhand) — the combat mechanic and packet path were
  never broken, exactly as the task instructed to verify independently
  rather than assume. The actual defect: `PowerAttackFlash.tick()`
  (which decrements `flashTimer`, a 10-"tick" / `FLASH_DURATION`
  budget) was called from INSIDE `TotalityHudRenderer`'s
  `HudElementRegistry` render callback — the exact same
  frame-not-tick bug as Section 3, at an even shorter budget (10
  vs. 80), so 26.2's higher achieved frame rate burns through it in a
  small fraction of a real second — visually indistinguishable from
  "never appears," which is exactly what was reported.

  The visual itself was NOT changed and is NOT being redesigned: it is
  a full-screen translucent orange tint
  (`graphics.fill(0, 0, screenW, screenH, flashColor)`, color
  `0xFFFF6600`, alpha capped at `0.4 * 255`), confirmed via the same
  diff to be the ORIGINAL, unchanged implementation — not a literal
  corner-only vignette. Restored exactly as it was; not replaced with
  a bar, icon, meter, or any new widget.

  FIX — same pattern as Section 3. `PowerAttackFlash.register()`
  (new) registers a `ClientTickEvents.END_CLIENT_TICK` listener that
  calls the (now `private`) `tick()` — the sole mutator of
  `flashTimer`. `TotalityHudRenderer` no longer calls
  `PowerAttackFlash.tick()` at all; it only reads `isActive()`/
  `getAlpha()` to draw. The tick listener shares the same `client
  .player == null || client.gui.hud.isHidden()` guard the render
  callback already used, so F1 continues to suppress/pause the effect
  exactly as before. A new `ClientPlayConnectionEvents.DISCONNECT`
  handler calls `reset()`, clearing any in-progress flash so it can
  never survive into a new world/server session — covers the
  death/disconnect/dimension-change stale-state requirement; a normal
  in-world death or dimension change doesn't need a separate hook
  since the 10-tick (0.5 second) budget already self-clears almost
  immediately under ordinary play regardless.

  Explicitly NOT changed: Power Attack timing/balance, when the flash
  triggers (still exactly at hold-charge completion, both hands,
  unchanged), its color, its fade curve, or its full-screen geometry.

  VERIFICATION: new `PowerAttackFlashVerification`
  (`client/renderer/hud/`, client-only, `runIfDev()` convention) — 7
  checks: `trigger()` enables the effect with nonzero alpha; idle
  state (never triggered, or fully decayed) is disabled; a single
  `tick()` call decrements by exactly 1; alpha fades monotonically to
  exactly 0, never negative; the effect fully clears after its 10-tick
  duration elapses; `reset()` immediately clears an in-progress effect;
  simulated low-vs-high frame rate produces identical tick-based
  clearing (frame count is never consulted anywhere in the fixed
  path). What this suite cannot verify (documented, not skipped):
  whether the flash is visually present, F1/GUI-scale/screen-geometry
  behavior in a live render, and that invalid-target input never calls
  `trigger()` at all (that last property belongs to
  `MinecraftAttackMixin`'s target-gating, Section 1 — confirmed here
  only by code inspection: `trigger()` is called from exactly two
  sites, both already gated by `targetStillValid`/a resolved
  `LivingEntity` crosshair pick).

  MANUAL TESTING: Stefan confirmed, 2026-07-16 — fixes work.

================================================================================
5. SHARED ROOT-CAUSE PATTERN (Sections 3-4) — NOT ONE FIX, TWO INSTANCES
================================================================================
  Both regressions were the SAME class of bug (authoritative lifetime
  state mutated inside a render/HUD-extraction callback instead of a
  tick callback), found and confirmed independently via separate `git
  diff`s against the same pre-migration commit — not solved by a
  single shared code change. `NotificationManager` and
  `PowerAttackFlash` each own their own `ClientTickEvents.END_CLIENT_TICK`
  registration; no shared/duplicate render callback was introduced;
  render-state extraction in both now gathers immutable display data
  only. Neither fix introduced frame-dependent behavior into the
  other, and neither runs its authoritative tick logic more than once
  per game tick.

================================================================================
6. OUT OF SCOPE (this document)
================================================================================
  Not implemented or touched by Sections 1-4: Power Attack
  damage/Stamina-cost/charge-duration/balance changes; Ability/Spell
  input redesign beyond the chord model in Section 2; ritual spell
  mechanics; Heat Vision or Fluid Tank visuals; a general HUD redesign;
  the Trading Screen visual/layout pass (still deferred to the later
  Fable session per `TOTALITY_ECONOMY_PRICING_AND_PROVISIONER.md`
  Section 13/14); any broader migration cleanup not directly required
  by these specific corrections.

================================================================================
7. POST-REVIEW CORRECTION PASS (2026-07-17) — Ability/Spell rebinding,
   offhand Power Attack target gating, server legality before Stamina
================================================================================
  A second, narrowly-scoped correction pass, found by final review of
  Sections 1-2's own work rather than a new manual-test report. Kept
  deliberately separate from the SAME task's economy/SELL corrections,
  documented in `TOTALITY_ECONOMY_PRICING_AND_PROVISIONER.md` Section 16.

  --------------------------------------------------------------------------
  7a. ABILITY/SPELL KEYBIND REBINDING — ROOT CAUSE AND FIX
  --------------------------------------------------------------------------
  ROOT CAUSE, CONFIRMED BY AUDIT: `TotalityKeybindHandlers.registerAbilityKeybind`
  computed its own `zDown`/`xDown` via raw
  `InputConstants.isKeyDown(window, GLFW_KEY_Z)`/`GLFW_KEY_X` —
  completely bypassing the registered `ModKeybinds.USE_ABILITY`/
  `USE_SPELL` key mappings for BOTH ordinary activation and the Section
  2 radial-chord decision. Rebinding either key in the controls menu
  changed nothing in this method. Meanwhile
  `registerVeinminerKeyKeybind` (channeled-ability hold/release) already
  correctly used `ModKeybinds.USE_ABILITY.isDown()` — so a rebind
  produced a SPLIT: the channeled-hold gesture followed the rebound key
  while ordinary activation and the radial chord silently kept
  following literal Z/X. `ModKeybinds.RADIAL_MODIFIER.isDown()` was
  already correct and untouched by this finding.

  FIX: `registerAbilityKeybind` now reads `ModKeybinds.USE_ABILITY.isDown()`/
  `ModKeybinds.USE_SPELL.isDown()` as the SOLE source of pressed/down
  state for both hands — no raw GLFW key code appears anywhere in the
  method any longer. This is the exact same accessor
  `ModKeybinds.RADIAL_MODIFIER` already used, so all three key mappings
  (Ability, Spell, Radial Modifier) now share one consistent input
  pattern. The press-edge chord-decision logic, the quick-tap activation
  logic, and `abilityRadialOpened`/`spellRadialOpened`'s suppression of
  `registerVeinminerKeyKeybind` are all UNCHANGED — only the boolean
  SOURCE for "is the key down right now" changed, so rebinding now
  transparently changes activation, the radial chord, AND the
  channeled-hold gesture together, since they all ultimately read the
  same `KeyMapping`. Default Z/X behavior is unchanged, since
  `ModKeybinds.USE_ABILITY`/`USE_SPELL` still default to Z/X.

  Explicitly NOT touched: the Grimoire (C key) and Block (V key)
  handlers, which still poll their own literal GLFW key codes — outside
  this task's explicit scope (Ability/Spell/Radial Modifier only); if a
  similar rebinding gap is wanted for those, that is separate scope.

  VERIFICATION: `KeybindVerification` extended from 3 to 8 checks — the
  3 original Radial Modifier checks are unchanged; 5 new checks confirm
  `ModKeybinds.USE_ABILITY`/`USE_SPELL` are registered under the
  Totality category, default to Z/X respectively, and that all three
  key mappings (Ability, Spell, Radial Modifier) use distinct
  translation keys. What this suite still cannot verify (documented, not
  skipped): the actual chord/rebinding state machine under real keyboard
  input — remains Stefan's manual checklist.

  --------------------------------------------------------------------------
  7b. OFFHAND POWER ATTACK — FALSE CHARGE/FLASH ROOT CAUSE AND FIX
  --------------------------------------------------------------------------
  ROOT CAUSE, CONFIRMED BY AUDIT: unlike the mainhand path (Section 1),
  the offhand path in `MinecraftAttackMixin` had THREE independent gaps:
    1. `totality$interceptOffhandAttack` (injects `startUseItem`)
       cancelled vanilla's use-item handling and began the hold-charge
       (`totality$offhandHolding = true`) unconditionally whenever
       dual-wielding, with NO target-validity check — looking at a
       block, air, or nothing at all still started the charge.
    2. `totality$tickHold`'s offhand branch re-checked only `dualWielding`
       every tick, never re-validating the target — unlike the
       mainhand's own `targetStillValid`, losing the target mid-charge
       did not cancel the offhand attempt.
    3. `totality$fireOffhandAttack` gated the actual `OffhandAttackPayload`
       send on `crosshairPickEntity instanceof LivingEntity`, but
       triggered `PowerAttackFlash.trigger()` and the strong-attack
       sound UNCONDITIONALLY whenever `powerAttack=true` — so reaching
       the hold threshold with no valid target still showed the orange
       flash and played the sound, even though the payload itself (and
       therefore Stamina/advantage) was correctly suppressed.

  FIX — mirrors the mainhand path exactly, sharing the SAME predicate
  (`PowerAttackManager.isValidTarget`, unchanged from Section 1):
    1. `totality$interceptOffhandAttack` now only cancels/begins
       charging when `totality$hasValidPowerAttackTarget(client)` is
       true — with no valid target, vanilla's own `startUseItem`
       proceeds untouched, so ordinary interaction/block use is never
       interfered with (matching the mainhand's identical guarantee for
       mining).
    2. `totality$tickHold`'s offhand branch gained
       `offhandTargetStillValid`, re-checked every tick — losing the
       target mid-charge now cancels via the SAME early-release code
       path (a normal, non-power offhand swing against whatever's
       currently under the crosshair, if anything), exactly mirroring
       the mainhand's `targetStillValid` handling.
    3. `totality$fireOffhandAttack` now gates the flash/sound on the
       SAME `hasLivingTarget` check the payload send already used — kept
       as explicit defense-in-depth even though, after fix 1/2, the
       threshold-reached call site can no longer be reached with an
       invalid target at all.
  No Stamina is spent, no flash triggers, and no strong-attack sound
  plays for an offhand charge that begins or completes against an
  invalid target. `OffhandAttackHandler` (server) was additionally
  refactored to call the SAME `PowerAttackManager.isValidTarget` instead
  of its own duplicate reach/liveness check (`MAX_ATTACK_RANGE_SQ`,
  numerically identical to `PowerAttackManager.MELEE_TARGET_RANGE` but a
  separate literal) — one shared predicate for mainhand and offhand,
  server-side, per the task's explicit "prefer a shared target-
  resolution/validation helper" instruction.

  --------------------------------------------------------------------------
  7c. SERVER-SIDE ATTACK LEGALITY BEFORE STAMINA — ROOT CAUSE AND FIX
  --------------------------------------------------------------------------
  ROOT CAUSE, CONFIRMED BY AUDIT: both the mainhand `PowerAttackPayload`
  handler (`TotalityServerPacketHandlers`) and the offhand handler
  (`OffhandAttackHandler`) validated only target TYPE/LIFE-STATE/REACH/
  ATTACKABILITY (`PowerAttackManager.isValidTarget`) before committing
  Stamina (`PowerAttackManager.onPowerAttackReceived`, or the offhand's
  own `PlayerStaminaManager.removeStamina` for its power-specific cost).
  Attacker-specific legality — PvP-disabled worlds, team friendly-fire
  settings, general invulnerability — was NOT checked at that point at
  all; it was only ever enforced downstream, later, by vanilla's own
  damage resolution once the actual attack ran — by which point Stamina
  had already been spent.

  FIX — new shared predicate `PowerAttackManager.isAttackerLegal(Player
  attacker, LivingEntity target)`, a SEPARATE concern from
  `isValidTarget`: false if `target.isInvulnerable()`, or if the target
  is a `Player` and either the world's `GameRules.PVP` rule is disabled
  or `attacker.canHarmPlayer(targetPlayer)` returns false (team
  friendly-fire) — the EXACT rules vanilla's own player-attack
  resolution already applies for a player target, not an invented
  blanket restriction; ordinary neutral/allied non-player mobs are
  completely unaffected (confirmed by a dedicated verification check).

  REQUIRED ORDER, now implemented identically for both hands:
    1. Resolve live target.
    2. `isValidTarget` (type/life-state/reach/attackability).
    3. `isAttackerLegal` (PvP/friendly-fire/invulnerability) — NEW gate,
       inserted here, before Stamina.
    4. Sufficient Stamina and other Power Attack conditions.
    5. Commit Stamina.
    6. Perform the attack.
    7. Trigger accepted visual/audio feedback.

  MAINHAND (`TotalityServerPacketHandlers`): the `PowerAttackPayload`
  handler now checks `isAttackerLegal` immediately after `isValidTarget`
  and before `onPowerAttackReceived` — an illegal target means the
  payload is silently ignored, Stamina untouched.

  OFFHAND (`OffhandAttackHandler`): `usePower` (whether the offhand
  attack rolls with advantage and spends the larger power-attack
  Stamina cost) now additionally requires `isAttackerLegal(player,
  target)`, alongside the pre-existing Stamina-sufficiency check —
  exactly mirroring the existing "gracefully downgrades to a normal
  roll if Stamina is insufficient" pattern. An illegal target downgrades
  the swing to a normal (non-power) attack rather than rejecting the
  whole offhand swing outright — ordinary (non-power) attack Stamina/
  damage handling is explicitly OUT of this pass's scope, matching the
  task's "Power Attack" framing specifically.

  SERVER ACCEPTANCE RESPONSE: NOT ADDED. Instead, the CLIENT gained its
  own optimistic `isAttackerLegal` pre-check (Section 7b's mixin, both
  hands) using ALREADY-SYNCED authoritative state — `Level.getGameRules()`
  is exposed only on `ServerLevel` in this Minecraft version (confirmed
  by decompiled-class inspection: neither the generic `Level` nor
  `ClientLevel` expose it, even though game rule VALUES ARE synced to
  the client via a dedicated packet), so the PVP-rule half of the
  client-side pre-check only actually evaluates when `attacker.level()`
  is genuinely a `ServerLevel` — i.e., never on the client. The
  client-side call therefore only meaningfully gates on invulnerability
  and (where visible) team friendly-fire; it is optimistic-only and
  NEVER authoritative — the server's own `isAttackerLegal` call remains
  the real, only-trusted gate. This is a real, if partial, reduction of
  false accepted-feedback using existing synced state, not full
  server-authoritative confirmation networking, per the task's explicit
  "do not add speculative networking if existing authoritative client
  state already solves it cleanly" allowance. The ordinary swing/attack
  itself (vanilla's own resolution, which will still correctly refuse
  actual damage) is UNCHANGED and always still happens regardless of
  `isAttackerLegal`'s outcome — only the Power-Attack-specific payload,
  Stamina, flash, and sound are gated on it.

  VERIFICATION: `PowerAttackVerification` extended from 9 to 12 checks.
  New: a legal invulnerable target is rejected; an ordinary hostile mob
  remains attacker-legal even with the server's PvP rule disabled (the
  "no blanket restriction against neutral/allied mobs" proof); a player
  target is rejected while PvP is disabled. A planned "ordinary
  player-vs-player target is legal" positive check, and a dedicated
  team-friendly-fire check, were NOT added — `TotalityFakePlayer.canHarmPlayer(Player)`
  is hardcoded `false` for every fake-player fixture instance (shared,
  pre-existing test infrastructure this pass should not change), so two
  fake players can never be mutually attacker-legal regardless of
  PvP/team state; a team-friendly-fire-specific check would "pass"
  vacuously without the fixture's flaw invalidating it, since
  `canHarmPlayer` already returns false unconditionally. Documented as a
  known suite limitation rather than silently omitted; team friendly-fire
  specifically remains Stefan's manual checklist (needs a real second
  client-connected player).

  RUNTIME-VERIFIED (2026-07-17, `gradlew runClient
  --args="--quickPlaySingleplayer \"New Testing World\""`):
  `[KeybindVerification] All 8 self-test checks passed.` (was 3);
  `[PowerAttackVerification] All 12 self-test checks passed.` (was 9).
  Every other pre-existing suite unchanged and passing — full combined
  count (10 suites, including the economy-side suites documented in
  `TOTALITY_ECONOMY_PRICING_AND_PROVISIONER.md` Section 16h) is 199
  checks, zero failures, across two consecutive client boots (the first
  boot caught a genuine test-fixture mismatch in an early draft of the
  new `PowerAttackVerification` checks — see 7c above — fixed and
  re-verified clean on the second boot). Zero unexpected `WARN`/`ERROR`
  from the `totality` namespace. `gradlew compileJava`/`build`:
  SUCCESSFUL.

  MANUAL TESTING: CONFIRMED (2026-07-17, Stefan) — mainhand and offhand
  Power Attack target gating; no invalid-target Stamina use, flash,
  sound, or payload; rebound Ability/Spell keys activate normally and
  the OLD default key stops activating after rebinding; a channeled
  Ability follows the rebound key; default Left Alt + Z/X still opens
  and correctly RETAINS the respective radial. ONE new issue surfaced
  by this same manual pass — see Section 8 below — and PvP/team-
  friendly-fire legality remain explicitly UNTESTED/DEFERRED (Section
  8's PvP status subsection).

================================================================================
8. RADIAL CORRECTION PASS (2026-07-17) — rebound-key radial retention,
   Grimoire modifier chord
================================================================================
  A second, narrower correction pass, found by Stefan's OWN manual test
  of Section 7's rebinding fix (not a new review-bundle audit). Kept
  deliberately separate from Section 7's own findings and from the SAME
  task's economy-document status note (`TOTALITY_ECONOMY_PRICING_AND_PROVISIONER.md`
  — unchanged this pass, no economy/SELL logic touched).

  --------------------------------------------------------------------------
  8a. REBOUND ABILITY/SPELL RADIAL CLOSES IMMEDIATELY — ROOT CAUSE AND FIX
  --------------------------------------------------------------------------
  MANUAL SYMPTOM: rebind Ability from Z to another key (e.g. M); Left
  Alt + M opens the Ability radial and it closes again immediately.
  Identical symptom for a rebound Spell key. Restoring the default Z/X
  keeps the radial open normally.

  ROOT CAUSE, CONFIRMED BY AUDIT: Section 7a's fix made the CHORD-OPENING
  decision (`registerAbilityKeybind`/`registerVeinminerKeyKeybind` in
  `TotalityKeybindHandlers`) correctly key-mapping-driven, but
  `AbilityRadialScreen.tick()`/`SpellRadialScreen.tick()` — the RADIAL
  SCREEN'S OWN release/close check, run every tick once the screen is
  already open — still polled raw `InputConstants.isKeyDown(window,
  GLFW_KEY_Z)`/`GLFW_KEY_X` directly, entirely independent of {@code
  ModKeybinds}. Once Ability was rebound to M, the literal-Z check read
  "not held" on the very first tick after the screen opened (M being
  held, not Z) — the exact behavior `tick()` uses to mean "the chord key
  was released, close the radial" — so the radial closed one tick after
  opening regardless of whether the actual (rebound) key was still held.
  At the DEFAULT binding this bug was invisible: the registered key
  mapping's default just happens to be the literal key the old code
  polled, so "is the key mapping down" and "is literal Z down" always
  agreed by coincidence — exactly matching Stefan's own diagnosis that
  restoring Z/X made the radials work again.

  FIX, ATTEMPT 1 (INCOMPLETE — see the follow-up below):
  `AbilityRadialScreen.tick()`/`SpellRadialScreen.tick()` were changed
  to read `ModKeybinds.USE_ABILITY.isDown()`/`ModKeybinds.USE_SPELL.isDown()`
  directly — the SAME registered key mapping reference that decided
  whether to open the chord in the first place. No GLFW key code,
  literal or otherwise, appeared anywhere in either screen's input
  handling any longer. This DID fix the reported symptom for a rebound
  key and was confirmed by Stefan's manual test immediately afterward
  ("Default Left Alt + Z/X radial behavior" — Section 7's closing
  manual-test note). While auditing the complete radial lifecycle per
  the task's explicit list, `GrimoireRadialScreen.tick()` was found to
  have the IDENTICAL bug shape (raw `GLFW_KEY_C`) and was fixed the
  same way as part of migrating Grimoire onto the modifier-chord model
  (Section 8b).

  SECOND REGRESSION, FOUND BY STEFAN'S VERY NEXT MANUAL TEST: after
  confirming the Grimoire chord itself now worked correctly with its
  default C/Left Alt, Stefan reported the radial-closes-immediately
  symptom was STILL happening — and now for the DEFAULT Ability/Spell
  keys too, not just rebound ones. Attempt 1 had traded one bug for a
  different, WORSE one that happened to be invisible in the specific
  manual sequence tested immediately after Attempt 1 landed.

  ROOT CAUSE OF THE REGRESSION, CONFIRMED BY DECOMPILED-BYTECODE
  INSPECTION: `Gui.setScreen(Screen)` — the exact method
  `client.gui.setScreen(...)` calls throughout this codebase —
  unconditionally calls the static `KeyMapping.releaseAll()` immediately
  before `Screen.init()`, for EVERY non-null screen it opens, with no
  exception for a screen opened from within the same tick as the chord
  press that triggered it. `KeyMapping.releaseAll()` zeroes the internal
  `isDown` boolean for EVERY registered key mapping in the game at that
  instant — Ability, Spell, Grimoire, and Radial Modifier all at once —
  regardless of whether the physical key is still actually held, and
  nothing re-asserts it while the key stays continuously down (a NEW
  GLFW press event is required to set it back to `true`, and a
  continuously-held key does not generate one). So the instant
  `AbilityRadialScreen` (or Spell's/Grimoire's) opens, `isDown()`
  becomes unreliable for every mapping in the game — Attempt 1's fix
  read exactly that now-unreliable value on the radial screen's very
  next `tick()`, closing it immediately regardless of binding. This was
  invisible in the manual test taken immediately after Attempt 1 landed
  only because that test's SPECIFIC sequence (Grimoire chord, then
  presumably re-confirming Ability/Spell defaults from muscle memory
  without a fresh, deliberate re-test) didn't happen to exercise the
  broken path before Stefan moved on — not because the bug was
  intermittent.

  Critically, this same corruption ALSO silently affected
  `TotalityKeybindHandlers`' OWN chord-tracking (`registerAbilityKeybind`,
  `registerVeinminerKeyKeybind`, `registerGrimoireKeybind`) — these are
  GLOBAL per-tick listeners that keep running for as long as the
  relevant key is held, including every tick AFTER a radial screen (or
  any other screen) has opened. Once `isDown()` reads corrupted, their
  own `abilityWasDown`/`spellWasDown`/`grimoireWasDown` edge-tracking
  and the channeled-ability hold/veinminer state in
  `registerVeinminerKeyKeybind` could desync from the real physical key
  state too — not just the radial screens' own release checks.

  FIX, FINAL: new `ModKeybinds.isPhysicallyDown(KeyMapping)` — reads
  REAL hardware/GLFW state directly, NEVER `KeyMapping.isDown()`.
  Resolves the mapping's CURRENT (rebind-aware) bound key via
  `KeyMapping.saveString()` round-tripped through
  `InputConstants.getKey(String)` — the exact same string round-trip
  vanilla's own options serialization already uses to save/load a
  rebound key — then polls `InputConstants.isKeyDown` for a
  keyboard-type binding or `GLFW.glfwGetMouseButton` for a mouse-type
  one. This is functionally what the ORIGINAL hardcoded-GLFW-literal
  polling always did (continuous real hardware state, immune to
  `KeyMapping.releaseAll()` since it never touches that internal
  bookkeeping at all) — just resolved against whichever key is
  CURRENTLY bound instead of a fixed literal, so it is simultaneously
  rebind-aware AND immune to the screen-open side effect. Applied
  EVERYWHERE a mapping's continuous down-state is read for chord/radial
  purposes: `AbilityRadialScreen.tick()`, `SpellRadialScreen.tick()`,
  `GrimoireRadialScreen.tick()`, and — because the corruption reached
  them too — `TotalityKeybindHandlers.registerAbilityKeybind`
  (Ability, Spell, AND Radial Modifier), `registerVeinminerKeyKeybind`
  (the channeled-ability/veinminer hold tracker), and
  `registerGrimoireKeybind` (Grimoire AND Radial Modifier). Chord-
  ownership semantics (press-edge decision, decided once, never
  re-evaluated) were untouched by either fix attempt — the bug was
  always in RETENTION/continuous-tracking, never in the opening
  decision itself.

  --------------------------------------------------------------------------
  8b. GRIMOIRE MOVED TO THE MODIFIER + GRIMOIRE-KEY CHORD MODEL
  --------------------------------------------------------------------------
  ROOT CAUSE OF THE REPORTED "V conflict": AUDITED AND NOT REPRODUCIBLE
  AS DESCRIBED. No code path in this codebase ever wired `GLFW_KEY_V`/
  `ModKeybinds.BLOCK` to the Grimoire radial — `GrimoireRadialScreen` was
  opened exclusively via `TotalityKeybindHandlers.registerGrimoireKeybind`'s
  OLD hold-C-for-`HOLD_THRESHOLD`-ticks trigger (confirmed by a full-file
  grep for `GLFW_KEY_V`/`GrimoireRadial`/`ModKeybinds.BLOCK`: exactly one
  site reads `GLFW_KEY_V`, `registerBlockKeybind`, and it has never
  referenced the Grimoire radial). This is recorded as an audit finding,
  not treated as evidence of a bug that needed a V-specific removal — V
  was already, and remains, exclusively Block's key.

  The REAL, actionable finding from the same audit: Grimoire's radial
  trigger was the one channel Ability/Spell's Section 2 migration (the
  original hold-N-ticks → modifier-chord model change) had NOT been
  applied to — it still used the OLD hold-C-for-`HOLD_THRESHOLD`-ticks
  gesture, a genuine input-model inconsistency with Ability/Spell (and,
  per Section 8a, carrying the identical rebind-breaks-retention bug
  once `ModKeybinds.OPEN_GRIMOIRE` — already registered, previously
  UNUSED — was wired in for rebinding at all).

  FIX: `TotalityKeybindHandlers.registerGrimoireKeybind` migrated onto
  the SAME modifier-chord model as `registerAbilityKeybind`, reusing the
  EXISTING `ModKeybinds.OPEN_GRIMOIRE` registration (default C, no
  second Grimoire key mapping created):
    - `ModKeybinds.OPEN_GRIMOIRE` alone → opens the ordinary Grimoire
      crafting/spell-construction screen on release (or the Class tab
      if Shift is held — a pre-existing, unrelated quirk left
      unchanged). There is no more continuous "hold to charge" gesture
      for the bare key — unlike a channeled Ability, the Grimoire screen
      has nothing to continuously charge toward, so any hold duration
      behaves the same as a quick tap always did.
    - `ModKeybinds.RADIAL_MODIFIER` + `ModKeybinds.OPEN_GRIMOIRE` →
      opens the Grimoire radial immediately, decided once at the press
      edge exactly like Ability/Spell, and suppresses the normal screen
      from opening on release.
  The now-fully-retired hold-threshold field (`grimoireHoldTicks`) was
  removed; `HOLD_THRESHOLD` itself (still referenced in Ability/Spell's
  own doc comments describing the OLD, already-superseded model) has no
  remaining reader and was deleted as dead code.
  `GrimoireRadialScreen.tick()` — and, per Section 8a's follow-up fix,
  `registerGrimoireKeybind`'s own Grimoire/Radial-Modifier down-state
  tracking too — now use `ModKeybinds.isPhysicallyDown(...)` rather
  than raw `GLFW_KEY_C` or plain `.isDown()`, so a rebound Grimoire key's
  radial retains correctly, immune to the `KeyMapping.releaseAll()`
  side effect Section 8a's follow-up documents.

  V remains untouched, exclusively `ModKeybinds.BLOCK`'s key
  (`registerBlockKeybind`, unmodified this pass).

  --------------------------------------------------------------------------
  8c. VERIFICATION
  --------------------------------------------------------------------------
  `KeybindVerification` extended from 8 to 12 checks: 4 new — Grimoire
  key mapping registered under the Totality category; defaults to C;
  Block's default (V) is distinct from Grimoire's default (C); Grimoire
  and Block each use distinct translation keys from each other and from
  Ability/Spell/Radial Modifier. These are registration-level
  confirmations only (the same category of check the suite already used
  for Ability/Spell/Radial Modifier) — deliberately NOT extended to call
  `ModKeybinds.isPhysicallyDown` itself: this suite runs from `TotalityClient
  .onInitializeClient()`, before a `Window` is guaranteed to exist yet,
  and `isPhysicallyDown` requires one. The actual tick-by-tick radial
  RETENTION behavior across a rebind (Section 8a/8b's real fix, and the
  regression Section 8a's follow-up describes) requires driving real
  keyboard input against a live client window either way, which this
  environment has no tool to do, and remains Stefan's manual checklist.

  RUNTIME-VERIFIED across three consecutive client boots this same day
  (2026-07-17, `gradlew runClient --args="--quickPlaySingleplayer \"New
  Testing World\""`): the first boot verified Attempt 1's fix (Section
  8a) — `[KeybindVerification] All 8 self-test checks passed.` (was 3),
  all other suites unchanged; the second boot verified Attempt 1 PLUS
  the Grimoire chord migration (Section 8b) — `[KeybindVerification] All
  12 self-test checks passed.` (was 8); the third boot verified the
  FINAL `isPhysicallyDown`-based fix (Section 8a's follow-up) —
  `[KeybindVerification] All 12 self-test checks passed.` again (no
  further registration-level checks changed by the follow-up, since it
  changed HOW down-state is read, not what's registered). Every other
  suite passed unchanged, first attempt, on all three boots: ItemValue
  19, MerchantSell 43, Provisioner 71, TradingScreen 22, PowerAttack 12,
  ProvisionerRenderer 4, NotificationTiming 9, PowerAttackFlash 7,
  ProvisionerEntityBackedSmokeTest 4 — 203 checks total across all 10
  suites, zero failures, on each of the three boots. Zero unexpected
  `WARN`/`ERROR` from the `totality` namespace (the only `ERROR` lines
  present are `ProvisionerVerification`'s own known, unchanged synthetic
  negative-test scenarios). `gradlew compileJava`/`build`: SUCCESSFUL
  after each of the three code changes.

  --------------------------------------------------------------------------
  8d. PVP / TEAM-FRIENDLY-FIRE MANUAL-TEST STATUS
  --------------------------------------------------------------------------
  Stefan could not manually test PvP-disabled or friendly-fire-disabled
  Power Attacks — he currently lacks a second account/player to set up
  either scenario. This is recorded as MANUALLY DEFERRED, not failed and
  not passed. It is explicitly NOT treated as a Phase 4 blocker —
  multiplayer is not a current development priority. The automated
  server-side legality checks added in Section 7c
  (`PowerAttackManager.isAttackerLegal`, and its dedicated
  `PowerAttackVerification` coverage — invulnerability, PvP-rule-disabled,
  and the "no blanket restriction against neutral/allied mobs" proof)
  were NOT removed or weakened on account of the missing manual
  coverage; they remain exactly as implemented and verified in Section
  7c. Team-friendly-fire specifically still has no automated
  coverage either, for the same `TotalityFakePlayer.canHarmPlayer()`
  fixture-limitation reason documented in Section 7c's `PowerAttackVerification`
  note — unchanged this pass.

  --------------------------------------------------------------------------
  8e. OUT OF SCOPE / EXPLICITLY UNTOUCHED (this pass)
  --------------------------------------------------------------------------
  Trading Screen layout, underfunded-SELL calculations, the confirmation
  protocol, structured rejection reasons, accountless/account-holder
  payout behavior, merchant Credits, BUY behavior, Provisioner
  persistence, Power Attack targeting/Stamina legality (Section 7,
  confirmed working and untouched), Notification timing, Power Attack
  flash timing, the Radial Modifier default, Blocking mechanics beyond
  confirming (not changing) V's exclusivity, and the MC 26.2 migration
  history — none of these were modified. No dialogue-check XP, quest
  skill XP, disposal, gifting, or donation behavior was implemented.

  MANUAL TESTING: PENDING — Stefan's full checklist (rebind Ability to
  M, confirm normal activation, old Z stops activating, Modifier+M opens
  and RETAINS the Ability radial without activating, select/cancel/
  reopen, channeled-Ability hold/release on M; same for Spell on a
  second rebound key; Grimoire: default C opens the normal screen,
  Modifier+C opens and retains the radial without also opening the
  normal screen, V does not open the Grimoire radial, V still Blocks,
  rebind Grimoire and repeat; regression: default Z/X, all three radial
  selections, Trading Screen underfunded flow, mainhand/offhand Power
  Attack). Not claimed passed until Stefan reports.
