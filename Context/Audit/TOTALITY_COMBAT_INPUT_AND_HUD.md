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
