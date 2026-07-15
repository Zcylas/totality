TOTALITY — DIALOGUE / RELATIONSHIP DESIGN (INITIAL)
(New document — deliberately narrow in scope. Captures the merchant-
via-Dialogue decision from TOTALITY_POST_AUDIT_DESIGN_DECISIONS.md
Section 5, plus the Relationship system it opens up. This is NOT a
full Dialogue engine specification — the audit found dialogue/dice
systems already exist in some form in code; this document does not
attempt to describe that existing engine from memory, only the NEW
decisions layered on top of it.)

STATUS: Section 1 (merchant routing) is an ADOPTED decision, directly
resolving a code conflict the audit found — IMPLEMENTED AND CLOSED
2026-07-13, verified by playtest. Section 2 (Relationship) is largely
NEW SCOPE and mostly TBD — do not treat it as a complete design.

================================================================================
1. ALL MERCHANT TRADING ROUTES THROUGH DIALOGUE
================================================================================
Resolves audit ECON-13. STATUS (2026-07-13): CLOSED — see
TOTALITY_POST_AUDIT_DESIGN_DECISIONS.md Section 5 for the implementation
writeup. PRIOR STATE (confirmed via code audit, now fixed):
`TotalityNpcEntity.shopId` used to bypass Dialogue entirely on
right-click — trading opened directly, no dialogue turn happened first.

DECISION: ALL NPC trading MUST begin through Dialogue. Locked flow:

  Interact with NPC -> Dialogue -> relationship/context-aware trade
  choice -> Open Shop action -> Trading screen

NPCs may still REFERENCE a `shopId` (the underlying shop data/runtime
state doesn't change, per TOTALITY_ECONOMY_BANKING_TRADING.txt Section
7), but ORDINARY INTERACTION must not bypass Dialogue to reach it.

--------------------------------------------------------------------------------
1a. New Dialogue action needed
--------------------------------------------------------------------------------
  ADD (or design, if Dialogue's action system already supports a
  similar pattern that can be extended) an action equivalent to:

    OpenShopAction(shopId)

  This is what a dialogue CHOICE triggers when the player selects a
  trade-oriented line — it's the bridge between Dialogue's choice
  system and Economy's trading screen (TOTALITY_ECONOMY_BANKING_
  TRADING.txt Section 8).

  PRIORITY SPLIT (Post-Audit Decisions Section 9a/9c, Section 11 table):
  a MINIMAL version of this action — just enough to open a shopId from
  a dialogue choice — was the Phase 1/P1 dependency, since it's what
  actually closed the audit-confirmed direct-shopId-bypass conflict.
  STATUS: CLOSED 2026-07-13 (implemented, verified by playtest).

  CLARIFICATION (2026-07-13 correction pass, does not reopen ECON-13):
  "minimal" describes SCOPE (one action, one system it opens), not
  validation rigor — the actual `OpenShopAction` implementation
  (`api/dialogue/actions/OpenShopAction.java`) already validates
  server-side for the case it handles: `shopId` comes only from
  authored dialogue JSON, never from client-supplied state; `execute()`
  checks `DialogueSessionManager.isInDialogue(player)` before doing
  anything; the NPC it hands off to `TradeSessionManager.startTrade`
  comes from `DialogueSessionManager.getActiveNpc(player)` (server-
  tracked session state, not a client-supplied entity reference); and
  `TradeSessionManager.startTrade` itself validates `shopId` against
  `ShopRegistry` and fails safely (logs, no-op) if unknown. Do not
  read this action as "trusting client-supplied shop data" — it
  doesn't accept any. What remains Phase 3/P3 is a GENERALIZED,
  REUSABLE validation FRAMEWORK for arbitrary FUTURE system-opening
  actions beyond shops (so the next ten actions don't each hand-roll
  their own version of the checks `OpenShopAction` already does once,
  by hand, correctly, for this one case) — not a claim that today's
  shop-opening flow is currently insecure. ECON-13 and the interaction-
  lock regression (Section 1b below) both remain CLOSED /
  PLAYTEST-VERIFIED; this clarification changes no status, only
  wording precision.

--------------------------------------------------------------------------------
1b. Follow-on regression: interaction-lock sharing
--------------------------------------------------------------------------------
  Same-day regression surfaced by the Section 1 fix itself: Dialogue
  and Trading shared a single nullable `dialoguePartner` field on
  `TotalityNpcEntity`. The Dialogue -> Trade handoff (`OpenShopAction`
  starting Trading, then Dialogue immediately advancing to its "end"
  state) unconditionally cleared that field, so the NPC resumed
  wandering while the Trading screen was still open.

  FIX: reference-counted lock (`interactionLockCount` /
  `interactionPartner`, `acquireInteractionLock` /
  `releaseInteractionLock`) shared by `DialogueSessionManager` and
  `TradeSessionManager`. Handoff now goes 1 -> 2 -> 1 -> 0 instead of
  ever touching 0 while Trading is active. Also added a real client
  close notification (`CloseTradePayload` on Esc/Cancel) and a
  disconnect/death/despawn safety net that force-ends any in-progress
  session.

  STATUS: CLOSED / PLAYTEST-VERIFIED — 2026-07-13. NPC stays
  stationary and facing the player through the full handoff, Esc/Cancel
  releases the lock correctly, and normal AI resumes afterward.

