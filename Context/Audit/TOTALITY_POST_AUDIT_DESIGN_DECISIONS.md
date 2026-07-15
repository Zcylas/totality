TOTALITY — POST-AUDIT DESIGN DECISIONS

STATUS: These are Stefan's CURRENT CANONICAL DECISIONS, made in direct
response to TOTALITY_IMPLEMENTATION_AUDIT_REPORT.md (dated 2026-07-12,
read-only, preserved UNCHANGED as a historical snapshot — this
document does not modify or supersede the audit report itself, it
records what was decided AFTER reading it).

Where a decision below resolves a specific open question from the
audit report's Section G, that question is cited by its audit ID
(e.g. RPG-CLASS-01, PHONE-04, ECON-13) so the two documents stay
cross-referenceable.

This document records DECISIONS. It does not invent unresolved
mechanics, formulas, or balance values that weren't actually decided —
anything still open is marked TBD, consistent with every other
document in this design corpus.

================================================================================
1. SPELL ACCESS AND CLASS/SPECIES FEATURES
================================================================================
Resolves audit RPG-CLASS-01 (Section G, Q1).

DECISION: the current state where every spell is marked
`isDefault()=true` (every player can cast every registered spell
regardless of class) MAY REMAIN temporarily for development testing.
It is explicitly NOT the final design.

The final implementation MUST include complete class and species/
origin feature granting, including:
  - Class-specific spell access.
  - Species/origin spell and ability grants.
  - Known/prepared spell rules where appropriate.
  - Warlock Pact Magic.
  - Monk Ki and other class resources.
  - Class-specific Rest recovery.
  - Subclass and covenant feature grants.

Temporary universal spell access should EVENTUALLY be replaced or
isolated behind an explicit development-only mechanism (e.g. a debug
flag/mode), not left as the shipped default indefinitely.

================================================================================
2. CLASS IMPLEMENTATION ARCHITECTURE
================================================================================
Resolves audit RPG-CLASS-03 (Section G, Q5).

DECISION: each class has its OWN implementation class — e.g.
`BarbarianClass`, `WizardClass`, `WarlockClass`, `MonkClass`.

These class implementations author and grant their OWN:
  - Starting features.
  - Level-based features.
  - Spell access.
  - Class resources.
  - Subclass timing.
  - Passive modifiers.
  - Rest interactions.

Shared registries/components MAY support these classes, but the CLASS
IMPLEMENTATION CLASSES are the authoritative organizers of class
progression — not a shared generic registry alone.

REQUIRED CLEANUP (resolves audit RPG-CLASS-03's specific coexistence
problem — `ClassData.startingAbilities`/`SubclassData.startingAbilities`
/`CovenantData.grantedAbilities` currently coexist with
`ClassFeatureRegistry`, with nothing reading the former and only
Barbarian using the latter): resolve or remove these unused competing
fields once the per-class implementation plan above is actually built.
Do not leave two non-interoperating mechanisms coexisting indefinitely.

================================================================================
3. LONG REST TIME AND MULTIPLAYER CONSENSUS
================================================================================
Resolves audit REST-08 (Section G, Q3).

DECISION: a Long Rest ALWAYS represents exactly EIGHT MINECRAFT HOURS.
It does not simply advance to the next morning. This applies to BOTH
bed and non-bed Long Rests — the audit found a "fake" (non-bed) Long
Rest currently advancing the shared world clock, which conflicted with
the Rest document's "must not force a global time skip" goal; this
decision replaces that conflict with an explicit, precise rule.

--------------------------------------------------------------------------------
3a. Singleplayer
--------------------------------------------------------------------------------
  Completing a valid Long Rest advances the world clock by exactly
  EIGHT Minecraft hours.

--------------------------------------------------------------------------------
3b. Multiplayer
--------------------------------------------------------------------------------
  When multiple eligible players are online:
    - Long Rest uses a vanilla-style sleep-consensus PROMPT.
    - At least 50% of eligible online players must be sleeping or Long
      Resting for the SHARED world clock to advance.
    - When the threshold IS reached, the clock advances exactly eight
      Minecraft hours.
    - When the threshold is NOT reached, an individual player can
      STILL finish their own Long Rest by remaining in the Rest state
      for the full eight Minecraft hours — 8,000 ticks, approximately
      6 minutes 40 seconds of real time.
    - ONE PLAYER MUST NEVER independently advance the shared
      multiplayer clock without reaching the threshold — this is the
      locked rule that directly resolves the audit's REST-08 conflict.

  Later, Fatigue/Rest Need (TOTALITY_REST_AND_FATIGUE.txt Section 10)
  will prevent unrestricted repeated Long Rest recovery, but the
  eight-hour D&D-style duration itself remains canonical regardless of
  how Fatigue eventually gates access to resting.

================================================================================
4. SHORT REST COUNT PERSISTENCE
================================================================================
Resolves audit REST-05 (Section G, Q4 — "Short Rest cap persistence").

STATUS (2026-07-13): IMPLEMENTED AND CLOSED. `RestStateComponent` +
`RestComponents` (new files, `api/rpg/rest/`) now hold
`shortRestsUsedSinceLongRest` as a real persisted component, wired
through the existing player-component framework
(`PlayerComponentEvents.registerForPlayers` + `RespawnStrategy.
ALWAYS_COPY`). `RestManager` reads/writes through this component
instead of its old in-memory `HashMap<UUID,Integer>`, which was
deleted along with the disconnect-time reset call in
`PlayerConnectionEvents` (the exact exploit path this decision closes).
Verified by playtest: persists through logout/reconnect, server
restart, death/respawn, and dimension changes; resets only on a valid
completed Long Rest, not on cancel/interrupt or relog; clamped to 0..2.

DECISION: the limit remains TWO completed Short Rests per valid Long
Rest (unchanged). `shortRestsUsedSinceLongRest` MUST PERSIST through:
  - Logout and reconnect.
  - Death and respawn.
  - Dimension changes.
  - Server restarts.

It resets ONLY after a valid restorative Long Rest completes — NOT on
relog (closing the exploit the audit identified: a player currently
can relog to reset the counter without ever completing a Long Rest).

The stored value can be clamped to 0..2. Dream-session state (Rest and
Fatigue doc Section 3) is SEPARATE and does not need to use this value.

================================================================================
5. MERCHANT INTERACTIONS
================================================================================
Resolves audit ECON-13 (Section G, Q6).

STATUS (2026-07-13): IMPLEMENTED AND CLOSED. New `OpenShopAction`
(`api/dialogue/actions/`) registered in `DialogueAction.CODEC` as
`"open_shop"`; `TotalityNpcEntity.mobInteract`'s direct-shopId bypass
removed outright, so ordinary right-click only ever starts Dialogue.
`example_trader.json`'s trade choice now fires the action instead of
dead-ending on flavor text. Verified by playtest: Interact -> Dialogue
-> "I'd like to trade." -> Trading screen, with no direct-shopId path
remaining. A same-day follow-on regression (the NPC resumed wandering
while Trading was open, because Dialogue's own end-state was
unconditionally releasing the interaction lock Trading had just taken)
was found and fixed via a reference-counted lock shared by
`DialogueSessionManager`/`TradeSessionManager`. STATUS: CLOSED /
PLAYTEST-VERIFIED — 2026-07-13. NPC stays stationary and facing the
player through the full Dialogue -> Trade handoff, Esc/Cancel releases
the lock correctly, and normal AI resumes afterward. Banker (which
overrides `mobInteract` entirely and never used
`shopId`) is unaffected. Scope explicitly excluded from this pass:
Relationship API, price modifiers, mood/reputation gating (see
TOTALITY_DIALOGUE_RELATIONSHIP_DESIGN.md) — this only builds the bridge
those hook into later.

DECISION: ALL NPC trading MUST begin through Dialogue. The audit found
`TotalityNpcEntity.shopId` currently bypassing Dialogue entirely on
right-click — this decision replaces that bypass with a mandatory flow:

  Interact with NPC -> Dialogue -> relationship/context-aware trade
  choice -> Open Shop action -> Trading screen

NPCs may reference a `shopId`, but ORDINARY INTERACTION must NOT
bypass Dialogue to reach it directly.

ADD (or design) a Dialogue action equivalent to:

  OpenShopAction(shopId)

This allows trading choices to later depend on: relationship,
reputation, quests, NPC mood, time and location, skill checks, and
whether the NPC currently wishes to trade at all.