--------------------------------------------------------------------------------
1c. Why routing through Dialogue matters (not just architectural
    purity)
--------------------------------------------------------------------------------
  This allows trading choices to later DEPEND ON:
    - Relationship (Section 2 below).
    - Reputation.
    - Quests.
    - NPC mood.
    - Time and location.
    - Skill checks.
    - Whether the NPC currently WISHES to trade at all (an NPC could
      refuse trade under certain conditions — angry, busy, doesn't
      trust the player yet).

  None of this is possible if trading bypasses Dialogue entirely, which
  is exactly why the direct-shopId-bypass the audit found is a real
  architectural regression, not just a style inconsistency.

================================================================================
2. RELATIONSHIP SYSTEM (NEW SCOPE — LARGELY TBD)
================================================================================
DECISION (the one locked piece): all important NPCs will support
Dialogue AND some form of Relationship progression.

RELATIONSHIP CEILINGS DIFFER BY NPC — this is the other locked piece:
some NPCs may only ever become friends (a relationship ceiling of
"Friend," never deeper); others may support DEEPER bonds such as:
  - Brother/Sister-equivalent bonds.
  - Romantic partners.
  - Spouses.

Relationship-aware Dialogue and trading (Section 1) are the two
CONFIRMED consumers of this system so far — an NPC's willingness to
trade, price adjustments, dialogue tone/options can all read from
Relationship state.

--------------------------------------------------------------------------------
2a. What's genuinely NOT decided yet (do not invent)
--------------------------------------------------------------------------------
  - Exact relationship SCALE/tiers (how many steps between "stranger"
    and an NPC's ceiling, what each tier is called).
  - How relationship VALUE increases/decreases (gifts? dialogue
    choices? quests completed? time spent? some combination?).
  - Whether relationship is a single numeric value per NPC or has
    sub-components (trust vs. affection vs. respect, etc.).
  - How an NPC's specific CEILING is authored (a flat per-NPC max
    value? a category the NPC belongs to — e.g. "shopkeeper" NPCs cap
    at Friend, "companion-eligible" NPCs can go further?).
  - Whether romance/spouse relationships have their own additional
    mechanics beyond a high relationship value (a proposal event? a
    dedicated ceremony? shared housing consequences tying into the
    future Home/Comfort system, Post-Audit Decisions Section 10?).
  - How Relationship interacts with the Follower system idea already
    noted in TOTALITY_FUTURE_IDEAS.txt (Followers and relationship
    progression) — these may be the same underlying system or
    related-but-distinct; not yet resolved.
  - Whether Relationship is per-NPC-instance or has any shared/
    reputation-adjacent component affecting multiple NPCs at once
    (e.g. a settlement-wide reputation separate from individual
    relationships).

--------------------------------------------------------------------------------
2b. Suggested (RECONSTRUCTION — PROPOSED, needs sign-off) minimal
    shape, to make Section 1's dependency concrete without over-
    designing the rest
--------------------------------------------------------------------------------
  public record RelationshipState(
          UUID npcId, int value, Identifier currentTier,
          Identifier ceilingTier
  ) {}

  public record NpcRelationshipProfile(
          Identifier npcId, Identifier ceilingTier,
          List<Identifier> availableTiers
  ) {}

  This is the SMALLEST possible shape that lets Dialogue/Trading
  (Section 1) actually read "what's my relationship with this NPC, and
  what's the ceiling" without committing to how value changes, what
  the full tier list looks like, or how romance/spousal mechanics
  work. Treat this as scaffolding to unblock Section 1's dependency,
  not as the final Relationship API.

================================================================================
3. OPEN ITEMS
================================================================================
  - The existing Dialogue engine itself (dialogue nodes, conditions,
    ability checks, success/failure actions, persistence) is NOT
    documented here — audit confirmed dialogue/dice systems already
    exist in some form in code; a proper description of that existing
    system should come from code audit, not be reconstructed here from
    memory.
  - Section 2's entire relationship mechanic (2a) — genuinely open,
    needs its own design pass.
  - How OpenShopAction's server-side validation should work exactly
    (Post-Audit Decisions Section 9c lists this as a Phase 3
    dependency).

================================================================================
END OF DOCUMENT
================================================================================