RELATIONSHIP SYSTEM (new scope, not previously documented anywhere in
this design corpus): all important NPCs will support Dialogue AND some
form of Relationship progression. Relationship CEILINGS differ by
NPC — some may only ever become friends, while others may support
deeper bonds such as Brother/Sister-equivalent bonds, romantic
partners, or spouses. See the new
TOTALITY_DIALOGUE_RELATIONSHIP_DESIGN.md companion document for the
fuller (still largely TBD) design space this decision opens up.

================================================================================
6. PHONE MODEL AND HARDWARE TIER
================================================================================
Resolves audit PHONE-04 (Section G, Q7).

DECISION: Phone MODEL and HARDWARE TIER/FRAME are SEPARATE CONCEPTS —
this corrects the shipped implementation, which the audit found had
literally named the item "Basic Copper Phone" (baking tier into
identity, with tier as a compile-time-fixed field), exactly the
mistake the Phone document had explicitly warned against.

  Example of the corrected model:
    Model: Basic Phone
    Hardware tier: Copper
    Setup state: Complete

  Hardware progression (unchanged from prior design): Copper -> Iron
  -> Gold -> Diamond -> Netherite.

  NEW: there will EVENTUALLY be phone MODELS beyond Basic. A high-end
  model may be something like a "Dimensional Phone," though exact
  model names and progression are NOT YET LOCKED — this is a genuinely
  new axis (model) layered on top of the existing tier axis, not
  previously scoped in the Phone Platform document.

  Phone state should conceptually preserve: model, hardware tier,
  setup completion, unlocked apps, favorites and user configuration.

  Tier AND model upgrades should preserve compatible state. Do NOT
  permanently hardcode "Basic Copper Phone" as the only item identity,
  and do not assume every future Phone remains "Basic" — the model
  axis is meant to actually vary later.

================================================================================
7. CHARACTER CREATION ATTRIBUTES
================================================================================
Resolves audit RPG-ATTR-03 (Section G, Q8) — CONFIRMS this is still a
planned near-term feature, not abandoned in favor of the current flat-
10-start-plus-level-up-only shipped behavior. Also UPGRADES the
Balance document's Topic 1 numbers from "scaled starting points for
playtesting" to DEFINITE, while adding new persistence rules not
previously specified.

DECISION: character creation will DEFINITELY provide EXACTLY TWO
attribute-generation methods.

--------------------------------------------------------------------------------
7a. Point Buy
--------------------------------------------------------------------------------
  All eight attributes begin at 8. Budget: 36 points. Pre-Origin
  maximum: 15. Standard escalating D&D point-buy costs (unchanged from
  Balance doc Topic 1.1):

    Score | Cost
    8     | 0
    9     | 1
    10    | 2
    11    | 3
    12    | 4
    13    | 5
    14    | 7
    15    | 9

--------------------------------------------------------------------------------
7b. Rolling
--------------------------------------------------------------------------------
  Roll 4d6, discard the lowest die. Generate EIGHT scores. The player
  may assign the scores FREELY to the eight attributes. Add the eight
  generated scores together. If the total is BELOW 93, reroll the
  ENTIRE set. The FIRST set whose total is AT LEAST 93 is automatically
  accepted and LOCKED. Once a qualifying set appears, the player
  CANNOT reroll again (this is a NEW clarification — the Balance
  document's Topic 1.1 established the 93 threshold and the reroll
  mechanic, but didn't specify that acceptance is automatic-and-final
  the moment a qualifying set appears, rather than optional).

--------------------------------------------------------------------------------
7c. Persistence (NEW — not previously specified anywhere)
--------------------------------------------------------------------------------
  PERSIST ONLY:
    - The final assigned base scores.
    - `AttributeGenerationMethod.POINT_BUY` or
      `AttributeGenerationMethod.ROLLING`.

  DO NOT PERSIST:
    - Individual dice.
    - Rejected roll sets.
    - Reroll history.
    - Point-buy purchase history.

  Species/Origin bonuses apply AFTER score assignment (unchanged from
  Balance doc Topic 1.2's already-established species-first-bonus-
  applied-last ordering).

  NO server configuration for disabling either method is currently
  needed. General configuration support is EXTREMELY LOW PRIORITY and
  may be added near the end of development, not now.

================================================================================
8. ITEM RARITY CLARIFICATION
================================================================================
NEW scope — not a direct answer to an audit question, but a
clarification that affects how several systems (Carpentry, Farming,
Fishing, Cooking) should read/write ItemRarity going forward.

DECISION: `ItemRarity` is PRIMARILY authored or stack-assigned
PRESENTATION metadata. It controls: tooltip/name color, name
animation, visible item classification.

It is NOT a universal mechanical-quality ladder, and does NOT need
eligibility rules or automatic rarity calculation.

Different items use whichever rarity descriptor fits them — examples:
  - Incense can be UNCOMMON.
  - A Basic Phone with Copper hardware tier can be CRUDE.
  - Crops produced from particular seeds may inherit or receive
    different rarities.
  - Furniture, Farming, Fishing, Cooking, loot, and other systems MAY
    copy or override a `RarityComponent` on resulting stacks.

MECHANICAL QUALITY REMAINS SEPARATE (this is the important boundary):
  - Carpentry uses WORKMANSHIP.
  - Farming/Fishing/Husbandry products use PRODUCT GRADE (Food
    Ecosystem doc Section 6a).
  - Cooking uses DISH QUALITY (Food Ecosystem doc Section 17).
  - Current condition and freshness remain separate (Food Ecosystem
    doc Section 6d).

Example: a Flawless Oak Chair can have ANY authored or inherited
rarity appropriate to the item — Flawless WORKMANSHIP itself does NOT
automatically assign MASTERWORK, RARE, or any other rarity. The two
axes (rarity = presentation, workmanship/grade/quality = mechanical)
are independent.

See TOTALITY_SHARED_CROSS_SYSTEM_FOUNDATIONS.txt for where this
clarification has been folded into the shared documentation, since
Rarity is a cross-cutting concept multiple systems touch.

================================================================================
9. REVISED DESIGN AND IMPLEMENTATION PRIORITIES
================================================================================
The audit confirmed that Totality should NOT immediately attempt to
implement every missing feature. Work should proceed in DEPENDENCY
ORDER so new systems don't build on incomplete or conflicting
foundations.

THIS SEQUENCE IS A CURRENT PLANNING PRIORITY, NOT A STATEMENT THAT
EVERY LISTED SYSTEM IS ALREADY FULLY DESIGNED. Several Phase 3 items
are themselves still largely undesigned APIs, listed here as
DEPENDENCIES TO DESIGN/ADOPT, not as already-complete specifications.

--------------------------------------------------------------------------------
9a. Phase 1 — Resolve immediate implementation conflicts
--------------------------------------------------------------------------------
  - Persist `shortRestsUsedSinceLongRest` (Section 4).
  - Correct multiplayer Long Rest consensus and eight-hour time
    handling (Section 3).
  - Route all merchant interaction through Dialogue (Section 5) — this
    only needs a MINIMAL `OpenShopAction` sufficient to open a shopId
    from a dialogue choice; the broader Dialogue Action API (other
    system-opening actions, full server-side validation) stays a
    Phase 3 dependency (Section 9c) and is not required to close this
    specific conflict.
  - Resolve the Phone model/tier representation before adding more
    phone progression (Section 6).
  - Replace universal spell access once the real class/species
    feature-granting system is ready (Section 1).
  - Resolve the ungated Character-screen shortcut (audit PHONE-03 —
    Shift+C `CharacterScreen` currently doesn't require an equipped
    phone, unlike the TAB path) if the Phone is intended to be
    mandatory for ALL normal access paths.

--------------------------------------------------------------------------------
9b. Phase 2A — Immediate shared RPG foundations
--------------------------------------------------------------------------------
CORRECTION: the Generic Player Resource API and Unlock/Access/
Entitlement API were originally placed in Phase 3 while the RPG
features that DEPEND on them (Warlock Pact Magic, Monk Ki, Stamina
Rest recovery) were placed in Phase 2 — internally inconsistent with
this whole section's own "dependency order" philosophy. These two
shared APIs move here, ahead of the features that need them:

  - Generic Player Resource API: Mana, Stamina, Thirst, Rest Need,
    Sanity, Ki, Pact Magic or other class resources where appropriate,
    special species resources. STATUS (2026-07-13): DESIGN CLOSED /
    CANONICAL / IMPLEMENTATION-READY — see
    `TOTALITY_GENERIC_PLAYER_RESOURCE_API.md`. Not yet implemented; its
    next action is implementation/migration per that document's own
    phased plan, not a further design pass.
  - Unlock, Access and Entitlement API: Known, Owned, Unlocked,
    Visible, Currently available, Temporarily granted. STATUS
    (2026-07-13): DESIGN CLOSED / CANONICAL / IMPLEMENTATION-READY —
    see `TOTALITY_UNLOCK_ACCESS_AND_ENTITLEMENT_API.md`. Not yet
    implemented; its next action is implementation/migration per that
    document's own phased plan, not a further design pass.
  - Per-class implementation architecture (Section 2) — the
    `BarbarianClass`/`WizardClass`/etc. pattern itself, since the
    Phase 2B features below (Pact Magic, Ki, spell/ability grants) are
    meant to be authored THROUGH this pattern, not built ahead of it.

--------------------------------------------------------------------------------
9b-2. Phase 2B — Complete RPG features
--------------------------------------------------------------------------------
  These build ON TOP of Phase 2A's shared foundations:

  - Warlock Pact Magic.
  - Monk Ki.
  - Class/species spell and ability grants (subclass and covenant
    feature grants included).
  - Known/prepared spell rules where required.
  - Stamina Rest recovery.
  - HP/Hit Dice recovery (audit: currently ZERO implementation — no
    RestListener touches health at all).
  - Character Creation: Point Buy, Rolling, final assignment and
    persistence (Section 7).
  - Skills and Masteries UI (audit: largest player-facing gap found in
    the entire audit — currently a hardcoded stub, and masteries are
    authored for only 3 of 39 skills).

--------------------------------------------------------------------------------
9c. Phase 3 — Remaining shared APIs required by multiple future systems
--------------------------------------------------------------------------------
  Design and ADOPT these before building systems that depend on them
  (these are DEPENDENCIES TO DESIGN, largely not yet designed). The
  Generic Player Resource API and Unlock/Access/Entitlement API moved
  to Phase 2A above — everything else originally listed here stays:

  ECONOMY TRANSACTION AND PAYMENT API: physical/account payment
    sources, validation, atomic commit, rollback, idempotency, ledger/
    history, refunds (extends the transaction-service concept already
    proposed in TOTALITY_ECONOMY_BANKING_TRADING.txt Section 6).
  ITEM VALUE AND MERCHANT RUNTIME API: base values, sell pricing,
    stock, restocking, merchant Credit pool, buyback, investment
    (Economy doc Sections 3, 7, 8).

  NOTE (2026-07-13, reconciled against TOTALITY_ECONOMY_PRICING_AND_
  PROVISIONER.md Section 11): this Phase 3 list is a DESIGN-DEPENDENCY
  listing, not a strict build-order mandate for the first Provisioner.
  The first working SELL/Provisioner does NOT need the full Economy
  Transaction API's ledger/rollback/idempotency/refund-history feature
  set completed first — only a minimal, purpose-built atomic merchant
  transaction contract (validate fully before commit, atomic exchange
  of item and Credits, no partial mutation on either side) is required,
  a strict subset of the eventual full API that can be extended later
  without being redone. See that document's Section 11 for the
  concrete, reconciled practical build order (Item Value ->
  context-aware pricing -> minimal SELL transaction contract -> SELL ->
  Provisioner).
  DIALOGUE ACTION API ADDITIONS (BROADER SYSTEM — the minimal
    `OpenShopAction` needed to close the P1 merchant-routing conflict
    is already covered at Phase 1, Section 9a, and already validates
    server-side for its own case — authored-only `shopId`, session-
    tracked NPC via `DialogueSessionManager.getActiveNpc`, `ShopRegistry`
    lookup validation; see TOTALITY_DIALOGUE_RELATIONSHIP_DESIGN.md
    Section 1a's 2026-07-13 clarification for the exact code path):
    other system-opening actions, and a GENERALIZED reusable server-
    side validation FRAMEWORK for those future actions (Section 5) —
    not a claim that the current shop-opening flow lacks validation.
  RELATIONSHIP API: NPC-specific relationship ceilings, friendship,
    deep familial-style bonds, romance, spouse, relationship-aware
    dialogue and trading (Section 5, new
    TOTALITY_DIALOGUE_RELATIONSHIP_DESIGN.md).
  REST SESSION AND ELIGIBILITY API REFINEMENTS: eight-hour completion,
    multiplayer consensus, recovery qualification, comfort input,
    future Fatigue integration (Sections 3-4, Rest/Fatigue doc).
  PHONE MODEL AND TIER STATE API: phone model, hardware tier/frame,
    setup state, apps, favorites, upgrade-state preservation
    (Section 6, Phone Platform doc).
  MAIL, DELIVERY AND CLAIM API: messages, packages, offline delivery,
    claim-once behavior, refund-safe delivery (Phone Platform doc
    Section 6).
  DISEASE LIFECYCLE API: exposure, infection, incubation, stages,
    symptoms, treatment, cure, intervention integration (Food
    Ecosystem doc Section 13f, Disease/Spell Intervention doc).

--------------------------------------------------------------------------------
9d. Phase 4 — Complete currently visible but incomplete systems
--------------------------------------------------------------------------------
  Functional merchant SELL path; Buyback; account tiers; ATM;
  Provisioner and first trading quest; Phone Store; Mail; remaining
  Phone apps; Comfort and Well Rested behavior; Outdoor Rest Events.

--------------------------------------------------------------------------------
9e. Phase 5 — New profession and world systems
--------------------------------------------------------------------------------
  After their shared dependencies (Phase 3) are stable: Carpentry
  (Section 10), Home and Comfort (Section 10), Farming/Cultivation,
  Fishing, Processing, Cooking, Diet, Survival, Husbandry, Carcass
  Processing, Disease content, Dream and Sanity systems.

================================================================================
10. CARPENTRY AND HOME/COMFORT DESIGN DIRECTION
================================================================================
NEW scope. This is DIRECTIONAL GUIDANCE for a future dedicated
Carpentry + Home/Comfort design document — NOT the full design itself.
The full design happens in a later, separate session.

--------------------------------------------------------------------------------
10a. Two connected but separate systems
--------------------------------------------------------------------------------
  CARPENTRY OWNS: furniture and household-object crafting, blueprints,
    material requirements, workmanship, Carpentry XP and masteries,
    repair/restoration/refinishing, furniture output data.

  HOME/COMFORT OWNS: rooms and homes, furniture contribution
    evaluation, comfort categories, resident capacity, duplicate
    limits and diminishing returns, accessibility, shelter integration
    (reads the Food Ecosystem doc's ShelterEnvironmentContext, Section
    5a — same shared view Survival and Rest already use), Rest bonuses
    (Rest/Fatigue doc Section 7, Comfort), future relationship/
    visitor/Sanity effects.

  CANONICAL RULE: Carpentry determines HOW WELL a furniture piece was
  made. Home/Comfort determines HOW USEFUL that furniture is INSIDE a
  living space. These are genuinely separate questions — a piece can
  be expertly made (high workmanship) but poorly suited to a specific
  room's comfort needs, or vice versa.

--------------------------------------------------------------------------------
10b. Furniture identity
--------------------------------------------------------------------------------
  Furniture should conceptually combine: furniture type, material,
  workmanship, current condition, optional upholstery/finish/traits,
  existing `RarityComponent` presentation metadata (Section 8).

  Example: "Flawless Oak Chair" — its COMFORT comes from furniture
  type, material suitability, workmanship, and condition — NOT
  directly from ItemRarity (consistent with Section 8's rarity-is-
  presentation-only rule).

--------------------------------------------------------------------------------
10c. Rarity propagation (consistent with Section 8)
--------------------------------------------------------------------------------
  ItemRarity remains shared tooltip/name presentation metadata. It can
  be: authored on the registered item, copied from an input, assigned
  dynamically to an output stack, inherited from seeds/blueprints/
  special source items where content defines that behavior.

  Carpentry does NOT automatically calculate rarity from workmanship.

  Farming may similarly propagate rarity: Seed -> planted crop ->
  harvested produce. Rarity remains separate from Product Grade,
  freshness, genetics, and harvest quality throughout that chain.

================================================================================
11. PRIORITY / STATUS TABLE
================================================================================
  Priority | System                         | Status              | Blocking dependencies              | Next action
  ---------|--------------------------------|----------------------|-------------------------------------|------------------------------------------
  P1       | Short Rest persistence          | CLOSED (2026-07-13)  | None                                | Done — RestStateComponent persists shortRestsUsedSinceLongRest (Sec 4); verified by playtest
  P1       | Long Rest timing/consensus      | Conflict found       | None                                | Implement 8hr + 50% consensus rule (Sec 3)
  P1       | Merchant/Dialogue routing       | CLOSED (2026-07-13)  | None                                | Done — direct-shopId bypass removed, routes through OpenShopAction (Sec 5); verified by playtest
  P1       | Minimal OpenShopAction          | CLOSED (2026-07-13)  | None                                 | Done — OpenShopAction implemented + wired; full Dialogue Action API (server-side validation, other system-opening actions) stays P3
  P1       | Interaction-lock regression     | CLOSED / PLAYTEST-VERIFIED (2026-07-13) | None                    | Done — reference-counted lock shared by DialogueSessionManager/TradeSessionManager
  P1       | Phone model/tier separation     | Conflict found       | None                                | Split model from tier in item state (Sec 6)
  P1       | Universal spell access          | Known interim state  | Class Implementation Architecture   | Replace once Phase 2A/2B class system exists (Sec 1)
  P1       | Character-screen phone gating   | Inconsistency found  | None                                | Decide/apply same TAB gating to Shift+C
  P2A      | Generic Player Resource API     | DESIGN CLOSED / CANONICAL / IMPLEMENTATION-READY (2026-07-13, TOTALITY_GENERIC_PLAYER_RESOURCE_API.md) | None | Not yet implemented — build per that document's Phase/acceptance-test structure
  P2A      | Unlock/Access/Entitlement API   | DESIGN CLOSED / CANONICAL / IMPLEMENTATION-READY (2026-07-13, TOTALITY_UNLOCK_ACCESS_AND_ENTITLEMENT_API.md) | None | Not yet implemented — build per that document's Phase/acceptance-test structure
  P2A      | Class-specific feature arch.    | Partial (1/N classes)| None                                | Build per-class implementation classes (Sec 2)
  P2B      | Warlock Pact Magic              | Not implemented      | Generic Player Resource API (2A)    | Design + implement resource pool
  P2B      | Monk Ki + other class resources | Not implemented      | Generic Player Resource API (2A)    | Design + implement
  P2B      | Known/prepared spell rules      | Not implemented      | Class Implementation Architecture (2A)| Design + implement
  P2B      | HP/Hit Dice recovery            | Not implemented      | None                                | Highest-priority Rest gap, implement
  P2B      | Stamina Rest recovery           | Not implemented      | Generic Player Resource API (2A)    | Implement
  P2B      | Skills/Masteries UI             | Not implemented      | None                                | Largest player-facing gap, build UI
  P2B      | Character Creation flow         | Not implemented      | None                                | Implement Point Buy + Rolling (Sec 7)
  P3       | Economy Transaction API         | Partially proposed   | None                                | Finalize (extends Economy doc Sec 6)
  P3       | Item Value/Merchant Runtime API | Not implemented      | Economy Transaction API              | Design + implement
  P3       | Dialogue Action API (broader)   | Not designed         | Minimal OpenShopAction (P1) already exists | Design full system-opening action framework + server-side validation (Sec 5) — the minimal shop-opening piece is covered at P1
  P3       | Relationship API                | Not designed         | Dialogue Action API                  | Design (new companion doc)
  P3       | Rest Session/Eligibility refine | Partially designed   | None                                 | Add 8hr/consensus/comfort/Fatigue hooks
  P3       | Phone Model/Tier State API      | Conflict found       | None                                 | Design + implement (Sec 6)
  P3       | Mail/Delivery/Claim API         | Not implemented      | Economy Transaction API              | Design + implement
  P3       | Disease Lifecycle API           | Not designed         | Exposure (Shared Foundations doc)    | Design
  P4       | Merchant SELL path              | Stub, non-functional | Item Value/Merchant Runtime API      | Implement
  P4       | Buyback                         | Not implemented      | Item Value/Merchant Runtime API      | Implement
  P4       | Account tiers, ATM              | Not implemented      | Economy Transaction API              | Implement
  P4       | Provisioner + First Sale quest  | Not implemented      | Merchant SELL path                   | Implement
  P4       | Phone Store, Mail, remaining apps| Not implemented     | Mail/Delivery API, Economy Transaction API | Implement
  P4       | Comfort, Outdoor Rest Events    | Not implemented      | Rest Session refinements             | Implement
  P5       | Carpentry + Home/Comfort        | Directional only     | Rarity clarification (done, Sec 8)   | Full dedicated design doc, then implement
  P5       | Farming/Fishing/Processing/Cooking/Diet/Survival/Husbandry/Carcass/Disease content/Dream+Sanity | Designed (varying completeness) in Food Ecosystem + companion docs | Phase 3 shared APIs | Implement per Food Ecosystem build order once dependencies are stable

================================================================================
END OF DOCUMENT
================================================================================
