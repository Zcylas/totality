TOTALITY — ECONOMY PRICING AND FIRST PROVISIONER
(New document. DOCUMENTATION-ONLY design pass, 2026-07-13. No
implementation code was written or changed while producing this
document — see the Implementation Order section for what comes next.)

STATUS: ADOPTED DECISIONS from Stefan's 2026-07-13 design pass,
reconciled against the actual current codebase and against
TOTALITY_ECONOMY_BANKING_TRADING.txt / TOTALITY_POST_AUDIT_DESIGN_
DECISIONS.md / TOTALITY_DIALOGUE_RELATIONSHIP_DESIGN.md. Sections
marked PLAYTEST VALUE are explicitly non-final balance numbers, not
locked design. Same ADOPTED/PROPOSED/TBD convention as every other
Totality design document.

Precedence: this document extends TOTALITY_ECONOMY_BANKING_TRADING.txt
Sections 7-9 (Item Values, First Ordinary Trader) with the concrete
Provisioner build. Where the two disagree, this document — being the
more recent, code-reconciled pass — wins; TOTALITY_ECONOMY_BANKING_
TRADING.txt should be treated as superseded on those specific points
(noted inline below) rather than silently contradicted.

================================================================================
0. RECONCILIATION SUMMARY (read this first)
================================================================================
Before writing the sections below, the actual codebase was audited.
Key findings that shape every decision here:

  - `ShopEntry` (`api/shop/ShopEntry.java`, `record(ItemStack stack,
    long price)`) and `ShopTemplate` (`api/shop/ShopTemplate.java`,
    `record(String name, List<ShopEntry> sells)`) are IMMUTABLE, JSON-
    authored, and SHARED across every NPC that references the same
    `shop_id` via `ShopRegistry`. There is currently no per-NPC runtime
    state at all — every Provisioner pointing at the same `shop_id`
    would show the IDENTICAL stock. This directly conflicts with
    Section 3's requirement that EACH individual Provisioner rolls and
    persists its OWN assortment. Reconciled in Section 3 below by
    introducing a new per-entity runtime layer rather than trying to
    force individual variation into the shared, immutable
    `ShopTemplate`.

  - No `ItemValue`/base-value/pricing registry exists anywhere in the
    codebase (confirmed by grep — the only hits were unrelated vanilla
    `AttributeInstance.getBaseValue()` calls). `TradingScreen`'s own
    code comment already says the quiet part out loud: "selling
    requires a item-value system that doesn't exist yet." Section 1
    below is genuinely new infrastructure, not a rename of something
    that already exists.

  - `WalletComponent` (`api/economy/currency/WalletComponent.java`) is
    hard-coupled to `ServerPlayer` in its constructor — it is a PLAYER
    account balance, not a general-purpose ledger. It CANNOT be reused
    as-is for a merchant's business Credits (Section 2). A merchant
    balance needs its own field, not a repurposed Wallet.

  - `CreditPaymentHelper.pay`/`canAfford` already implement the
    "spend Wallet first, then physical Credits" resolution the player
    side needs — this is reused unchanged for BUY. It does not yet
    know how to pay a player FROM a merchant's pool (SELL) — that's new
    logic in the same class, not a parallel payment helper.

  - CORRECTION PASS (2026-07-13): the approved Provisioner stock
    contains a Water Bottle (8 Credits) and a Potion of Healing (80
    Credits) — both `minecraft:potion`, distinguished only by the
    `DataComponents.POTION_CONTENTS` data component (confirmed in code,
    already used the same way in `InventoryEquipHelper`/
    `HotbarSlotMap`/`InventoryActionHandler`). A bare item-ID key
    cannot represent this. Both `ItemValueRegistry` (Section 1a) and
    `ProvisionerAssortmentPool` (Section 3a) are corrected to support
    full, component-aware `ItemStack` matching (reusing `ItemStack.
    CODEC`, the same codec `ShopEntry` already uses — no new
    potion-specific field), which also means both now need the SAME
    `RegistryOps`/`ServerLifecycleEvents.SERVER_STARTED` loading
    pattern `ShopRegistry` already uses, not the simpler pattern this
    document originally (incorrectly) proposed for them.

  - The "existing random name and skin/category system" referenced in
    project memory is, in actual code, a RANDOM GENDER + NAME system
    only: `NpcGender` (`MALE`/`FEMALE`) and `NpcNameRegistry` (loads
    `data/totality/npc_names/{male,female,neutral}.json`). **No
    separate "skin category" axis exists in code.** Section 8 below
    corrects the framing to match what's actually implemented — the
    Provisioner's "generic identity" is gender + name only, not a
    richer skin-category system that would need to be invented from
    scratch. If a broader appearance-variation axis is wanted later,
    that's new scope, not something this document should assume exists.

  - `BankerNpcEntity` (`entity/npc/BankerNpcEntity.java`) is the real
    precedent for "a dedicated NPC gets its own entity class": it's a
    THIN subclass of `TotalityNpcEntity` that overrides `mobInteract`
    only, no new persisted fields. The Provisioner (Section 8) follows
    the same shape, plus the new persisted runtime fields Section 2/3
    require (which Banker didn't need).

  - The Quest system (`api/quest/QuestManager.java` etc.) currently
    triggers objective completion via hardcoded per-quest static hooks
    called from specific gameplay events (e.g. `onPhoneEquipped`), not
    from a generic proximity/trigger system. Section 9's "approach a
    Provisioner" trigger is NEW plumbing in that same hardcoded-hook
    style, not an existing generic trigger this document can just
    reference.

================================================================================
1. CENTRAL PRICING
================================================================================
ADOPTED (new infrastructure — see Section 0, no prior art to extend).

  - Ordinary item prices belong to a central, shared item-value
    registry — NOT repeated per shop JSON entry. `ShopEntry.price`
    currently duplicates a price per shop; that field's MEANING
    changes under this design (Section 1d).
  - Value resolution is keyed by MORE than a bare item ID where
    needed: a plain item-ID rule is sufficient and remains convenient
    for ordinary items (bread, coal, planks, leather, tools), but some
    items share one item ID across economically distinct variants
    (Section 1a) — a more specific, component-aware rule must be able
    to win over the plain fallback for those.
  - Normal retail price = 100% of adjusted base value.
  - Normal player sale payout = 50% of adjusted base value. (Matches
    TOTALITY_ECONOMY_BANKING_TRADING.txt Section 7b, which already
    adopted this 50% figure — this document supplies the concrete
    registry that section didn't yet have.)
  - A merchant ACCEPTING an item (Section 5) is a separate question
    from the item HAVING a value — an item can have a base value and
    still be un-sellable to a given merchant.
  - Exceptional authored offers may override the normal price (e.g. a
    quest-specific discount, a unique item with no sensible base
    value). `ShopEntry.price`, reinterpreted, becomes this override
    slot rather than the only source of price.
  - `ItemRarity` (the mob/loot rarity tag) does NOT automatically
    affect economic value — rarity and price are deliberately
    decoupled axes.
  - Condition, quality, freshness, enchantment, trait, specialization,
    Relationship, reputation, and Persuasion modifiers must all modify
    the FINAL computed price without mutating the central base value —
    they are multipliers/adjustments applied at quote time, not writes
    to the registry.
  - All price calculations are server-authoritative — never trust a
    client-supplied price, matching the existing pattern in
    `TradeSessionManager.handleBuy` (server recomputes cost from
    `entry.price() * quantity`, never trusts client math).

--------------------------------------------------------------------------------
1a. Proposed shape — component-aware matching, most-specific wins
--------------------------------------------------------------------------------
  CORRECTED (2026-07-13 pass): a bare item-ID key is NOT sufficient.
  The approved Provisioner stock (Section 3/4) already contains a
  counterexample — a Water Bottle (8 Credits) and a Potion of Healing
  (80 Credits) are BOTH `minecraft:potion`, distinguished only by the
  `minecraft:potion_contents` data component (confirmed in code:
  `net.minecraft.core.component.DataComponents.POTION_CONTENTS`, used
  the same way in `InventoryEquipHelper`/`HotbarSlotMap`/
  `InventoryActionHandler` to tell potion stacks apart). This is not a
  potion-only quirk — enchanted books, filled containers, maps, and
  future custom/component-defined items all share this same "one item
  ID, several economically distinct variants" shape. Rather than
  invent a potion-specific exception, the registry supports two rule
  kinds from day one, with a defined precedence between them:

  PROPOSED — package `api/economy/value/` (sibling to the existing
  `api/economy/currency/`):

    public record ItemValueRule(
        Identifier itemId,
        Optional<ItemStack> componentMatch,   -- ABSENT = plain item-ID
                                                  fallback rule. PRESENT
                                                  = a PARTIAL, REQUIRED-
                                                  COMPONENT predicate,
                                                  authored as a
                                                  reference stack via
                                                  the SAME
                                                  `ItemStack.CODEC`
                                                  `ShopEntry` already
                                                  uses (no new codec
                                                  invented — Section 2
                                                  reuses this exact
                                                  type too).
        long baseValue
    ) {}

  CLARIFIED (2026-07-13, second correction pass) — exact
  `componentMatch` matcher semantics, stated precisely rather than by
  example:

  - The item ID must match (`stack.getItem()` equals the rule's
    `itemId`) — this check always applies, component-aware or not.
  - Every component EXPLICITLY AUTHORED on the reference stack must
    equal the real stack's value for that same component type. A
    Healing-Potion rule that only sets `POTION_CONTENTS` only checks
    `POTION_CONTENTS` — it is a PARTIAL predicate over the components
    it actually declares, not a full-stack equality check.
  - Any component the reference stack does NOT set is IGNORED on the
    real (target) stack — an unrelated component present on the real
    stack (custom name, other future components) never breaks a match
    it would otherwise satisfy.
  - Stack COUNT is ignored entirely by matching — `componentMatch` is a
    components-only predicate; a reference stack authored as count 1
    matches a real stack of any count.
  - A component-aware rule (`componentMatch` present) OUTRANKS a plain
    item-ID fallback rule (`componentMatch` absent) for the same
    `itemId` — most-specific wins, per Section 1.
  - SPECIFICITY IS THE NUMBER OF EXPLICITLY AUTHORED COMPONENT ENTRIES.
    Among every rule that matches a given real stack, the rule with
    MORE declared component entries wins — a rule declaring 2
    components beats one declaring only 1, which beats the plain
    item-ID fallback (0). This is what "most-specific wins" means
    precisely, and it is NOT an error for two DIFFERENTLY-specific
    rules to both be capable of matching the same real stack — that is
    the normal, expected, EXPECTED-TO-COEXIST case (e.g. a plain
    Healing-Potion rule and a more specific "named Healing Potion"
    rule can both legitimately match a custom-named healing potion;
    the more specific one simply wins).
  - AMBIGUITY IS SCOPED TO EQUALLY SPECIFIC RULES ONLY. Two rules for
    the same `itemId` can only conflict with each other if they
    declare the SAME NUMBER of components — a load-time error never
    applies across two rules of different specificity, only between
    ties. Within a tie (same declared-component count), two further
    cases are distinguished:
      - MUTUALLY EXCLUSIVE, NOT AMBIGUOUS, BOTH KEPT: the tied rules
        share at least one declared component key but require
        DIFFERENT values for it (e.g. Water Bottle and Potion of
        Healing both declare only `POTION_CONTENTS`, with different
        required potion types) — no single real stack could ever
        satisfy both simultaneously, so they never actually compete
        and both are kept.
      - AMBIGUOUS, LOAD-TIME ERROR, BOTH REJECTED: the tied rules
        declare DIFFERENT component keys (or the same keys with
        agreeing values) such that a single real stack COULD satisfy
        every declared component from both simultaneously, with
        neither being more specific than the other. `ItemValueRegistry`
        must FAIL VALIDATION for that pair at load time (reject both,
        log a hard error) rather than silently picking whichever rule
        happened to load first or last. Load order must never be a de
        facto tie-breaker.
      - EXACT DUPLICATES (identical declared components AND values,
        including two plain fallbacks) are rejected the same way, as
        the trivial case of a tie.
      - Every pair within an item's rule set is checked for these
        conflicts independently — a rule already rejected against one
        conflicting rule must still be compared against every other
        rule for the same item, so three mutually duplicate/ambiguous
        rules are ALL rejected, not just the first two compared.

    static Optional<ItemValueRule> pickMostSpecificMatch(
        List<ItemValueRule> candidates, ItemStack stack
    ) {
        // among every rule in `candidates` that matches `stack`,
        // returns the one with the most declared component entries;
        // a plain fallback (0 declared entries) is used only if no
        // component-aware rule matches. Ambiguous ties are impossible
        // here — they are rejected at load time by validateAndGroup,
        // so this never needs to break one itself.
    }

    static Map<Item, List<ItemValueRule>> validateAndGroup(
        Map<Identifier, ItemValueRule> parsed
    ) {
        // groups rules by item, then rejects (logs, excludes from the
        // result — never crashes) every rule that is an exact
        // duplicate or an ambiguous tie with another rule for the
        // SAME item, per the rules above. Pure and package-private —
        // directly testable against a hand-built map, no datapack
        // load required.
    }

  Simple entries (bread, coal, planks, leather, tools) author ONLY
  `itemId` + `baseValue`, `componentMatch` absent — exactly as
  convenient as a bare item-ID rule, no forced complexity for the
  common case. Component-sensitive entries (potion variants, and any
  future enchanted book/map/filled-container/custom-food value) add a
  `componentMatch` reference stack. This is the general mechanism the
  instruction asked for — not a hardcoded potion branch.

  REGISTRY LOADING, CORRECTED: because `componentMatch` reuses
  `ItemStack.CODEC` (component-bearing — `PotionContents` resolves a
  `Holder<Potion>`, a registry entry, exactly like the enchantment
  case that already forced `ShopRegistry` off the simple reload-
  listener pattern), `ItemValueRegistry` needs `RegistryOps` built from
  real `RegistryAccess`, the SAME `ServerLifecycleEvents.SERVER_STARTED`
  pattern `ShopRegistry` already uses (`RegistryOps.create(JsonOps.
  INSTANCE, server.registryAccess())`) — NOT the simpler
  `SimplePreparableReloadListener` pattern `QuestRegistry`/
  `NpcNameRegistry` use. The earlier draft of this document claimed the
  simple pattern would suffice specifically because it assumed a bare-
  ID-only key; that assumption no longer holds once component-aware
  rules exist in the SAME registry, so the whole registry adopts the
  more capable loading pattern uniformly rather than trying to split
  rules across two differently-loaded files.

--------------------------------------------------------------------------------
1b. Price resolution service — corrected to accept transaction context
--------------------------------------------------------------------------------
  CORRECTED (2026-07-13 pass): the original two-argument shape
  (`quoteRetailPrice(ItemStack)`/`quoteSellPayout(ItemStack)`) has no
  room for the modifier chain Section 1 already lists (Relationship,
  reputation, Persuasion, condition, quantity, authored override,
  player/merchant identity) without replacing every call site later
  when those modifiers arrive. PROPOSED — a small stateless helper
  (name TBD, candidate `ItemPricingService`), still NOT a new parallel
  payment path — it only computes a quote, `CreditPaymentHelper` still
  moves the money:

    public record PricingContext(
        ServerPlayer player,
        Optional<Identifier> merchantId,        -- e.g. shopId, or a
                                                    Provisioner entity's
                                                    identity once
                                                    Section 8 exists
        TransactionType direction,               -- BUY or SELL, reuses
                                                    (or aligns with) the
                                                    TransactionType enum
                                                    already PROPOSED in
                                                    TOTALITY_ECONOMY_
                                                    BANKING_TRADING.txt
                                                    Section 6
        Optional<Long> authoredOverride          -- Section 1c's
                                                    ShopEntry-supplied
                                                    override, if any
        // future, NOT required to exist yet: relationship/reputation/
        // Persuasion inputs, merchant specialization — added to this
        // record later without touching quoteRetail/quoteSell's own
        // signatures
    ) {}

    public record PriceQuote(
        long unitPrice, int quantity, long total,
        TransactionType direction
        // optional modifier/breakdown info — NOT a full user-facing
        // breakdown UI yet (Section 1f), just enough for the caller to
        // display/validate against
    ) {}

    PriceQuote quoteRetail(ItemStack stack, int quantity, PricingContext context);
    PriceQuote quoteSell(ItemStack stack, int quantity, PricingContext context);

  Exact field list on `PricingContext`/`PriceQuote` is PROPOSED, not
  locked — the ARCHITECTURAL RULE that matters is that pricing calls
  take context and quantity from day one, so adding a modifier later
  (Relationship, Persuasion, etc., all still individually TBD/future
  work) extends the context record rather than requiring every call
  site to be rewritten. Do not build a full user-facing price-breakdown
  UI now — no existing code supports one yet (`TradingScreen` currently
  shows a flat price per entry, see Section 1f).

--------------------------------------------------------------------------------
1c. Authored-offer override
--------------------------------------------------------------------------------
  `ShopEntry.price` (or its future per-entry-stock equivalent, Section
  3) is treated as an EXPLICIT OVERRIDE of the central computed price
  when present, not a duplicate of it. A shop entry with no override
  should be resolvable by item id alone against
  `ItemValueRegistry` — exact "how does an entry OPT OUT of an
  override and fall back to central pricing" field shape is TBD
  (candidate: make `price` `Optional<Long>` for future authored shops,
  while existing `test_trader.json`-style shops keep an explicit price
  for backward compatibility).

--------------------------------------------------------------------------------
1d. Conflict flagged against TOTALITY_ECONOMY_BANKING_TRADING.txt
--------------------------------------------------------------------------------
  That document's Section 7a says items "receive an `ItemValue` at
  registration or equivalent static data" — this document's
  `ItemValueRegistry` is DATA-DRIVEN (JSON) rather than registration-
  time, to match the actual pattern every other Totality registry in
  this codebase uses (`ShopRegistry`, `QuestRegistry`,
  `NpcNameRegistry` — all JSON-datapack-driven, none use compile-time
  registration). Treat that document's "at registration" wording as
  superseded by this document's data-driven approach, consistent with
  Section 0's precedence rule.

--------------------------------------------------------------------------------
1e. Server-authoritative displayed quotes (canonical rule)
--------------------------------------------------------------------------------
  ADOPTED. This is not a new philosophy — it's the SAME pattern
  `TradeSessionManager` already follows for BUY today (confirmed in
  code: `sendState` builds server-computed `ShopEntryDisplayData(stack,
  price, affordable)` and sends it to the client; `handleBuy` then
  recomputes `entry.price() * quantity` itself and never trusts
  anything the client sent back) — this section just states it as an
  explicit canonical rule so it's not lost once pricing becomes
  context-/player-specific:

  - The SERVER computes the price quote (`PriceQuote`, Section 1b)
    displayed by the client.
  - The CLIENT only displays the server-issued quote — it does not
    independently determine the authoritative price.
  - When BUY or SELL is submitted, the server RECALCULATES or
    RE-VALIDATES the current quote before committing the transaction —
    a quote the client is holding may be stale by the time it submits.
  - A stale or manipulated client-supplied quote must never be able to
    produce an invalid price or a duplication exploit.

  This matters MORE once prices become player-specific (Relationship,
  Persuasion, reputation, Section 1b's `PricingContext`) than it does
  today, since two different players could legitimately see two
  different quotes for the identical item at the identical merchant —
  the server, not the client, is what decides which one is honored at
  commit time. No networking is designed/changed in this pass — this
  is a canonical rule for whoever implements Section 11 step 2 to
  build against.

--------------------------------------------------------------------------------
1f. Rounding — TBD
--------------------------------------------------------------------------------
  UNRESOLVED, explicitly flagged rather than decided in this pass.
  Percentage-based modifiers (the 50% sell payout itself, and any
  future condition/Relationship/Persuasion percentage modifier) can
  produce fractional Credit results, and Credits are a whole-number
  currency (`WalletComponent`/`CreditsItem` both use `long`). Open
  questions, none decided here:

  - Whether BUY totals round up, down, or via another rule (e.g.
    banker's rounding) when a modifier produces a fraction.
  - Whether SELL totals round the same way, or deliberately differently
    (e.g. always rounding a payout DOWN and a cost UP, so the merchant
    is never disadvantaged by rounding, is one candidate but NOT
    adopted here).
  - Whether rounding is applied PER UNIT (round each individual item's
    price, then multiply by quantity) or ONCE on the full transaction
    total (compute the exact fractional total, round only at the end)
    — these two approaches can disagree for large quantities and must
    be picked deliberately, not left to fall out of implementation
    order.
  - How to prevent a zero-price or quantity-inflation exploit (e.g. a
    fractional-cent item that rounds down to 0 Credits per unit,
    bought at very high quantity for an effectively free haul) once
    any modifier can push a price below 1 Credit.

  No canonical Totality document currently resolves this — do not
  choose a rule implicitly by whatever the first implementation
  happens to do; decide it explicitly when Section 11 step 2 (context-
  aware pricing service) is actually built.

  STILL UNRESOLVED after Phase 1's implementation (2026-07-13 hardening
  pass) — flagged explicitly rather than quietly settled by what the
  code happens to do today: `ItemPricingService`'s SELL quote computes
  `unitPrice = baseValue / 2` using plain integer division, which
  FLOORS any odd `baseValue`. Every currently-authored base value
  (Section 4) is even, so this floor never actually discards anything
  today — that is a property of the CURRENT DATA, not a rounding
  policy. The following remain explicitly open and MUST be settled
  before functional SELL ships or before any odd base value is
  authored, whichever comes first:
    - Whether an odd base value's SELL payout floors, rounds to
      nearest, or ceilings.
    - How a future percentage modifier (condition/Relationship/
      Persuasion/reputation) composes with the existing 50% split —
      same open per-unit-vs-total-rounding question as above.
    - Whether a minimum positive payout is guaranteed once modifiers
      exist (e.g. a floored-to-zero SELL payout for a genuinely
      valuable item would be a real design problem, not just a
      cosmetic rounding quirk).
  Do NOT read `baseValue / 2` as this document's chosen rounding rule —
  it is a placeholder that happens to be exact for the current data,
  not a decision. Do not introduce floating-point pricing to work
  around this — Credits remain a whole-number `long` currency
  end-to-end; whatever rounding rule is eventually chosen must resolve
  in integer arithmetic.

================================================================================
2. PROVISIONER BUSINESS CREDITS
================================================================================
ADOPTED, PLAYTEST VALUE for the number itself.

  - `baseStartingCredits = 300` Credits. Baseline, not a maximum.
  - Player purchases (BUY) increase the merchant's `currentCredits`.
  - Player sales (SELL) decrease the merchant's `currentCredits`.
  - The merchant cannot buy items beyond its `currentCredits` — a SELL
    that would exceed the pool is rejected or quantity-limited.
  - `currentCredits` may exceed `baseStartingCredits` (no hard ceiling
    from ordinary trading — only Investment (Section 2b/10) permanently
    raises the merchant's BASELINE, distinct from its live balance —
    see Section 2b for the corrected terminology).
  - Replenishment/reset behavior is TBD (ties into
    TOTALITY_ECONOMY_BANKING_TRADING.txt Section 7c's "next restock
    time" concept — whether Credit pool and item restock share one
    timer or are independent is not decided).

  This confirms and reuses the accounting direction already locked in
  TOTALITY_ECONOMY_BANKING_TRADING.txt Section 7d (buy increases pool,
  sell decreases it — the doc's own correction of an earlier backwards
  note). No conflict here, just a concrete number for it.

--------------------------------------------------------------------------------
2a. Where this actually lives (reconciliation, not in the original
    request but required to make it buildable)
--------------------------------------------------------------------------------
  `currentCredits` CANNOT be a `WalletComponent` (Section 0 — that
  class is `ServerPlayer`-coupled). It also is NOT physically-held
  inventory (Section 7 explicitly requires the full business balance
  not exist in the merchant's pocket). PROPOSED: a plain persisted
  field on the Provisioner entity itself (Section 8's
  `ProvisionerNpcEntity`), NBT key candidate `"CurrentCredits"` (long),
  read/written the same way `TotalityNpcEntity` already persists
  `dialogueId`/`shopId`/`Gender` — no new component-framework machinery
  needed for a single per-entity long.

--------------------------------------------------------------------------------
2b. Baseline vs. live balance — corrected terminology
--------------------------------------------------------------------------------
  CORRECTED (2026-07-13 pass): the original draft conflated a
  merchant's live trading balance with its authored/investable
  baseline by saying Investment "permanently increases the
  `currentCredits` floor." That mixes two different concepts. Five
  distinct terms, kept separate:

    baseStartingCredits          Authored baseline, currently 300
                                  Credits (Section 2), a PLAYTEST VALUE.
    permanentInvestmentBonus     FUTURE persisted per-merchant bonus
                                  created by player investment (Section
                                  10) — TBD, not built yet.
    applicablePersuasionMasteryBonus
                                  FUTURE player/merchant-context bonus
                                  from Persuasion masteries (Section
                                  10) — TBD, exact behavior not decided.
    effectiveStartingCredits     CONCEPTUALLY derived:

                                    effectiveStartingCredits =
                                      baseStartingCredits
                                      + permanentInvestmentBonus
                                      + applicable baseline modifiers

                                  This is what a merchant's balance
                                  replenishes TOWARD (Section 2's
                                  "Replenishment/reset behavior is
                                  TBD" — the exact mechanism is still
                                  unresolved, only the TARGET value's
                                  composition is clarified here).
    currentCredits                The LIVE merchant business balance,
                                  changed immediately by every BUY
                                  (increases it) and SELL (decreases
                                  it) — Section 2's existing field,
                                  unchanged by this correction.

  Investment does NOT directly turn `currentCredits` into a permanent
  floor — it raises `permanentInvestmentBonus`, which raises
  `effectiveStartingCredits`, which is a separate concept from whatever
  `currentCredits` happens to be at any given moment from ordinary
  trading. The exact replenishment behavior toward
  `effectiveStartingCredits` remains TBD, as it already was.

================================================================================
3. PROVISIONER RANDOMIZED STOCK
================================================================================
ADOPTED direction, PLAYTEST VALUE for every specific item/price/
quantity/probability below.

  Each individual Provisioner receives a balanced persistent
  assortment, generated ONCE and never rerolled by reopening the shop,
  relogging, restarting the server, or unloading/reloading the NPC.

  Normal assortment (PLAYTEST VALUES):

    Guaranteed:
      Torch x16 — 4 Credits each
      Bread x6 — 12 Credits each
      Water Bottle x4 — 8 Credits each

    Select four distinct common entries from:
      Oak Planks x32 — 2 Credits each
      Coal x16 — 8 Credits each
      String x8 — 10 Credits each
      Leather x6 — 16 Credits each
      Glass Bottle x8 — 6 Credits each
      Apple x8 — 8 Credits each

    Select one cooked-food entry:
      Cooked Beef x6 — 18 Credits each
      Cooked Porkchop x6 — 18 Credits each

    Select one tool entry (Stone tier, stock 2):
      Stone Shovel — 24 Credits / Stone Sword — 28 / Stone Pickaxe —
      32 / Stone Axe — 36

    PLAYTEST VALUE: 10% chance the tool slot uses one Iron tool
    instead (stock 1): Iron Shovel — 72 / Iron Sword — 84 / Iron
    Pickaxe — 96 / Iron Axe — 108.

  Provisioners sell emergency tools but do not BUY tools. Tools,
  weapons, armor, ores, and ingots primarily belong to the future
  Blacksmith market (net-new NPC, not built yet).

--------------------------------------------------------------------------------
3a. Where this lives (the real reconciliation)
--------------------------------------------------------------------------------
  Per Section 0, the existing `ShopTemplate`/`ShopRegistry` are shared,
  immutable, JSON-authored data — they CANNOT hold per-instance
  variation. Two layers are needed, kept deliberately separate:

    1. A STATIC, SHARED "assortment pool" definition — the pick-lists
       and probabilities above — authored once as data, structurally
       similar to `ShopTemplate` but describing CHOICES rather than a
       fixed sell list. PROPOSED name `ProvisionerAssortmentPool`.
       CORRECTED (2026-07-13 pass): this pool's entries do NOT reduce
       to plain item ids — the approved pool itself contains the same
       Water-Bottle-vs-Potion-of-Healing distinction as Section 1a
       (both `minecraft:potion`, distinguished only by
       `DataComponents.POTION_CONTENTS`). Each pool entry is therefore
       authored as a full `ItemStack` via the SAME `ItemStack.CODEC`
       `ShopEntry` already uses (no new codec, no potion-specific
       field — simple entries like coal/bread just happen to have no
       extra components set, so they stay just as compact to author).
       Because of that, `ProvisionerAssortmentPool` needs the SAME
       `ServerLifecycleEvents.SERVER_STARTED` + `RegistryOps` loading
       pattern as `ShopRegistry` (and as `ItemValueRegistry`, Section
       1a) — NOT the simple reload-listener pattern this document
       originally (incorrectly) proposed. The earlier draft's claim
       that "plain item ids resolve fine under `JsonOps.INSTANCE`" is
       WRONG for this pool specifically and is retracted here.

    2. A per-entity PERSISTED runtime result of rolling against that
       pool exactly once — PROPOSED `MerchantStockEntry(ItemStack
       item, int stock)` list, persisted on the `ProvisionerNpcEntity`
       instance (Section 8), NOT stored back into `ShopTemplate`.
       Price is deliberately NOT frozen into this runtime record — it
       resolves LIVE against `ItemValueRegistry`/`ItemPricingService`
       (Section 1) at display time, so a future price modifier
       (Relationship, Persuasion, etc.) applies without needing to
       reroll or rewrite stock. Only WHICH items and HOW MANY are
       rolled once and frozen; PRICE is always live.

  `TradeSessionManager`/`TradingScreen` will need a future extension
  point to read from a Provisioner's per-entity `MerchantStockEntry`
  list instead of (or in addition to) a shared `ShopRegistry` template
  when the NPC in question is a `ProvisionerNpcEntity` — flagged as a
  real code change needed in Implementation Order (Section 11), not
  attempted in this documentation-only pass.

================================================================================
4. RARE SPECIAL STOCK
================================================================================
ADOPTED direction, PLAYTEST VALUE for the numbers.

  - Each Provisioner has a 12.5% chance to carry one normal vanilla
    drinkable Potion of Healing. Stock 1, price 80 Credits.
  - Generated once, persisted, same mechanism as Section 3's roll (not
    a separate system — one roll pass produces the whole assortment
    including this slot).
  - Provisioners do not normally buy potions.
  - The future Alchemist NPC will reliably sell Alchemy API potions
    (the existing `AlchemyPotionItem`/`PotionData` system per project
    memory `project_trading_system.md`'s "Alchemist candidate" note),
    ingredients, and related specialist products — this rare vanilla
    potion on the Provisioner is an incidental extra, not a preview of
    Alchemist stock.

================================================================================
5. PROVISIONER ACCEPTED GOODS
================================================================================
ADOPTED, kept deliberately separate from Section 1's value system, per
the explicit instruction that "a merchant accepting an item is
separate from the item having a value."

  Provisioners may buy common general goods: ordinary foods and
  ingredients; wood and planks; coal; string/fibres/hides and similar
  utility materials.

  Provisioners do NOT normally buy: tools, weapons, armor, potions,
  enchanted equipment, phones, quest items, artifacts, specialist
  machines.

--------------------------------------------------------------------------------
5a. Proposed shape
--------------------------------------------------------------------------------
  Matches (and formally adopts) the `accepts_tags` field ALREADY
  recovered/proposed in TOTALITY_ECONOMY_BANKING_TRADING.txt Section
  7c's shop-definition sketch, previously undecided/unimplemented —
  this document locks it in as a plain item-tag set:

    Set<TagKey<Item>> acceptedTags   -- e.g. #totality:provisioner_buys

  Checked independently of `ItemValueRegistry` at SELL time: an item
  must BOTH have a resolvable base value (Section 1) AND match an
  accepted tag for a given merchant to be sellable to it. Neither
  condition alone is sufficient. Per-merchant-type tag sets (Blacksmith
  accepts different tags than Provisioner) is the natural extension
  point once a second merchant archetype exists — not built here.

================================================================================
6. SELLABLE-STOCK LIQUIDITY CHECK
================================================================================
ADOPTED, PLAYTEST VALUE — arithmetic sanity check only, not a locked
formula.

  At the default 50% sell payout, excluding tools and the Healing
  Potion (which the Provisioner doesn't buy back anyway, Sections 3/4):

    Guaranteed subtotal: 84 Credits
    Four common entries: minimum 128 / average 160 / maximum 184
    Cooked-food slot: 54 Credits

    Total: minimum 266 / average 298 / maximum 322 Credits

  This is why 300 starting Credits is currently considered a suitable
  first-playtest value — a player selling back the entire non-tool,
  non-potion assortment in one sitting lands almost exactly at the
  starting pool, neither trivially draining it nor leaving it
  untouched. Reroll this table if Section 3's item list/prices change.

================================================================================
7. NPC/MERCHANT STATE SEPARATION
================================================================================
ADOPTED. Three distinct concepts must stay distinct:

  - NPC PERSONAL INVENTORY — whatever the entity is notionally
    "carrying" on its person. `TotalityNpcEntity` currently has no
    real inventory of its own (it's a `PathfinderMob`, no `Container`
    implementation) — this is presently a NOTIONAL/future concept for
    Provisioners, not something that exists in code yet.
  - MERCHANT SALE STOCK — Section 3's per-entity
    `MerchantStockEntry` list. This is business inventory, not
    personal property.
  - MERCHANT BUSINESS CREDITS — Section 2's `currentCredits`. The
    merchant's full business balance must NOT physically exist in
    their pocket (i.e. must NOT be represented as physical `CreditsItem`
    stacks the entity is "holding" — it's a plain persisted number,
    Section 2a).

  Future Pickpocket (not designed here) normally exposes PERSONAL
  inventory and any carried physical Credits, NOT the complete
  business balance — this document only reserves that distinction, it
  does not design Pickpocket. Stealing actual merchant stock (a future
  Pickpocket interaction) must remove it from the merchant's runtime
  stock list (Section 3a), not conjure a duplicate.

================================================================================
8. GENERIC PROVISIONER IDENTITY
================================================================================
ADOPTED, REWORDED to match what actually exists in code (Section 0).

  - The first Provisioner is a generic NPC archetype, not a unique
    named character — same distinction `TotalityNpcEntity.
    usesRandomIdentity()` already encodes (`true` by default, `false`
    for hand-crafted named characters).
  - It uses the EXISTING random gender + name system:
    `NpcGender`/`NpcNameRegistry` (`entity/npc/`). There is currently
    no separate "skin category" axis in code — do not design against
    one that doesn't exist; if broader appearance variation is wanted
    later, that is new scope on top of this document, not something
    it assumes.
  - Its generated identity (name/gender) AND its generated assortment
    (Section 3) AND its `currentCredits` (Section 2) must all persist
    on the same entity instance across relog/restart/reload — this is
    a superset of what `TotalityNpcEntity` already persists
    (`dialogueId`, `shopId`, `Gender`), extended with the new fields.
  - PROPOSED entity shape, following the `BankerNpcEntity` precedent
    (thin subclass, Section 0):

      public class ProvisionerNpcEntity extends TotalityNpcEntity {
          // new persisted fields: List<MerchantStockEntry> stock,
          // long currentCredits
          // roll-once-on-first-spawn logic (mirrors finalizeSpawn's
          // existing random-gender/name roll, extended to also roll
          // Section 3's assortment + Section 2's starting Credits
          // exactly once, guarded so a reload/respawn never rerolls
          // an already-generated instance)
      }

  - Stable identity will later support individual Relationship,
    Investment (Section 10), Contacts, personal inventory (Section 7),
    and Pickpocket state — none of that is designed here, just kept
    possible by giving the Provisioner a real, persistent per-instance
    identity now instead of a stateless/interchangeable one.

================================================================================
9. FIRST TRADING TUTORIAL QUEST
================================================================================
ADOPTED DIRECTION, mechanics TBD. Reconciles with, and partially
supersedes on NAMING, TOTALITY_ECONOMY_BANKING_TRADING.txt Section 9's
"First Sale" quest concept — same intent, this document's name takes
precedence per Section 0's precedence rule (recency).

  - Approaching a Provisioner for the first time starts a global,
    player-specific trading tutorial quest.
  - Any Provisioner may advance it — not tied to one specific NPC
    instance (contrast with Section 8's per-instance identity, which
    is about STOCK/CREDITS, not quest gating).
  - It should teach: Dialogue-gated trading (the ECON-13 Interact ->
    Dialogue -> Trade flow, already implemented and playtest-verified
    per TOTALITY_POST_AUDIT_DESIGN_DECISIONS.md Section 5), buying,
    selling, accepted categories (Section 5), the 50% resale value
    (Section 1), and limited merchant Credits (Section 2).
  - Exact trigger distance, text, reward, and stages remain TBD.
  - Do NOT implement this quest until a functional SELL exists
    (Section 1/5's central pricing + accepted-categories work) —
    teaching a mechanic that doesn't work yet is worse than not having
    the tutorial.

--------------------------------------------------------------------------------
9a. Reconciliation: how this would actually hook in
--------------------------------------------------------------------------------
  Follows the existing `QuestManager` hardcoded-hook pattern (Section
  0) used by `FIRST_SIGNAL`/`MOBILE_BANKING` — a new quest identifier
  plus a new static hook (candidate `onProvisionerApproached(player)`)
  called from wherever proximity is actually detected. Proximity-based
  triggering is NEW plumbing relative to the current quest system's
  existing triggers (which fire on discrete actions like equipping the
  phone, not continuous distance checks) — should be throttled (e.g.
  checked on a slow tick interval or only while a Provisioner is
  loaded/ticking near a player), not a naive per-tick distance scan
  across every player/NPC pair. Exact throttle mechanism is TBD,
  deferred to implementation time per Section 9's "not until SELL
  exists" gate anyway.

================================================================================
10. FUTURE INTEGRATIONS (recorded, not designed)
================================================================================
  - Persuasion masteries may raise `applicablePersuasionMasteryBonus`
    (Section 2b), and therefore a merchant's `effectiveStartingCredits`
    for a given player's trades — not `currentCredits` directly.
  - A Friend-tier Relationship may unlock an Investment dialogue
    option.
  - Investment may permanently increase an individual merchant's
    `permanentInvestmentBonus` (Section 2b), raising its
    `effectiveStartingCredits` baseline — NOT a direct, permanent floor
    on the live `currentCredits` balance (Section 2b corrects this
    wording; the two concepts are related but distinct).
  - Contacts may later display known merchants and relationship state.
  - Restocking, assortment rotation, Credit replenishment, buyback
    (already flagged TBD in TOTALITY_ECONOMY_BANKING_TRADING.txt
    Section 8), Relationship, Pickpocket (Section 7), and specialist
    merchants (Blacksmith, Alchemist) all remain future work, not
    scoped by this document.

================================================================================
11. IMPLEMENTATION ORDER
================================================================================
CORRECTED (2026-07-13 pass) to be consistent with
TOTALITY_POST_AUDIT_DESIGN_DECISIONS.md — the earlier draft's 8-step
order didn't match that document's Phase 3 listing (Economy Transaction
API before Item Value/Merchant Runtime). This is now the single
reconciled order for both documents (see also the clarifying note added
to that document's Section 9c):

  1. Component-aware `ItemValueRegistry`/value-rule system (Section
     1/1a) — central item-value registry, `ItemValueRule` with
     item-ID fallback AND component-aware matching. New package
     `api/economy/value/`.
  2. Context-aware `ItemPricingService` and server-authoritative quote
     representation (Section 1b/1e) — `PricingContext`/`PriceQuote`,
     `quoteRetail`/`quoteSell`.
  3. Minimal merchant transaction/runtime foundation required by SELL
     (Section 2/5): `baseStartingCredits`/`currentCredits`;
     `acceptedTags` or equivalent accepted-category rules; authoritative
     server-side validation; ATOMIC exchange of item and Credits (never
     "remove item, then attempt payment" or "pay player, then attempt
     item removal" — validate fully before any mutation, commit both
     sides together or neither). This is a MINIMAL, purpose-built
     contract for SELL specifically — it does NOT require the full
     future Economy Transaction API (ledger, refund history,
     idempotency, rollback — TOTALITY_POST_AUDIT_DESIGN_DECISIONS.md
     Section 9c) to be finished first; that broader API can still be
     built later without redoing this minimal contract, only extending
     it.
  4. Functional server-side SELL transaction — EXTENDS
     `TradeSessionManager` (new `handleSell` alongside existing
     `handleBuy`), `CreditPaymentHelper` (new merchant-pays-player
     path), built on steps 1-3.
  5. `TradingScreen` SELL UI and server-issued quote display — wires up
     the currently-inert SELL mode (`screen/shop/TradingScreen.java`)
     to step 4, following Section 1e's server-authoritative-quote rule.
  6. Persistent randomized Provisioner assortment (Section 3/3a) — new
     `ProvisionerAssortmentPool` (static data, component-aware per
     Section 3a's correction) + `MerchantStockEntry` (per-entity
     persisted runtime).
  7. Generic `ProvisionerNpcEntity`/profile (Section 8) and its
     persisted identity, stock, and Credits — EXTENDS
     `TotalityNpcEntity`, thin subclass per the `BankerNpcEntity`
     precedent, plus the roll-once-on-first-spawn logic tying steps
     3/6 together.
  8. First trading tutorial quest (Section 9) — new `QuestManager`
     hook + quest JSON, gated on step 5 being functional first.
  9. Later: restocking, Credit replenishment, buyback, investment,
     Relationship, Pickpocket, Contacts, and specialist merchants
     (Section 10) — not scheduled yet.

All prices, quantities, and probabilities in Sections 2-6 are PLAYTEST
VALUES, not permanent balance — expect them to move once real
playtesting happens against a working SELL implementation.

================================================================================
12. IMPLEMENTATION STATUS
================================================================================
Tracks ONLY what has actually been built against this document. Update
this section, not the numbered design sections above, as each
Implementation Order step lands.

--------------------------------------------------------------------------------
12a. Phase 1 — COMPLETE (2026-07-13): Section 11 steps 1-2
--------------------------------------------------------------------------------
  Built, compiled clean (`gradlew compileJava`), package
  `api/economy/value/`:

  - `ItemValueRule` (`ItemValueRule.java`) — `record(ItemStack
    referenceStack, long baseValue)`, reuses `ItemStack.CODEC` exactly
    as Section 1a specifies. A rule matches an item-ID-only fallback
    when its reference stack's `getComponentsPatch()` is empty;
    otherwise every declared component must equal the real stack's
    value for that component type (components the rule doesn't
    declare are never checked; stack count is never compared).
  - `ItemValueRuleConflicts` (package-private) — pure duplicate/
    ambiguity detection, isolated so it's directly exercisable without
    a full datapack load.
  - `ItemValueRegistry` — loads `data/totality/item_values/*.json` (one
    rule per file), using the SAME `ServerLifecycleEvents.
    SERVER_STARTED` + `RegistryOps` pattern as `ShopRegistry` (no live
    `/reload`, same known trade-off). Rejects negative `base_value`s;
    rejects duplicate/ambiguous rules for the same item at load time
    with a diagnostic log error naming both conflicting resource IDs
    (conflicting rules are excluded from the resolved registry, not
    crash-on-load). Zero is an ALLOWED base value — no sellability
    meaning is attached to it (Section 5 still owns "is this item
    accepted," untouched by this phase).
  - `PricingDirection` (`RETAIL`/`SELL`) — a minimal, deliberately NOT
    the broader `TransactionType` TOTALITY_ECONOMY_BANKING_TRADING.txt
    Section 6 proposes; align them later if that broader API is built.
  - `PricingContext`/`PriceQuote` — as Section 1b, with `player`/
    `merchantId` carried but unused this phase (future Relationship/
    reputation/Persuasion/specialization extend the context record
    without touching call sites), `authoredOverride` fully functional
    (bypasses `ItemValueRegistry` and becomes the quote's unit price
    directly).
  - `ItemPricingService.quoteRetail`/`quoteSell` — 100%/50% of
    resolved base value; checked `Math.multiplyExact` (throws
    `ArithmeticException` on overflow, never wraps); throws
    `IllegalArgumentException` for non-positive quantity or a
    direction/context mismatch. Rounding beyond exact integer division
    remains Section 1f's unresolved TBD — not decided by this
    implementation.
  - 20 `data/totality/item_values/*.json` files for every Section 4
    value, including the two component-aware `minecraft:potion` rules
    (Water Bottle, Potion of Healing) — no plain-`minecraft:potion`
    fallback rule was authored, and no splash/lingering potion values
    were added.
  - `ItemValueVerification` (package-private, dev-environment-gated
    self-test at `SERVER_STARTED`, since the project has no JUnit/test
    source set at all) — covers every case the phase required: Bread
    -> 12, Stone Axe -> 36, Water Bottle -> 8, Potion of Healing -> 80,
    each potion doesn't resolve as the other, a custom-named Healing
    Potion still resolves to 80, an unauthored potion variant resolves
    to nothing, quantity overflow throws, invalid quantity throws, and
    direct duplicate/genuine-ambiguity/mutually-exclusive-not-ambiguous
    cases are all detected correctly.

  NOT implemented (unchanged from design, explicitly out of scope for
  Phase 1): SELL transactions, merchant Credits
  (`baseStartingCredits`/`currentCredits`), `acceptedTags`,
  `ProvisionerNpcEntity`, randomized Provisioner assortment,
  `TradingScreen` SELL UI, the tutorial quest, restocking, and buyback.
  `ShopEntry.price`'s override semantics (Section 1c) are NOT yet
  wired to `PricingContext.authoredOverride` — the field exists and
  works, but nothing calls it with a real `ShopEntry` value yet.

  KNOWN PRE-EXISTING ISSUE, unrelated to this phase: `gradlew
  runServer` (dedicated server) currently fails during mod init —
  `EnergyItems`/`ModItems` load a client-only `Screen` class in a path
  that also runs on the dedicated server, which crashes before
  `ItemValueRegistry` even registers. This predates this phase (neither
  file has been touched) and was not fixed here — full runtime
  self-test output requires launching via `gradlew runClient`
  (integrated server) instead.

  RUNTIME-VERIFIED (2026-07-13, via `gradlew runClient`, integrated
  server, world startup log): 20 item value rules loaded across 19
  item IDs; all 11 self-test checks above PASSED — Bread/Stone Axe/
  Water Bottle/Healing Potion all resolved correctly, Water and Healing
  did not cross-match, a custom-named Healing Potion still matched,
  Night Vision had no value, quantity overflow and invalid quantities
  were rejected, and the pairwise duplicate/ambiguity helper checks
  passed.

--------------------------------------------------------------------------------
12b. Phase 1 hardening pass — COMPLETE (2026-07-13)
--------------------------------------------------------------------------------
  Corrected a real bug found by re-review, and hardened validation
  without touching the matcher itself (`getComponentsPatch()`-based
  matching is unchanged and stays runtime-verified correct):

  - FIXED: `ItemValueRegistry.validateAndGroup`'s pairwise conflict
    scan used to SKIP a pair once either side was already marked
    rejected (`if (rejected.contains(idA) || rejected.contains(idB))
    continue;`). This under-rejected a genuine three-or-more-way
    conflict — e.g. three identical duplicate rules A/B/C: comparing
    (A,B) rejected both, then (A,C) and (B,C) were skipped entirely,
    so C was never actually checked against anything and could survive
    by accident. The skip is removed — every pair in an item's group is
    now evaluated unconditionally, so the result no longer depends on
    comparison order. `validateAndGroup` and the new
    `pickMostSpecificMatch` (extracted from `resolveBaseValue`, same
    behavior) are now package-private `static` methods, directly
    testable against a hand-built map/list without a datapack load.
  - HARDENED: `PricingContext` now validates in its canonical
    constructor — `player`, `merchantId`, `direction`, and
    `authoredOverride` must all be non-null (`Optional` fields use
    `Optional.empty()`, never a null reference), and a present
    `authoredOverride` must not be negative. Zero remains a legal
    override. Invalid state is now impossible to construct, not just
    unlikely.
  - `ItemValueVerification` updated to match: constructs a real
    `TotalityFakePlayer` (via `server.overworld()`) instead of passing
    `null` as the player, since `player` is no longer nullable. Six new
    registry-level checks exercise `validateAndGroup` directly on
    isolated, hand-built maps (never touching the real loaded data):
    three identical component-aware rules all rejected; three
    identical plain fallbacks all rejected; one valid rule survives
    untouched alongside three mutually-duplicate conflicting rules for
    the same item; a lower- and a compatible higher-specificity rule
    both load, and the higher-specificity one wins when a stack
    matches both; two equal-specificity mutually exclusive rules
    (Water vs. Healing) both load; two equal-specificity overlapping
    rules that could both match one stack are both rejected. Two new
    checks cover `PricingContext`'s override validation: a negative
    override throws at construction; a zero override produces a valid
    zero-price quote and a positive override becomes the exact unit
    price (with the total still computed via `Math.multiplyExact`).
    Total self-test check count: 11 (original) + 6 (registry-level) +
    2 (override validation) = 19.
  - Registry load-reporting log message corrected to distinguish
    parsed/accepted/rejected counts instead of implying every parsed
    definition is active: `"Parsed {N} item value rules: {accepted}
    accepted, {rejected} rejected, across {M} items"` (previously
    worded as if `loaded.size()` were already the accepted count).
  - Design text (Section 1a) corrected: the canonical rule is that
    ONLY equally-specific rules can conflict with each other — two
    rules of DIFFERENT specificity are never in conflict and both load
    unconditionally, with the more specific one winning at match time.
    The earlier wording's resolveBaseValue pseudocode imprecisely
    implied any two component-aware rules capable of matching the same
    stack were an authoring error; that was wrong once different-
    specificity coexistence was required, and is corrected in place.
  - Section 1f (rounding) reinforced: the current `baseValue / 2` SELL
    calculation is explicitly documented as a placeholder correct only
    because all current data is even, not a chosen rounding policy —
    odd-value rounding, modifier-rounding composition, and minimum
    positive payout all remain open and must be settled before
    functional SELL or an odd base value ships.

  `gradlew compileJava`: SUCCESSFUL. `gradlew runClient` launched
  clean to the main menu with no startup errors (proving the hardening
  changes introduced no client-side regression).

  RUNTIME-VERIFIED (2026-07-13, via `gradlew runClient`, integrated
  server, world startup log, confirmed twice on separate loads): 20
  item value rules parsed, 20 accepted, 0 rejected, across 19 items;
  all 19 self-test checks passed, including all 6 new registry-level
  conflict cases and both new override-validation cases; no unexpected
  `ERROR` lines (only the intentional synthetic ones from the
  registry-validation self-tests, each immediately followed by its
  expected PASS).

  Still NOT implemented (unchanged): SELL transactions, merchant
  Credits, `acceptedTags`, `ProvisionerNpcEntity`, randomized stock,
  `MerchantStockEntry`, `TradingScreen` SELL UI, buyback, restocking,
  tutorial quest, Relationship/reputation/Persuasion/investment
  modifiers, the broader Economy Transaction API. The dedicated-server
  `EnergyItems`/`Screen` issue remains unfixed and out of scope.

--------------------------------------------------------------------------------
12c. Phase 2 — Minimal merchant runtime and SELL foundation (2026-07-13)
--------------------------------------------------------------------------------
  Implements Section 11 steps 3-4 (a minimal merchant transaction
  contract and a functional server-side SELL), plus the BUY-side half
  of step 3 (merchant Credits increase on BUY). Steps 5 (TradingScreen
  SELL UI), 6 (randomized assortment), 7 (`ProvisionerNpcEntity`), and
  8 (tutorial quest) remain untouched, per explicit scope.

  MERCHANT RUNTIME (Section 2, new `api/shop/` classes):
  - `MerchantRuntime` — interface: `merchantId()`, `currentCredits()`,
    `setCurrentCredits(long)` (throws on negative — a merchant balance
    can never go negative by construction, not by clamping), and
    `acceptedTags()` with a default `accepts(ItemStack)` that checks
    membership across all accepted tags (an EMPTY tag set correctly
    rejects everything — no implicit "accepts anything" fallback).
  - `InMemoryMerchantRuntime` — the PLACEHOLDER implementation Section
    2a/11 anticipated: non-persistent, resets on server restart,
    explicitly documented as something `ProvisionerNpcEntity` replaces
    later without changing any code written against the
    `MerchantRuntime` interface.
  - `MerchantRuntimeRegistry` — lazily creates one
    `InMemoryMerchantRuntime` per `shopId` (the only merchant identity
    that exists before per-entity Provisioners), seeded with Section
    2's `baseStartingCredits = 300` PLAYTEST VALUE and the single
    current Provisioner accepted-tag policy (every merchant currently
    uses the same policy, since only one merchant archetype exists).

  ACCEPTED GOODS (Section 5): `ModTags.PROVISIONER_BUYS`
    (`#totality:provisioner_buys`), backed by a real new tag data file
    `data/totality/tags/item/provisioner_buys.json` populated with
    real vanilla items only: bread, apple, carrot, cooked beef, cooked
    porkchop, oak planks, coal, string, leather. `carrot` is
    deliberately included with NO `item_values` rule authored, to
    give the self-test a genuine "accepted but unvalued" case. Tools,
    weapons, armor, potions, and anything ore/ingot-shaped are
    deliberately absent, per Section 5's exclusion list. Sellability
    requires BOTH a resolvable base value (Section 1, unchanged from
    Phase 1) AND this tag — checked independently, exactly as
    Section 5a specifies.

  SERVER-ISSUED SELL STATE (Section G): new `MerchantSellQuoteView`
    record — `accepted`, `hasValue`, `unitPayout`,
    `maxQuantityByStack`, `maxQuantityByMerchant`,
    `effectiveMaxQuantity`, `requestedQuantity`, `totalPayout`,
    `rejectionReason`. Its static `compute(...)` is the SAME pure
    logic used by both `TradeSessionManager.handleSell`'s validation
    and the self-test — one implementation, not two. No new
    client-facing networking carries this yet (Part G explicitly
    allows deferring the full SELL UI).

    `Context/Audit/Image References/trade_screen.png` (initially
    mis-searched under a typo'd filename, located after Stefan
    corrected it) was reviewed after this section was first written.
    It confirms the shape chosen here without requiring any changes:
    it shows Merchant Credits displayed alongside the player's own
    Credits (confirming `ShowShopStatePayload` will need an additive
    `merchantCredits` field later — trivially available already via
    `MerchantRuntime.currentCredits()`), a bottom "YOUR INVENTORY" row
    the player clicks to SELL (confirming `SellItemPayload`'s choice
    to identify the sold stack by inventory `slotIndex` rather than by
    item identity), a BUY/SELL/BUYBACK three-tab layout (confirming
    BUYBACK is real future scope, not invented), and per-item Stock
    counts (the future `ProvisionerAssortmentPool`/`MerchantStockEntry`
    work, cleanly separable from `MerchantRuntime`). The detail panel's
    Price Each / Stock / Quantity / Total Cost fields map directly onto
    `MerchantSellQuoteView`'s `unitPayout`/`effectiveMaxQuantity`/
    `totalPayout`. Nothing built this phase needed to change as a
    result of reviewing it.

  SELL TRANSACTION (Section D, `TradeSessionManager.handleSell`,
    new): validates, in order — active session; positive quantity;
    valid slot; non-empty real stack at that slot; stack holds enough;
    then delegates accepted/valued/quotable/affordable-by-merchant
    checks to `MerchantSellQuoteView.compute` (steps 7-11 collapse
    into one reused check); finally confirms the player can safely
    receive the payout (`CreditPaymentHelper.canReceive`). Every check
    happens before any mutation. Commit is three operations already
    proven safe by validation — remove the exact quantity, pay the
    player the exact payout, decrement the merchant's Credits by the
    exact payout — so no rollback machinery was needed (nothing in the
    commit step can fail once validation has passed). New C2S
    `SellItemPayload(slotIndex, quantity)` / `SellItemHandler`, mirrors
    `BuyItemPayload`/`BuyItemHandler` exactly.

  MERCHANT-TO-PLAYER PAYMENT (Section E, `CreditPaymentHelper`, new
    `receive`/`canReceive`): credits the player's Wallet directly —
    the SAME established mechanism `BankTellerHandler.deposit` already
    uses to give a player Credits it didn't take from elsewhere in the
    same operation (distinct from `pay`, which SPENDS existing player
    funds). Rejects negative amounts, treats zero as a trivial no-op,
    and checks for overflow before mutating rather than relying on
    `WalletComponent.modify`'s own clamp.

  BUY INTEGRATION (Section F, `TradeSessionManager.handleBuy`,
    modified): computes the merchant's prospective new balance via
    `Math.addExact` and rejects (before charging the player) if it
    would overflow. Merchant Credits are only actually incremented
    after the player has been successfully charged — a failed BUY
    changes neither the player's funds nor the merchant's Credits.

  ROUNDING (Section H, `ItemPricingService`, modified): the Phase 1
    accidental-behavior placeholder is replaced with the explicit
    Phase 2 CANONICAL INTERIM rule —
    `baseValue == 0 ? 0 : Math.max(1L, baseValue / 2L)` — floor half,
    but never below 1 Credit for a positive base value. Exposed as
    public `ItemPricingService.sellUnitPayout(long)` so the rule can
    be verified directly against values no real item currently has
    (5, 1). Still explicitly INTERIM — modifier-composition and
    per-unit-vs-transaction-total rounding remain open per Section 1f.

  VERIFICATION LOGGING CLEANUP (Part A, new
    `api/core/util/VerificationReporter`): shared PASS/FAIL/summary
    helper now used by both `ItemValueVerification` and the new
    `MerchantSellVerification`. Concise by default — one summary line
    per suite; `-Dtotality.verboseVerification=true` also prints every
    individual PASS; failures always print individually either way.
    `ItemValueRegistry`'s conflict validation was split into a PURE
    `validate(...)` (returns a structured `ValidationResult`, logs
    nothing) and the production `load()` path that turns
    `ValidationResult.conflicts()` into real `ERROR` log lines — the
    registry self-tests now call `validate(...)` directly, so
    synthetic `totality:selftest/...` conflicts no longer produce any
    log output at all (previously they produced real-looking `ERROR`
    lines with an explanatory disclaimer). Real datapack conflicts
    still log as `ERROR` exactly as before — confirmed by the real
    `test_trader`/`item_values` data still loading and logging
    normally.

  NEW SELF-TEST SUITE (Part I, `MerchantSellVerification`, 22
    checks): checks 1-13 exercise `MerchantSellQuoteView`/
    `ItemPricingService` directly against isolated
    `InMemoryMerchantRuntime` fixtures (accepted+valued sellable;
    valued-but-unaccepted rejected; accepted-but-unvalued rejected;
    tool and potion rejected by policy; base value 12 -> payout 6;
    odd value 5 -> floored payout 2; value 1 -> minimum payout 1;
    value 0 -> payout 0; quantity multiplication; quantity overflow;
    exact-Credits and insufficient-Credits merchant affordability).
    Checks 14-22 drive the REAL `TradeSessionManager` session/commit
    path against the existing `totality:test_trader` shop and a
    `TotalityFakePlayer` (successful SELL removes the exact quantity,
    pays the exact amount, and decreases merchant Credits by the exact
    amount; a failed SELL changes nothing at all; successful/failed
    BUY correctly does/doesn't change merchant Credits; invalid
    session/slot/quantity/empty-stack SELL attempts are all safely
    rejected; selling part of a stack leaves the correct remainder;
    an item with an irrelevant extra component still resolves its
    correct base value). Every check is wrapped so an unexpected
    exception becomes a reported FAILURE rather than aborting the rest
    of the suite.

  `gradlew compileJava`: SUCCESSFUL on first attempt.

  RUNTIME-VERIFIED (2026-07-13, via `gradlew runClient`, integrated
  server, world startup log): `Parsed 20 item value rules: 20
  accepted, 0 rejected, across 19 items`; `[ItemValueVerification] All
  19 self-test checks passed.`; `[MerchantSellVerification] All 22
  self-test checks passed.` — 41 checks total, zero failures. Zero
  unexpected `ERROR` lines (confirming the logging-cleanup fix:
  synthetic conflict data no longer logs at all) and zero `PASS` lines
  printed by default (confirming concise-mode works as designed) — the
  only other log lines were expected `WARN`s from
  `MerchantSellVerification`'s own intentional-rejection checks
  (invalid session/slot/quantity/empty-stack), which are genuine
  `TradeSessionManager` rejection messages, not verification noise.
  `-Dtotality.verboseVerification=true` was not independently
  re-confirmed by Claude Code this pass (the concise-mode result above
  already demonstrates the default path correctly); flip it on to see
  every individual PASS line if wanted.

  NOT implemented, unchanged from scope: `ProvisionerNpcEntity`,
  persistent per-entity merchant NBT, randomized assortment,
  `ProvisionerAssortmentPool`, `MerchantStockEntry`, stock
  depletion/restocking, Credit replenishment, buyback, the full
  `TradingScreen` SELL visual redesign, the tutorial quest,
  Relationship/reputation/Persuasion/investment modifiers,
  Pickpocket, Contacts, Blacksmith/Alchemist NPCs, the broader Economy
  Transaction API, ledger history, refunds, an idempotency framework,
  and the unrelated dedicated-server `Screen`-classloading bug.

12d. Phase 2 hardening pass (2026-07-14)
--------------------------------------------------------------------------------
  A focused hardening pass over 12c's Phase 2 work, driven by a review-
  bundle audit. Preserves every check that already passed; does not
  touch Phase 3 scope (`ProvisionerNpcEntity`, randomized stock,
  TradingScreen SELL redesign — still explicitly deferred).

  1. BUY OVERFLOW / NEGATIVE-PRICE / NEGATIVE-PAYMENT DEFENSES
     `TradeSessionManager.handleBuy` now computes `price * quantity`
     via `Math.multiplyExact` (was raw `*`, which could silently wrap
     negative, e.g. `Long.MAX_VALUE * 100`) and rejects an entry with
     `price() < 0` before any payment attempt. `CreditPaymentHelper`
     had two real bugs fixed: `canAfford(player, negative)` and
     `canAffordPhysical(player, negative)` both previously returned
     `true` for a negative amount (the negative collapsed to 0 via
     `Math.max(0, ...)` in `canAfford`, and `physicalCredits(player) >=
     negativeAmount` is trivially true in `canAffordPhysical`) — both
     now explicitly reject `amount < 0` up front, which transitively
     fixes `pay`/`payPhysical` since both delegate to the afford check
     first. `receive`/`canReceive` already rejected negative amounts
     correctly and were left alone. `physicalCredits(player)` now sums
     with saturating (not wrapping) addition, so multiple large
     `totality:credits` stacks can never flip the total negative.
     `ItemPricingService.sellUnitPayout(long)` now throws
     `IllegalArgumentException` on a negative input instead of
     silently returning a positive minimum payout of 1; `quote(...)`
     guards its one call site so a negative authored base value
     degrades to "no resolvable value" rather than propagating the
     exception into a live transaction. `ShopRegistry.load` now drops
     (logs `ERROR`, does not load) any catalog entry with a negative
     authored price as a datapack authoring error — defense-in-depth
     alongside, not instead of, `handleBuy`'s own transaction-time
     rejection.

  2. VERIFICATION ISOLATION FROM PRODUCTION MERCHANT STATE
     `MerchantSellVerification` previously called
     `MerchantRuntimeRegistry.getOrCreate(TEST_TRADER_SHOP_ID)` and
     mutated the SAME runtime real gameplay would use, and the final
     shared-runtime check left `totality:test_trader` at 282 Credits
     instead of the intended 300 — a real test-pollution bug.
     `TradeSessionManager` gained package-private
     `startTradeForVerification(player, shopId, ShopTemplate,
     MerchantRuntime[, npc])` entry points that accept an explicitly
     supplied `ShopTemplate` and `MerchantRuntime` instead of resolving
     them from `ShopRegistry`/`MerchantRuntimeRegistry` — the public
     `startTrade` is unchanged and still resolves production state
     normally. Every session-based self-test now builds its own
     isolated `ShopTemplate` fixture (a self-authored "Verification
     Shop", not the real `totality:test_trader` JSON) and a fresh
     `InMemoryMerchantRuntime` per check, so no check can create or
     mutate `MerchantRuntimeRegistry`'s real per-server map at all — a
     stronger isolation than save/restore. A new `runSessionCheck`
     helper wraps every session-based check's session start/action/
     assert in `try {...} finally { TradeSessionManager.endTrade(...);
     <slot cleared> }`, so a check that throws unexpectedly can never
     leave a dangling session or leftover inventory state behind for
     the next one. A new check (`checkVerificationNeverTouchesProduc
     tionRuntime`, run last) asserts `MerchantRuntimeRegistry.peek(
     server, TEST_TRADER_SHOP_ID).isEmpty()` — a non-creating registry
     lookup added specifically to prove this — and that no session
     remains dangling for the fake player.

  3. MERCHANT-RUNTIME / TRADE-SESSION LOGICAL-SERVER LIFECYCLE
     `MerchantRuntimeRegistry`'s single process-global `Map<Identifier,
     InMemoryMerchantRuntime>` is now `Map<MinecraftServer,
     Map<Identifier, InMemoryMerchantRuntime>>`, with a new
     `MerchantRuntimeRegistry.register()` hooking
     `ServerLifecycleEvents.SERVER_STOPPED` to drop the entire
     per-server entry — a client that closes one integrated world and
     opens another in the same JVM can no longer see merchant Credits
     left over from the previous world. `MerchantRuntimeRegistry.
     getOrCreate`/`peek` both now take an explicit `MinecraftServer`;
     `TradeSessionManager.startTrade` supplies it via
     `player.level().getServer()` (the existing codebase convention,
     see `RestSessionManager`/`BlessSpell` for precedent). A new
     `TradeSessionManager.register()` hooks the same `SERVER_STOPPED`
     event to clear `SESSIONS` outright (no client connection remains
     by then to notify, unlike the per-player `DISCONNECT` cleanup
     already in `PlayerConnectionEvents`, which was already correct
     and untouched). A new pure lifecycle check
     (`checkServerStoppedClearsMerchantRuntimeToBaseline`) exercises
     `getOrCreate` -> mutate -> `clearForServer` -> `getOrCreate` again
     against a dedicated probe shop id and asserts the second call
     returns the 300-Credit baseline, without needing to actually stop
     and restart a real server mid-test.

  4. ENTITY-BACKED TRADE SESSION REVALIDATION
     `ActiveTrade` now stores the NPC's `UUID` and dimension
     (`ResourceKey<Level>`) alongside its numeric entity id, and
     exposes `isEntityBacked()` (`npcEntityId != -1`) so a
     deliberately non-entity-backed session (verification, a future
     remote shop) is never confused with a missing/removed NPC on an
     otherwise entity-backed one. A new private
     `TradeSessionManager.revalidateNpc` runs at the top of both
     `handleBuy` and `handleSell`, before any other check: for an
     entity-backed session it re-resolves the entity by id in its
     recorded dimension and requires ALL of — non-null, `isAlive()`,
     `!isRemoved()`, `getUUID().equals(recordedUuid)` (not just a
     reused numeric id), `player.level() == npc.level()`, distance
     within `TotalityNpcEntity.INTERACTION_RANGE_SQR` (a new public
     constant — the number itself is unchanged, 8 blocks/64.0 squared,
     extracted from `customServerAiStep`'s existing per-tick safety-net
     check so there is exactly one interaction-distance rule, not two
     that could drift apart), and — for a `TotalityNpcEntity`
     specifically — a new `isInteractionLockOwnedBy(player)` confirming
     the lock hasn't been superseded. On failure, `revalidateNpc` calls
     `endTrade` itself (releasing the lock, notifying the client) and
     the caller performs no economic mutation. `startTrade`/`beginSes
     sion` now also releases a REPLACED session's previous NPC lock
     before installing the new one (previously only `endTrade` did
     this, so opening a second trade without closing the first left
     the original NPC frozen). `releaseTradePartner` was fixed to
     resolve the NPC via the trade's recorded dimension rather than
     `player.level()` — previously, if the player had already changed
     dimension, the old NPC's lock could never be found and released.
     Two new checks exercise this against a real spawned
     `TotalityNpcEntity` (`server.overworld().addFreshEntity(...)`):
     discarding the NPC mid-session (which independently also proves
     `TotalityNpcEntity.remove()`'s own proactive session-end still
     works) and moving the NPC out of range WITHOUT a server tick
     running, so only `revalidateNpc` itself (not the entity's own
     per-tick safety net) can catch it — both assert the session ends
     and no mutation occurs. A wrong-dimension case was not added
     separately ("where practical") since it exercises the exact same
     `player.level() == npc.level()` branch as the distance check, and
     the dev harness has no second populated level readily available
     to move a fake player into.

  5. STRUCTURED TRANSACTION RESULTS, NO REJECTION-WARNING SPAM
     `TradeSessionManager.handleBuy`/`handleSell` no longer return
     `void` or call `LOGGER.warn`/`LOGGER.error` for any validation
     rejection — they return new `BuyResult`/`SellResult` records
     (`success`, a `Reason` enum, and payout/quantity detail) that the
     caller inspects instead. Ordinary gameplay rejections (item not
     accepted, merchant can't afford it, empty/stale slot, player
     can't afford a BUY, a session that raced closed, an NPC that
     wandered out of range) produce no log output anywhere, by design
     — they are normal outcomes a future SELL/BUY UI will display, not
     warnings. `BuyItemHandler`/`SellItemHandler` (the actual
     production network path) now log a `WARN` ONLY for reason values
     that indicate a packet the real UI could never send on its own —
     `INVALID_INDEX`/`OVERFLOW` for BUY, `INVALID_SLOT`/
     `INVALID_QUANTITY` for SELL. `MerchantSellVerification` asserts
     directly against the returned `Reason` values (e.g.
     `SellResult.Reason.NOT_SELLABLE`, `BuyResult.Reason.CANNOT_AFFORD`)
     instead of relying on log output at all.

  6. SMALL DEFENSIVE CLEANUP
     `InMemoryMerchantRuntime`'s constructor now rejects a null
     `merchantId` or `acceptedTags`, and stores `Set.copyOf(
     acceptedTags)` so a caller's original mutable `Set` reference can
     never change a merchant's accepted-goods policy after
     construction. `ItemPricingService.sellUnitPayout` — see Part 1
     above. This document's own Part I count in 12c ("checks 14-22")
     is corrected by this section: see the new count below.

  7. VERIFICATION SUITE — 31 checks (was 22)
     Checks 1-14 are pure `MerchantSellQuoteView`/`ItemPricingService`
     checks (13 unchanged from 12c, plus 1 new: negative base value
     rejected by `sellUnitPayout`). Checks 15-23 are the original 9
     real-session checks, refactored onto isolated fixtures per Part 2
     above but otherwise unchanged in intent. Checks 24-27 are new
     hardening checks for Part 1 (negative `pay`/`payPhysical`
     rejected without mutation; BUY multiplication overflow rejected
     without mutation; a negative authored shop price cannot complete
     a BUY). Check 28 is Part 3's lifecycle check. Checks 29-30 are
     Part 4's entity-backed revalidation checks. Check 31 (Part 2) runs
     last and asserts production merchant-runtime isolation.

  `gradlew compileJava`: SUCCESSFUL.

  RUNTIME-VERIFIED (2026-07-14, `gradlew runClient`, two consecutive
  logical-server starts in the same client process — "Testing World"
  loaded, fully quit ("Stopping server", all three dimensions saved),
  then reloaded): BOTH starts logged identically —
  `Parsed 20 item value rules: 20 accepted, 0 rejected, across 19
  items`; `[ItemValueVerification] All 19 self-test checks passed.`;
  `[MerchantSellVerification] All 31 self-test checks passed.` — 50
  checks total, zero failures, on EACH start. Zero unexpected `WARN`
  or `ERROR` lines from the `totality` namespace on either start
  (confirmed by grep across the full session log) — this is the
  concrete proof of Part 5's fix: the previous pass's expected
  intentional-rejection `WARN`s are gone entirely, not just
  suppressed by log level. Zero `PASS` lines printed by default
  (concise-mode still correct). The identical result on the second
  start — reached only after a real `MinecraftServer` instance fully
  stopped and a new one started — is the practical confirmation of
  Part 3's server-scoping fix: nothing carried over/leaked from the
  first logical server into the second (check 31 additionally proves
  no production `totality:test_trader` runtime entry was ever created
  by verification on either start).

  NOT implemented, unchanged from scope (Phase 3 and later, still
  explicitly deferred): `ProvisionerNpcEntity`, persistent per-entity
  merchant NBT, randomized assortment, `ProvisionerAssortmentPool`,
  `MerchantStockEntry`, stock depletion/restocking, Credit
  replenishment, buyback, the full `TradingScreen` SELL visual
  redesign, the tutorial quest, Relationship/reputation/Persuasion/
  investment modifiers, Pickpocket, Contacts, Blacksmith/Alchemist
  NPCs, the broader Economy Transaction API, ledger history, refunds,
  an idempotency framework, and the unrelated dedicated-server
  client-classloading fix.

12e. Phase 2 correction pass (2026-07-14, later same day)
--------------------------------------------------------------------------------
  A narrowly-scoped follow-up to 12d, fixing four issues found by a
  second review pass over the hardening pass itself. Preserves every
  check that already passed; still does not begin Phase 3.

  1. UUID-SAFE LOCK RELEASE. `revalidateNpc` already compared the
     resolved entity's UUID against `ActiveTrade.npcUuid()`, but
     `releaseTradePartner` resolved the recorded NPC by dimension +
     numeric entity id ALONE (a private `resolveNpc` helper, no UUID
     check) before calling `releaseInteractionLock()` — so if that
     numeric id ever resolved to a DIFFERENT entity than the one the
     session actually started with, ending the session could release
     the WRONG entity's lock. Fixed by centralizing UUID-safe
     resolution into one helper, `resolveRecordedNpc(player, trade)`,
     used by BOTH `revalidateNpc` and `releaseTradePartner` — it
     returns an entity only when the recorded dimension exists, the
     numeric id resolves, `npcUuid` is non-null, AND the resolved
     entity's UUID matches the recorded one (via a new extracted
     package-visible predicate, `matchesUuid(candidate, expectedUuid)`
     — see below for why it is a separate function). `revalidateNpc`'s
     own redundant UUID check was removed since the helper now
     guarantees it; every other check it performed (liveness,
     dimension, range, lock ownership) is unchanged.

     A new verification check (`checkReusedEntityIdWithWrongUuidLock
     NotReleased`) proves the guarantee, but NOT by forcing a live
     numeric-id collision through `Level#getEntity(int)` — two earlier
     attempts at exactly that both failed for reasons specific to this
     self-test harness, not to the fix: first via `Entity.setId` called
     before a second `addFreshEntity`, which was empirically observed
     to have no effect (the id the level actually indexes did not
     change); then via forcing the target chunk to load synchronously
     first, which still left `getEntity` returning null. The root cause
     confirmed by that second attempt's diagnostic: this suite runs at
     `SERVER_STARTED`, before the server has processed a single tick —
     and `Level#getEntity(int)`'s lookup is populated by tick-driven
     entity-section processing, so a freshly spawned entity is never
     visible to it yet, regardless of chunk-load state. (This is a
     characteristic of testing at `SERVER_STARTED` specifically, not a
     bug — a real Provisioner in actual gameplay is always resolvable
     by the time a player can interact with it, many ticks after
     spawn.) Given a live end-to-end resolution genuinely cannot be
     exercised deterministically in this harness, `matchesUuid` was
     extracted as its own package-visible predicate — the ONLY part of
     `resolveRecordedNpc` that depends on comparing two UUIDs, with the
     null/level-lookup guards around it being simple, low-risk
     delegation to well-established APIs that do not need the same
     scrutiny. The check drives `matchesUuid` directly against two
     real, distinct, never-added-to-the-level `TotalityNpcEntity`
     objects (a positive control that a real UUID matches itself, and
     an "impostor" that must not match) and confirms the impostor's own
     unrelated interaction lock is never touched by the mismatched call.

  2. LIFECYCLE VERIFICATION NO LONGER TOUCHES THE PRODUCTION REGISTRY.
     `checkServerStoppedClearsMerchantRuntimeToBaseline` called
     `MerchantRuntimeRegistry.getOrCreate`/`clearForServer` directly
     against the REAL development `MinecraftServer` — exactly the
     production-state mutation this suite's own Part 2 isolation work
     otherwise goes out of its way to avoid. Removed outright rather
     than refactored into an isolated instance: the two-consecutive-
     logical-server runtime test already documented in 12d (load,
     fully quit, reload; both starts identical) is real, end-to-end
     proof of the exact same `SERVER_STOPPED` behavior, achieved
     without mutating anything live. Verification check count is 1
     lower from this removal alone; see item 7 below for the net
     total after this pass's additions.

  3. VERIFICATION CLEANUP NOW MATCHES ITS OWN DOCUMENTATION.
     `runSessionCheck` previously restored the session and the test
     inventory slot in `finally`, but wallet restoration was left to
     each individual check body — a body that threw before reaching
     its own `setWallet(player, 0)` line would leave the fake player's
     wallet mutated for whatever check ran next. `runSessionCheck` now
     captures the player's wallet value AND the original stack in the
     test slot BEFORE the check body runs, and restores both in
     `finally` unconditionally — the four check bodies that previously
     reset the wallet manually at their own end
     (`checkSuccessfulBuyIncreasesMerchantCredits`,
     `checkBuyOverflowRejectedWithoutMutation`,
     `checkNegativeShopPriceCannotCompleteBuy`, plus
     `checkFailedBuyDoesNotChangeMerchantCredits`'s setup-only
     `setWallet(player, 0)`, which was already setup rather than
     cleanup and is unchanged) had their now-redundant manual resets
     removed. The two standalone checks that mutate the fake player
     outside `runSessionCheck`
     (`checkNegativePaymentRejectedWithoutMutation`,
     `checkNegativePhysicalPaymentRejectedWithoutMutation`) gained
     their own `try/finally` for the same reason. The fake player
     finishes the suite with no active trade session, its original
     wallet value, no leftover test inventory, and no production
     merchant-runtime entries created by verification — the last of
     these already had its own dedicated check
     (`checkVerificationNeverTouchesProductionRuntime`, unchanged,
     still runs last).

  4. MALFORMED PHYSICAL CREDITS HARDENED. `CreditPaymentHelper.
     physicalCredits` already saturated instead of overflow-wrapping
     for a large POSITIVE stored amount (12d), but its guard
     (`amount > 0 && total > Long.MAX_VALUE - amount`) only gated the
     saturation branch, not the addition itself — a zero-or-negative
     stored amount (safely constructible via `CreditsItem.setAmount`,
     which performs no validation of its own) fell through to
     `total + amount`, actually SUBTRACTING from the running total.
     Fixed by skipping any stack with a non-positive stored amount
     entirely (`if (amount <= 0) continue;`) before the saturating-add
     branch runs at all — a malformed Credits item can now only ever
     be worth nothing, never a negative contribution. A new check
     (`checkMalformedNegativePhysicalCreditsStackIgnoredNotSubtracted`)
     constructs exactly this malformed stack alongside a legitimate
     one and proves the computed total is unaffected either way.

  5. `gradlew compileJava`: SUCCESSFUL.

  6. RUNTIME-VERIFIED (2026-07-14, `gradlew runClient`, "Testing
     World" reloaded): `Parsed 20 item value rules: 20 accepted, 0
     rejected, across 19 items`; `[ItemValueVerification] All 19
     self-test checks passed.`; `[MerchantSellVerification] All 32
     self-test checks passed.` — 51 checks total, zero failures. Zero
     unexpected `WARN`/`ERROR` lines from the `totality` namespace
     (confirmed by grep across the full session log) and zero `PASS`
     lines printed by default — concise logging remains intact. This
     confirmation took several attempts on the entity-backed UUID
     check specifically (see item 1's design note above) before
     landing on the pure-predicate approach that finally passed
     cleanly on the first try once implemented; every other check
     (including the 3 carried over unchanged from 12d's entity-backed
     work) passed on every attempt.

  7. VERIFICATION SUITE — 32 checks (was 31): net +1 from 12d (-1 for
     removing the lifecycle check per item 2 above, +2 for the new
     UUID-collision check per item 1 and the malformed-Credits check
     per item 4). Checks 1-14 unchanged (pure quote/pricing checks,
     including 12d's negative-base-value addition). Checks 15-23
     unchanged (the original 9 real-session checks). Checks 24-28 are
     12d's Part 1 hardening checks, now including
     `checkMalformedNegativePhysicalCreditsStackIgnoredNotSubtracted`
     as check 26. Checks 29-30 are 12d's entity-backed revalidation
     checks (discarded NPC, out-of-range NPC); check 31 is this pass's
     new reused-entity-id/wrong-UUID check. Check 32 (production-
     runtime isolation) still runs last.

  NOT implemented, unchanged from scope (Phase 3 and later, still
  explicitly deferred): identical list to 12d — `ProvisionerNpcEntity`,
  persistent per-entity merchant NBT, randomized assortment,
  `ProvisionerAssortmentPool`, `MerchantStockEntry`, stock
  depletion/restocking, Credit replenishment, buyback, the full
  `TradingScreen` SELL visual redesign, the tutorial quest,
  Relationship/reputation/Persuasion/investment modifiers, Pickpocket,
  Contacts, Blacksmith/Alchemist NPCs, the broader Economy Transaction
  API, ledger history, refunds, an idempotency framework, and the
  unrelated dedicated-server client-classloading fix.

12f. Phase 2 final correction (2026-07-14, later still)
--------------------------------------------------------------------------------
  A single, narrowly-scoped fix to a gap left by 12e's item 4. Phase 3
  still not begun.

  1. `shrinkPhysicalCredits` NOW HONORS THE SAME NON-POSITIVE GUARD AS
     `physicalCredits`. 12e's item 4 hardened `physicalCredits`'s
     read-only total against a malformed (zero-or-negative stored
     amount) Credits stack, but `shrinkPhysicalCredits` — the private
     helper both `payPhysical` and `pay`'s physical-remainder branch
     use to actually SPEND physical Credits — still processed such a
     stack exactly like a legitimate one. For a negative `stackAmount`,
     `take = Math.min(stackAmount, remaining)` came out negative, and
     `remaining -= take` then INCREASED the amount still owed rather
     than leaving it untouched. A malformed negative Credits stack
     sitting in an inventory slot earlier than a player's legitimate
     Credits could therefore make a physical payment consume MORE
     legitimate Credits than the requested amount, while `pay`/
     `payPhysical` still reported success. Fixed by adding the
     identical guard `physicalCredits` already uses
     (`if (stackAmount <= 0) continue;`), placed before `take` is
     computed, so a malformed stack is now skipped outright by both
     the totaling and the spending path. Because `shrinkPhysicalCredits`
     is the single method both `payPhysical` (physical-only) and
     `pay`'s remainder step (mixed Wallet + physical) call, this one
     guard protects both payment paths — no separate fix was needed
     for `pay`.

  2. NEW CHECK. `checkShrinkPhysicalCreditsIgnoresMalformedNegativeStack`
     constructs a malformed −9,999-Credits stack in an earlier
     inventory slot (slot 4) and a legitimate 100-Credit stack in a
     later slot (slot 6), then calls `payPhysical(player, 50)` and
     asserts: the payment succeeds; exactly 50 legitimate Credits
     remain (`physicalCredits` afterward, and the good stack's own
     stored amount, both read back as 50); and — implicitly, by that
     exact remaining amount — the malformed stack neither inflated the
     amount owed nor caused any extra legitimate Credits to be
     removed. Both inventory slots are captured before mutation and
     restored in `finally`, matching every other check's cleanup
     convention (12e item 3).

  3. `gradlew compileJava`: SUCCESSFUL.

  4. RUNTIME-VERIFIED (2026-07-14, `gradlew runClient`, "Testing
     World" reloaded): `[ItemValueVerification] All 19 self-test
     checks passed.`; `[MerchantSellVerification] All 33 self-test
     checks passed.` — 52 checks total, zero failures, on the first
     attempt. Zero unexpected `WARN`/`ERROR` lines from the `totality`
     namespace (the only `totality`-tagged `WARN` lines present are
     the pre-existing, unrelated missing-texture/missing-block-model
     warnings for `ritual_dais_active`, `blessed_incense`, `incense`,
     `zanpakuto`, and `platinum_coin` — unchanged by this correction)
     and zero `PASS` lines printed by default — concise logging
     remains intact.

  5. VERIFICATION SUITE — 33 checks (was 32): net +1, the new check
     from item 2 above. Checks 1-14 and 15-23 unchanged. Checks 24-26
     unchanged (12e's Part 1 hardening checks, including check 26,
     `checkMalformedNegativePhysicalCreditsStackIgnoredNotSubtracted`).
     Check 27 is this pass's new
     `checkShrinkPhysicalCreditsIgnoresMalformedNegativeStack`. Checks
     28-29 are the BUY-overflow/negative-price checks (12e's former
     27-28, shifted by one). Checks 30-31 are the entity-backed
     revalidation checks (12e's former 29-30). Check 32 is the reused-
     entity-id/wrong-UUID check (12e's former 31). Check 33
     (production-runtime isolation) still runs last.

  NOT implemented, unchanged from scope (Phase 3 and later, still
  explicitly deferred): identical list to 12e.

12g. Phase 3 — Provisioner assortment, per-entity stock, and stock-aware
     BUY (2026-07-14, later still)
--------------------------------------------------------------------------------
  Implements Section 11 steps 6-7 (persistent randomized Provisioner
  assortment + the generic `ProvisionerNpcEntity`), plus the stock-aware
  BUY-side extension to `TradeSessionManager` and the server-issued
  shop-state fields the future Trading Screen redesign (Phase 4) will
  need. Does NOT implement Phase 4's visual redesign, BUYBACK,
  restocking, Credit replenishment, the tutorial quest, or any of
  Section 10's future integrations — all explicitly out of scope, per
  task instruction.

  STATIC ASSORTMENT DATA (Part A, new package `api/shop/assortment/`):
  `AssortmentItemEntry` (`record(ItemStack item, int stock)`, reuses
  `ItemStack.CODEC` exactly as `ShopEntry`/`ItemValueRule` already do),
  `WeightedAssortmentEntry` (an `AssortmentItemEntry` plus a selection
  `weight`, JSON-flattened rather than nested), `AssortmentSelectionGroup`
  (`rolls`/`distinct`/`entries` — "choose N [distinct] entries", weighted-
  without-replacement when distinct, one algorithm covers both a uniform
  "choose 4 distinct" group and a tiered single choice), `ChanceAssortmentEntry`
  (an independent `chance` roll, JSON-flattened), and `ProvisionerAssortmentPool`
  (`guaranteed`/`selection_groups`/`chance_entries`, with a PURE
  `validate()` — mirrors `ItemValueRegistry.validate`'s "no logging, no
  I/O, self-tests inspect the structured result directly" pattern
  exactly — and a `roll(RandomSource)` producing the frozen
  `List<MerchantStockEntry>` a Provisioner persists). `validate()`
  rejects: empty/multi-count item stacks (stock must be tracked via the
  separate `stock` field, never stack count); non-positive stock; a
  non-positive or empty-pool group roll count; a distinct group
  requesting more rolls than it has entries; non-positive weights or a
  non-positive group total weight; a chance outside `[0, 1]` or
  non-finite; and — since no entry-merge rule exists — any item
  template (matched via `ItemStack.isSameItemSameComponents`)
  appearing more than once across the ENTIRE pool (guaranteed ∪ every
  group's entries ∪ chance entries). `ProvisionerAssortmentRegistry`
  loads `data/totality/provisioner_assortments/*.json`, the SAME
  `ServerLifecycleEvents.SERVER_STARTED` + `RegistryOps` pattern
  `ShopRegistry`/`ItemValueRegistry` already use (component-bearing
  templates need real registry access) — a pool that fails to parse or
  fails `validate()` is rejected with a logged, per-error diagnostic
  and simply absent from the registry, never a partial/crashing load.

  ADOPTED PROVISIONER POOL (Part B, `data/totality/provisioner_assortments/
  generic_provisioner.json`): guaranteed Torch×16, Bread×6, Water
  Bottle×4; a 4-distinct-of-6 common-material group (Oak Planks×32,
  Coal×16, String×8, Leather×6, Glass Bottle×8, Apple×8, all weight 1);
  a 1-of-2 cooked-food group (Cooked Beef×6, Cooked Porkchop×6, weight
  1 each); a 1-of-8 tool group modeling the 90%-Stone/10%-Iron tier
  split as four Stone entries at weight 9 (stock 2 each) and four Iron
  entries at weight 1 (stock 1 each) — total weight 40, so any one
  Stone tool is 9/40 = 22.5% (×4 = 90%) and any one Iron tool is
  1/40 = 2.5% (×4 = 10%), uniform within each tier by construction; and
  one 12.5%-chance entry for a normal drinkable Potion of Healing
  (stock 1, ordinary `minecraft:potion` + `minecraft:potion_contents`
  = `minecraft:healing`, no splash/lingering variant). No prices are
  authored anywhere in this file — every price resolves LIVE against
  the existing `ItemValueRegistry`/`ItemPricingService` at quote/commit
  time (all referenced items already had `item_values/*.json` entries
  from Phase 1/2, confirmed matching these exact PLAYTEST values before
  authoring this pool — no new pricing data was needed). All quantities,
  weights, and the 12.5% chance remain PLAYTEST VALUES, unchanged from
  Section 3/4.

  RUNTIME STOCK (Part C, new `api/shop/MerchantStockEntry` +
  `MerchantStockProvider`): `MerchantStockEntry` is an immutable record
  whose canonical constructor stores its OWN defensive copy of the item
  template (normalized to count 1) and whose `item()` accessor returns
  a further defensive copy on every read — no caller, however it
  obtained a reference, can mutate the internal template. Decrementing
  never mutates in place (`withStock(int)` returns a new instance).
  `currentStock` is validated `>= 0` both by the compact constructor
  (defense-in-depth against direct misuse) AND by the CODEC's own
  `Codec.intRange(0, Integer.MAX_VALUE)` field (so a corrupted NBT
  value fails to DECODE cleanly as a `DataResult` error, never by
  throwing mid-decode). `MerchantStockProvider` is a DELIBERATELY
  separate interface from `MerchantRuntime` (Credits and stock are
  different concerns, confirmed by `trade_screen.png` showing them as
  separate UI elements) — `stockEntries()`/`canPurchaseStock(index,
  qty)`/`decrementStock(index, qty)`, index-stable (an implementation
  must never remove/reorder an entry merely because it reaches zero).

  `ProvisionerNpcEntity` (Part D, `entity/npc/`): a thin
  `TotalityNpcEntity` subclass following the `BankerNpcEntity`
  precedent, implementing BOTH `MerchantRuntime` and
  `MerchantStockProvider` directly on the entity itself. Identity
  (random gender + name) is entirely unchanged — reuses
  `TotalityNpcEntity.finalizeSpawn` as-is, no new skin-category axis
  invented (Section 0/8, confirmed still accurate). Trading is reached
  through the EXISTING generic `dialogueId`/`mobInteract` path (set
  once, in the constructor, to a new `provisioner_greeting` dialogue)
  rather than an overridden `mobInteract` — a Provisioner has no "met
  before" narrative branching, so Banker's bespoke override pattern
  isn't needed here. `data/totality/dialogues/provisioner_greeting.json`
  is new: a two-choice greeting ("I'd like to trade" → new
  `open_provisioner_shop` dialogue action; "Just browsing" → farewell),
  reusing the existing Interact → Dialogue → Trade flow unchanged
  (ECON-13's fix, still the only path in).

  PERSISTENT PER-ENTITY CREDITS (Part E): `currentCredits` (a `long`,
  PLAYTEST baseline 300, reused directly from
  `MerchantRuntimeRegistry.BASE_STARTING_CREDITS` — no duplicate
  constant) plus an explicit `creditsInitialized` boolean marker —
  NEVER inferred from `currentCredits != 0` (a merchant legitimately
  holding exactly 0 Credits is a valid live state, not "uninitialized").
  `setCurrentCredits` rejects negative values, identical contract to
  `InMemoryMerchantRuntime`. No replenishment, reset, or Investment
  logic — unchanged from Section 2b's still-TBD status.

  ROLL-ONCE ASSORTMENT GENERATION (Part F): `stockInitialized` is an
  equally explicit, independent boolean marker — NEVER inferred from
  `stock.isEmpty()` (a fully sold-out merchant is a legitimate
  non-reroll-triggering state with all-zero stock, not an empty list).
  `ensureCreditsAndStockInitialized()` runs from TWO places: {1}
  `finalizeSpawn` — the normal path, and critically, `finalizeSpawn` is
  ONLY ever invoked for a genuinely NEW spawn, never for an entity
  deserialized from saved NBT, which is what makes "reload never
  rerolls" true without any extra guard logic; and {2}
  `customServerAiStep` — a cheap, per-tick, two-boolean-check fallback
  retry (both checks short-circuit to nothing once both markers are
  true) covering the one legitimate retry case: the referenced
  assortment pool was missing/invalid at spawn time but a later
  datapack fix makes it valid before the Provisioner is next
  interacted with. A missing/invalid pool logs a clear real-content
  error, leaves `stockInitialized` false, and creates no partial
  stock — confirmed by verification, never crashes the server.

  NBT PERSISTENCE (Part G): `CurrentCredits` (long), `CreditsInitialized`/
  `StockInitialized` (booleans), `AssortmentPoolId` (string), and
  `Stock` (`MerchantStockEntry.CODEC.listOf()`, via the standard
  `ValueOutput.store`/`ValueInput.read` pattern this codebase already
  uses elsewhere — e.g. `PlayerEquipmentComponent`, `RestStateComponent`).
  ALL-OR-NOTHING list decode: if any single persisted stock entry fails
  to decode (e.g. corrupted NBT), the codec's list decode fails as a
  whole rather than silently dropping just the bad entry — deliberately,
  so a corruption event can never leave a partially-rerolled or
  duplicated stock behind; `StockInitialized` is read independently
  either way, so corruption degrades to "this Provisioner presents as
  fully sold out," never a reroll or a duplication. `writePhase3State`/
  `readPhase3State` are factored out as small public methods
  specifically so `ProvisionerVerification` can drive a focused,
  real `ValueOutput`/`ValueInput` NBT round-trip of just this state
  (via `TagValueOutput.createWithContext`/`TagValueInput.create`,
  `ProblemReporter.DISCARDING`) without needing a full `Entity#save`/
  `#load` cycle.

  TRADESESSIONMANAGER INTEGRATION (Part H): `ActiveTrade.template()` is
  now `@Nullable` — `null` marks a STOCK-backed session (a Provisioner,
  started via new `TradeSessionManager.startEntityBackedTrade(player,
  npc)`, which binds the session directly to `npc`'s own
  `MerchantRuntime`/`MerchantStockProvider`, never `ShopRegistry`/
  `MerchantRuntimeRegistry`). CRITICAL correctness fix applied
  uniformly to BOTH BUY and SELL: every commit now RE-RESOLVES the live
  NPC entity via a new `currentNpc(player, trade)` helper (built on the
  EXISTING `resolveRecordedNpc` UUID-safe resolution) rather than ever
  trusting the `MerchantRuntime` reference captured at session start —
  a chunk unload/reload cycle deserializes a NEW Java object sharing
  the OLD object's UUID, so a stored reference would silently keep
  writing to a discarded, orphaned entity instance instead of the live
  one. `currentMerchant`/`currentStockProvider` prefer the live-resolved
  entity when it implements the relevant interface, falling back to the
  session's originally captured `MerchantRuntime` for the
  non-entity-tied shared-registry case (which has no staleness problem
  at all — it isn't bound to a Java entity object). Every Phase 2
  guarantee (UUID-safe lock release, dimension/distance/liveness
  validation, interaction-lock ownership, disconnect/`SERVER_STOPPED`
  cleanup, no wrong-NPC lock release on id reuse) is preserved
  unchanged — `revalidateNpc` itself was not touched.

  STOCK-AWARE BUY (Part I, new `TradeSessionManager.handleStockBuy`):
  validates, in order: valid entry index; `MerchantStockProvider
  .canPurchaseStock` (current stock ≥ requested quantity — the entry at
  a given index never changes identity except by stock count, so a
  fresh `stockEntries()` read already satisfies "entry still represents
  the server's current stock item"); a live retail price resolved
  against the entry's OWN item template via
  `ItemPricingService.quoteRetail` (never a persisted price, per Part
  B); the merchant's balance can safely absorb the total
  (`Math.addExact`); and the player can pay
  (`CreditPaymentHelper.pay`). Only once every check passes does
  anything mutate — pay, credit the merchant, decrement stock, then
  deliver items — mirroring the template-backed path's existing
  "validate everything first" discipline, so no rollback machinery was
  needed here either. A new `BuyResult.Reason.OUT_OF_STOCK` value
  covers the stock-specific rejection. SELL is UNCHANGED (Section
  D's existing 11-step validation still applies verbatim) except for
  the same live-entity-resolution fix Part H made — a successful SELL's
  only mutation is the player's own inventory; a Provisioner's
  `MerchantStockProvider`, if it has one, is never touched by SELL, so
  a sold item never becomes ordinary BUY stock (out of scope, per task
  instruction — buyback/resale-stock remains future work).

  SERVER SHOP-STATE / NETWORKING (Part J): `ShowShopStatePayload` gained
  `merchantCredits` (a `long`, always available via
  `MerchantRuntime.currentCredits()`); `ShopEntryDisplayData` gained
  `limitedStock`/`availableStock` (an explicit boolean+int pair, not an
  ambiguous `-1 = unlimited` sentinel) plus a `soldOut()` helper
  (`limitedStock && availableStock <= 0`). A 3-argument
  `ShopEntryDisplayData` constructor (`stack, price, affordable`)
  remains for the existing unlimited/template-backed case — every
  pre-Phase-3 call site still compiles unchanged. `TradeSessionManager
  .sendState` now branches: a stock-backed session reports each entry's
  live-quoted unit price, current-stock-derived affordability, and
  `limitedStock = true` with the real `availableStock`; a
  template-backed session is unchanged (`limitedStock = false`). No
  visual redesign — `TradingScreen.java` was NOT touched at all; the
  new fields are purely additive and compile-safe for a client that
  doesn't yet read them (Phase 4's explicit scope).

  CONCURRENCY / STALE STATE (Part K): no new machinery was added —
  the existing single-threaded server-tick execution model, combined
  with `revalidateNpc`'s per-commit re-validation and `handleStockBuy`'s
  own `canPurchaseStock` check immediately before mutation, is already
  sufficient: two BUY packets for the same player process strictly
  sequentially on the server thread, so the second one simply sees the
  first one's already-decremented stock and is rejected normally
  (`OUT_OF_STOCK`) if it would overdraw — no idempotency/ledger
  framework was built, per explicit scope.

  DEVELOPMENT VERIFICATION (Part L, new `api/shop/ProvisionerVerification`,
  45 checks): assortment validation/generation checks run against a
  hand-built, isolated `testPool()` fixture mirroring
  `generic_provisioner.json` exactly (so seed-driven probabilistic
  assertions never depend on production data staying byte-for-byte
  identical) — EXCEPT one check that deliberately DOES exercise the
  real, datapack-loaded `ProvisionerAssortmentRegistry`, the same way
  `MerchantSellVerification` exercises real `item_values`/
  `provisioner_buys` data. Deterministic `RandomSource.create(seed)`
  sweeps (up to 300 seeds) confirm both the Stone (stock 2) and Iron
  (stock 1) tool-tier branches are reached with correct stock, and the
  rare Healing Potion is both absent and present across seeds. Runtime-
  stock, persistence (real NBT round-trip via
  `TagValueOutput`/`TagValueInput`), per-entity-independence, and
  transaction-integration checks all use isolated `ProvisionerNpcEntity`
  instances (mostly never added to a level; a handful added and driven
  through the REAL `TradeSessionManager` for entity-backed-session
  checks, following `MerchantSellVerification`'s exact spawn/use/discard-
  in-`finally` precedent) plus two new test-only public hooks on
  `ProvisionerNpcEntity` (`setCreditsForTest`/`setStockForTest`) for
  seeding known state without depending on a real pool roll. One check
  explicitly re-confirms a template-backed (non-Provisioner) session
  still completes a BUY correctly after Phase 3's branching changes to
  `handleBuy`/`sendState` — a regression guard for Phase 2's existing
  path. Every check that mutates player Wallet/inventory/trade
  session/spawned entities restores or discards it in `finally`.

  `gradlew compileJava`: SUCCESSFUL. One real bug was found and fixed
  during this pass's own verification, not after: five
  `ProvisionerVerification` transaction-integration checks initially
  FAILED with `NPC_INVALID` — a freshly `addFreshEntity`'d Provisioner
  is not yet visible to `Level#getEntity(int)` at
  `ServerLifecycleEvents.SERVER_STARTED` (before any tick has run),
  the SAME documented harness-timing limitation the economy correction
  pass already flagged for `MerchantSellVerification`'s two entity-
  backed checks (which only ever exercised the FAILURE path, so they
  never actually hit this). Rather than accept the same limitation for
  five NEW checks that specifically need a SUCCESSFUL transaction, a
  new verification-only entry point was added — `TradeSessionManager
  .startStockBackedTradeForVerification(player, MerchantRuntime,
  MerchantStockProvider)` — binding a session directly to an explicitly
  supplied, isolated Credits+stock pair with NO entity at all
  (`npcEntityId == -1`), extending the SAME established
  `startTradeForVerification` isolation pattern to stock-backed
  sessions. `ActiveTrade` gained a `@Nullable MerchantStockProvider
  stockProvider` field for exactly this fallback; a REAL, entity-backed
  Provisioner session is completely unaffected — `startEntityBackedTrade`
  still always passes `null` for this field and always re-resolves the
  live entity, per Part H. All five checks now pass, exercising the
  exact same production `handleBuy`/`handleSell`/`handleStockBuy` logic
  a real Provisioner uses.

  RUNTIME-VERIFIED (2026-07-14, `gradlew runClient`, "Testing World"):
  `[ItemValueVerification] All 19 self-test checks passed.`;
  `[MerchantSellVerification] All 33 self-test checks passed.`;
  `[ProvisionerVerification] All 45 self-test checks passed.` — 97
  checks total, zero failures. `Loaded 1 provisioner assortment
  pool(s), 0 rejected` confirms `generic_provisioner.json` parses and
  validates cleanly against the real registry. Zero unexpected
  `WARN`/`ERROR` lines from the `totality` namespace: the only ones
  present are the pre-existing, unrelated missing-texture/missing-
  block-model warnings (`ritual_dais_active`, `blessed_incense`,
  `incense`, `zanpakuto`, `platinum_coin`, unchanged by this phase) and
  ONE fully expected `ERROR` line — `checkMissingPoolDoesNotMarkStockInitialized`'s
  own intentional-failure scenario logging the exact "assortment pool
  not found" diagnostic it exists to prove happens cleanly, without a
  crash or partial stock. Zero `PASS` lines printed by default —
  concise logging remains intact.

  MANUAL, INTERACTIVE VERIFICATION (Part M) — HONESTLY LIMITED: this
  environment has no way to drive the actual Minecraft client window
  (no input-injection tool), a limitation already noted in this
  project's 2026-07-14 hardening-pass notes. Stefan was present for
  part of this session and, unprompted, ran `/summon totality:
  provisioner` himself, which generated and displayed a named
  Provisioner ("Ashby") with no errors — real, observed evidence that
  entity registration, random-identity generation, and the client
  entity renderer registration all work end-to-end. Beyond that one
  incidental data point, NONE of Part M's listed manual checks (two
  Provisioners' independent Credits/stock, buy/reopen/no-reroll,
  save-and-reload persistence, sold-out-remains-sold-out, SELL not
  adding to stock, or reading the live shop-state payload) were
  performed by Claude Code this session — only claimed where actually
  observed, per instruction. The Testing World client was left running
  at the end of this session for Stefan to drive these manually if
  desired; the automated `ProvisionerVerification` suite above already
  covers the same guarantees at the code level (persistence via a real
  NBT round-trip, independence via isolated dual-entity fixtures,
  sold-out/no-reroll via the real roll-once guard fields), so manual
  play-testing here would be confirmatory, not load-bearing.

  NOT implemented, explicitly out of scope per task instruction: the
  full Trading Screen visual redesign, finished SELL visual
  interaction, BUYBACK, buyback inventory, restocking, assortment
  rotation, Credit replenishment, the tutorial quest, a proximity quest
  trigger, Relationship, reputation, Persuasion modifiers, Investment,
  Pickpocket, Contacts, Blacksmith, Alchemist, natural Provisioner
  spawning, structures/villages, merchant personal inventory, a
  broader transaction ledger, refund history, an idempotency framework,
  and the unrelated dedicated-server client-classloading fix.

--------------------------------------------------------------------------------
12h. Phase 3 hardening pass (2026-07-14, later still)
--------------------------------------------------------------------------------
  A focused hardening pass over 12g's Phase 3 work, driven by Stefan's
  manual finding that a freshly `/summon`ed Provisioner generated a
  named identity correctly but right-clicking it did nothing. Fixes
  only the issues below; still does NOT begin Phase 4 (Trading Screen
  redesign, BUYBACK, restocking, the tutorial quest, natural spawning,
  or any of Section 10's future integrations).

  1. ROOT CAUSE FOUND AND FIXED: DEDICATED PROVISIONER DEFAULT
     DIALOGUE/ASSORTMENT CONFIGURATION.
     The bug was NOT in `TotalityNpcEntity.mobInteract` (audited first,
     per task instruction, and confirmed correct — it already checks
     `dialogueId != null` and routes into `DialogueSessionManager`
     exactly as designed). The real defect: `ProvisionerNpcEntity`'s
     constructor set `dialogueId` via `setDialogueId(GREETING_DIALOGUE)`,
     but `/summon` (and any other command/structure spawn) routes
     through `EntityType.loadEntityRecursive`, which ALWAYS calls
     `Entity#load` on the new entity — even with no explicit NBT
     argument — and `TotalityNpcEntity.readAdditionalSaveData` read
     `DialogueId` with `getStringOr(key, "")`, unconditionally resetting
     `dialogueId` to `null` whenever that key was absent from the
     compound tag. The constructor's default was silently overwritten
     moments after being set, on every command/structure spawn (natural
     mob spawning was unaffected — `finalizeSpawn` runs without ever
     calling `load` — which is why this bug was easy to miss). Fixed
     with a new `protected @Nullable Identifier defaultDialogueId()`
     hook on `TotalityNpcEntity` (returns `null` — unchanged behavior
     for the plain `totality:totality_npc` "blank test NPC," which
     still requires manual dialogue/shop configuration by design).
     `readAdditionalSaveData` now falls back to this hook ONLY when the
     NBT key is genuinely absent (`id.isEmpty() ? defaultDialogueId() :
     Identifier.tryParse(id)`) — an explicitly authored empty-string-
     excluded value always wins, and once the default is applied once,
     it gets WRITTEN back out by `addAdditionalSaveData` on the next
     save, so subsequent loads read it as an explicit value and never
     need the fallback again. `ProvisionerNpcEntity` overrides the hook
     to return `provisioner_greeting`; the constructor's own
     `setDialogueId` call was left in place (harmless — natural spawn
     and any test that constructs a `ProvisionerNpcEntity` directly
     without going through `load` still gets the default immediately,
     with no functional difference from the load-path fallback).

     `Merchant/shop/profile ID`: audited and found NOT applicable to
     Provisioner as designed — `OpenProvisionerShopAction` (the
     Provisioner's dialogue action, distinct from the generic
     `OpenShopAction(shopId)`) takes NO parameters at all and calls
     `TradeSessionManager.startEntityBackedTrade(player, npc)` directly;
     a Provisioner's trade flow never resolves a `ShopRegistry`
     `shop_id`. Its merchant identity is `MerchantRuntime.merchantId()`
     — `"provisioner/" + getUUID()` — which is ALWAYS well-formed and
     non-null by construction, never a separately-defaultable field.
     `AssortmentPoolId` already defaulted correctly (via
     `getStringOr(key, DEFAULT_ASSORTMENT_POOL_ID.toString())`, which
     supplies its default value directly rather than nulling first) —
     confirmed by a new direct test, no code change needed there.

     `provisioner_greeting.json` (already authored in 12g) was
     confirmed to have a valid Trade choice using the
     `open_provisioner_shop` action at its start state — verified
     directly against the real, datapack-loaded `DialogueRegistry`.

     Six new `ProvisionerVerification` checks: new Provisioner defaults
     `dialogueId` when NBT has no such key (the actual regression
     test for this bug — drives the real `readAdditionalSaveData`
     chain via new test-only `simulateFullLoadForTest`/
     `simulateFullSaveForTest` hooks against a genuinely empty
     `ValueInput`, not just the constructor); a fresh Provisioner's
     `merchantId()` is always well-formed; `AssortmentPoolId` defaults
     when absent; explicit authored `DialogueId`/`AssortmentPoolId`
     are never overwritten; a defaulted `dialogueId` survives a real
     save/load round-trip; `provisioner_greeting` has a valid Trade
     option. Requirement 7 ("trade resolution uses the entity-backed
     runtime and stock") was already covered by 12g's existing
     `checkEntityBackedSessionResolvesEntityRuntime` and the new
     delayed smoke test (item 9 below) — not duplicated again here.

  2. NEGATIVE PERSISTED MERCHANT CREDITS / INVALID MERCHANT STATE.
     `ProvisionerNpcEntity.readPhase3State`: a negative persisted
     `CurrentCredits` is now sanitized to `0` with one clear `ERROR`
     log line, WITHOUT touching `CreditsInitialized` — a genuinely
     initialized-but-corrupted balance degrades to 0, never silently
     rerolls back to the 300-Credit baseline (which only happens
     through the ordinary `!creditsInitialized` path, untouched by this
     fix). `ProvisionerNpcEntity.setCurrentCredits` now also marks
     `creditsInitialized = true` on every successful call, including
     `setCurrentCredits(0)` — previously a caller setting exactly 0
     right after construction would leave the initialized marker false,
     and the next `ensureCreditsAndStockInitialized()` call would
     silently replace the caller's explicit 0 with 300.
     `TradeSessionManager.handleBuy` (template-backed) and the new
     `handleStockBuy` (Phase 3, stock-backed) both now reject with a
     new `BuyResult.Reason.INVALID_MERCHANT_STATE` if
     `merchant.currentCredits() < 0`, checked before any payment/
     mutation — should never actually trigger post-hardening (the two
     fixes above close both ways a negative balance could arise), kept
     as defense-in-depth. Four new checks: negative persisted Credits
     sanitize to 0 without restoring the baseline;
     `setCurrentCredits(0)` marks initialized and blocks the later
     baseline; a stock-backed BUY against a corrupt-negative merchant
     is rejected with `INVALID_MERCHANT_STATE` and changes nothing; the
     same for a template-backed BUY (a hand-built `MerchantRuntime`
     anonymous class, since neither `InMemoryMerchantRuntime` nor
     `ProvisionerNpcEntity`'s real setter can normally reach negative —
     confirming the check is real defense-in-depth, not dead code).

  3. MISSING-ASSORTMENT-POOL RETRY THROTTLING.
     `ProvisionerNpcEntity.customServerAiStep` previously called
     `ensureCreditsAndStockInitialized()` (which logs an `ERROR` on a
     missing/invalid pool) unconditionally every tick — a genuinely
     missing pool would log ~20 times per second indefinitely. Fixed
     with two new transient (NOT NBT-persisted — deliberately, per
     task instruction) fields: `stockInitFailureLogged` (logs the
     failure exactly once, cleared automatically on a later successful
     initialization) and `ticksUntilNextStockInitAttempt` (throttles
     the actual RETRY, not just the log line, to once every 200 ticks —
     `STOCK_INIT_RETRY_INTERVAL_TICKS`). Documented explicitly as
     protection against initialization ordering and future
     compatibility, NOT a promise of live `/reload` support (the
     underlying registries don't offer one). One new check exercises
     the full throttle lifecycle end-to-end via a new test-only
     `runAiStepForTest(ServerLevel)` hook (drives the real
     `customServerAiStep`, safe on an isolated never-added entity since
     the inherited per-tick safety net short-circuits with no
     interaction partner): a bad pool ID fails once; 5 further
     simulated ticks do NOT retry (proving the throttle, not just the
     log, is real); switching to a valid pool ID and advancing through
     the remaining throttle window initializes exactly once. The runtime
     log confirmed this directly — the SAME throttled entity across 205
     total simulated ticks produced exactly ONE `ERROR` line.

  4. ASSORTMENT WEIGHT OVERFLOW / EXCESSIVE ROLL COUNT.
     `ProvisionerAssortmentPool.validate()` already accumulated a
     selection group's total weight via checked `long` arithmetic, but
     never checked the accumulated total against `Integer.MAX_VALUE` —
     `AssortmentSelectionGroup.pick`'s own weighted-selection math uses
     `int` (bounded by `random.nextInt(int)`), so a positive set of
     individually-valid weights summing past that ceiling would pass
     validation and silently wrap during generation. Now rejected at
     validation time. Also added `MAX_GROUP_ROLLS = 1000`, a documented
     datapack-safety bound (explicitly NOT a gameplay-balance value) on
     a single group's roll count — guards especially against a
     malformed `distinct = false` group (which never exhausts its pool)
     generating an enormous list. Two new checks: a two-entry group with
     `Integer.MAX_VALUE` weight each is rejected; a `distinct = false`
     group requesting 1,000,000 rolls is rejected.

  5. ASSORTMENT DEFINITIONS MADE IMMUTABLE.
     `AssortmentItemEntry`'s canonical constructor now rejects a null
     `ItemStack` and stores its OWN defensive copy (never the caller's
     reference); its `item()` accessor returns a further defensive copy
     on every read. Deliberately does NOT normalize the authored count
     — `ProvisionerAssortmentPool.validate()` needs to see the authored
     count as-is to reject a malformed entry (count != 1) as an
     authoring error, not have it silently coerced away first.
     `WeightedAssortmentEntry`/`ChanceAssortmentEntry` now reject a null
     `entry`. `AssortmentSelectionGroup`/`ProvisionerAssortmentPool` both
     now reject null lists and store `List.copyOf(...)` snapshots in
     their canonical constructors (which also rejects null elements for
     free) — a caller's original mutable list reference, or one shared
     across multiple pool definitions, can no longer change what a
     Provisioner rolls after construction. Three new checks: mutating an
     `ItemStack` returned from `AssortmentItemEntry.item()` doesn't
     affect a later read; clearing/changing the original constructor
     list doesn't affect an already-built pool; a pool's exposed lists
     (`guaranteed()`/`selectionGroups()`/a group's `entries()`) all
     throw `UnsupportedOperationException` on an attempted `.add()`.

     INCIDENTAL FIX FOUND WHILE WRITING THESE TESTS:
     `MerchantStockEntry`'s canonical constructor called
     `item.setCount(1)` unconditionally whenever a non-empty-count item
     was supplied — but `ItemStack#copy()`'s own fast path for an EMPTY
     stack returns the SHARED `ItemStack.EMPTY` singleton, not a fresh
     instance. Calling `setCount(1)` on that singleton (which the
     Section 7 "empty item template" tests below deliberately construct
     via `new MerchantStockEntry(ItemStack.EMPTY, ...)`) would have
     mutated `ItemStack.EMPTY`'s internal count in place for the
     remainder of the JVM session — a real, previously-latent landmine
     that could corrupt completely unrelated code assuming
     `ItemStack.EMPTY.getCount() == 0`, discovered specifically because
     this pass needed to safely construct that exact case. Fixed by
     guarding the `setCount` call with `!item.isEmpty()`.

  6. EXPLICIT BUY QUANTITY VALIDATION (0/negative/>100 REJECTED,
     NOT CLAMPED).
     Both `TradeSessionManager.handleBuy` (template-backed) and
     `handleStockBuy` (stock-backed) previously used
     `quantity = Math.max(1, Math.min(100, quantity))`, silently turning
     0/negative into 1 and anything above 100 into 100. Replaced with an
     explicit `if (quantity < 1 || quantity > 100) return
     BuyResult.rejected(BuyResult.Reason.INVALID_QUANTITY);` in both
     methods, checked before price calculation, affordability,
     payment, item delivery, and any merchant-Credit or stock mutation.
     `BuyItemHandler` now also logs a `WARN` for `INVALID_QUANTITY`
     (alongside the existing `INVALID_INDEX`/`OVERFLOW`) — the real BUY
     UI already clamps client-side and can never produce this
     server-side. Two new checks (one per BUY path) each drive all four
     invalid quantities (`0`, `-1`, `101`, `Integer.MAX_VALUE`) in one
     session and assert every one is rejected with `INVALID_QUANTITY`
     and changes nothing (player wallet, merchant Credits, and stock —
     where applicable — all unchanged).

  7. ALL-OR-NOTHING PERSISTED STOCK VALIDATION.
     `ProvisionerNpcEntity.readPhase3State` already benefited from the
     `MerchantStockEntry.CODEC` list decode failing as a whole for a
     STRUCTURALLY malformed entry (e.g. negative `current_stock`, out of
     the codec's own `Codec.intRange`). This pass adds a second,
     SEMANTIC validation pass (`isValidPersistedStock`, new, `public`
     specifically so `ProvisionerVerification` can drive it directly
     against hand-built lists) over a structurally-valid-but-corrupt
     list: an empty item template, or a duplicate item+components
     entry. Either violation discards the ENTIRE persisted list (never
     a partial valid subset) with one clear `ERROR` log line;
     `StockInitialized` is read completely independently and preserved
     exactly as persisted either way, so corruption degrades to "this
     Provisioner presents as fully sold out," never a reroll or a
     duplication. Four new checks: `isValidPersistedStock` rejects a
     list containing `ItemStack.EMPTY` (see item 5's incidental fix,
     without which this test would have been unsafe to write at all);
     rejects duplicate item+component entries while accepting distinct
     ones; rejects a list mixing one valid and one invalid entry in its
     ENTIRETY, not partially; and a full NBT round-trip with a corrupt
     (duplicate) but codec-encodable persisted stock confirms the
     reloaded entity ends up with empty stock AND `stockInitialized`
     still `true` (degrades to sold-out, never rerolls).

  8. LIVE-ENTITY RE-RESOLUTION — CONFIRMED UNCHANGED, NOT REGRESSED.
     `TradeSessionManager.revalidateNpc`/`currentNpc`/`currentMerchant`/
     `currentStockProvider`/`resolveRecordedNpc`/`matchesUuid`/
     `releaseTradePartner` (12g's Part H/hardening-pass guarantees) were
     NOT modified by this pass — the new `INVALID_MERCHANT_STATE`/
     `INVALID_QUANTITY` checks were added strictly AFTER the existing
     `revalidateNpc`/live-entity-resolution calls in both `handleBuy`
     and `handleStockBuy`, never before or in place of them. Confirmed
     by re-reading every call site and by every existing entity-backed
     check in `ProvisionerVerification`/`MerchantSellVerification`
     still passing unchanged.

  9. DELAYED REAL ENTITY-BACKED TRANSACTION SMOKE TEST — ADDED (not
     merely documented as deferred).
     Every check in `ProvisionerVerification` runs at
     `SERVER_STARTED`, before the server has processed a single tick —
     at which point a freshly `addFreshEntity`'d entity is provably NOT
     yet visible to `Level#getEntity(int)` (a real, previously-hit
     limitation, documented at length in 12g). A NEW suite,
     `ProvisionerEntityBackedSmokeTest`, uses the existing
     `ServerScheduler` (pre-existing infrastructure, previously
     registered but unused) to queue a genuine one-shot check 40 ticks
     (~2 seconds) after server start — long enough that a spawned
     entity is resolvable the normal way. It spawns a real
     `ProvisionerNpcEntity` near a `TotalityFakePlayer`, confirms it
     resolves via the exact same `Level#getEntity(int)` call
     `DialogueSessionManager#getActiveNpc` uses in real play, then
     drives one real BUY and one real SELL against it via the actual
     production `TradeSessionManager.startEntityBackedTrade`/
     `handleBuy`/`handleSell` entry points (not the verification-only
     stock-backed shortcut) — confirming Credits and stock actually
     change on the live entity, and that the sold item never becomes
     ordinary BUY stock. Cleans up the spawned entity, session, wallet,
     and inventory slot in `finally`, leaving nothing behind. This does
     NOT simulate an actual mouse click or dialogue UI — it starts the
     session the same way `OpenProvisionerShopAction` does, which is
     the full extent of what is verifiable without a real client
     input-injection tool (this environment has none, confirmed again
     this pass). The still-manual step remains Stefan physically
     right-clicking the NPC and watching Dialogue/Trading open on
     screen (Section 10's checklist, below).

  10. `gradlew compileJava`: SUCCESSFUL on first attempt after this
      pass's changes.

  11. RUNTIME-VERIFIED (2026-07-14, `gradlew runClient
      --args="--quickPlaySingleplayer \"Testing World\""`, integrated
      server, world auto-loaded, player joined): `Parsed 20 item value
      rules: 20 accepted, 0 rejected, across 19 items`;
      `[ItemValueVerification] All 19 self-test checks passed.`;
      `[MerchantSellVerification] All 33 self-test checks passed.`;
      `Loaded 1 provisioner assortment pool(s), 0 rejected`;
      `[ProvisionerVerification] All 67 self-test checks passed.`
      (was 45 — +22 new Phase 3 hardening checks); ~2 seconds after
      world join, `[ProvisionerEntityBackedSmokeTest] All 4 self-test
      checks passed.` — 123 checks total across all four suites, zero
      failures. Exactly four `ERROR` lines in the entire log, ALL
      expected and self-generated by this pass's own synthetic
      negative-tests (negative-Credits sanitize, corrupt-duplicate-
      stock discard, and two distinct missing-pool retry checks, one of
      which is 12g's pre-existing `checkMissingPoolDoesNotMarkStock
      Initialized`) — no unrelated, unexpected `WARN`/`ERROR` lines
      anywhere in the `totality` namespace (confirmed by grep across
      the full log), and the two missing-pool `ERROR` lines each fired
      EXACTLY ONCE despite one of them being driven through 205
      simulated tick attempts — direct runtime confirmation that
      Section 3's retry throttle actually suppresses repeat logging,
      not just in theory. Zero `PASS` lines printed by default —
      concise logging remains intact.

  12. MANUAL, INTERACTIVE VERIFICATION (Section 10 checklist) —
      HONESTLY LIMITED, same standing limitation as every prior phase:
      this environment cannot drive the actual Minecraft client window
      (no input-injection tool). The 15-item manual checklist in the
      task instruction (`/summon`, right-click, Dialogue opens, Trade
      option present, Trading Screen opens, buy/sell, stock/Credits
      changes, reopen without reroll, save/reload persistence, two
      independent Provisioners) was NOT performed by Claude Code this
      session and must not be claimed as passed. The "Testing World"
      client was left running (world loaded, player joined, all
      automated suites green) at the end of this session specifically
      so Stefan can drive this checklist manually without needing to
      relaunch. Item 9's delayed smoke test covers the same
      transactional guarantees at the code level against a REAL live
      entity (not just an isolated fixture) — genuinely closing the
      gap 12g left open — but does not and cannot substitute for
      Stefan actually seeing Dialogue/Trading open on screen.

  NOT implemented, unchanged from scope (Phase 4 and later, still
  explicitly deferred): the full Trading Screen visual redesign,
  BUYBACK, buyback inventory, restocking, assortment rotation, Credit
  replenishment, the tutorial quest, a proximity quest trigger,
  Relationship, reputation, Persuasion modifiers, Investment,
  Pickpocket, Contacts, Blacksmith, Alchemist, natural Provisioner
  spawning, the Provisioner Trading Post structure, village
  integration, home position/wander-radius, a broader transaction
  ledger, refund history, an idempotency framework, and the unrelated
  dedicated-server client-classloading fix.

--------------------------------------------------------------------------------
13. Phase 4 — Trading Screen redesign + Provisioner male/female textures
    (2026-07-14, later still)
--------------------------------------------------------------------------------
  Completes Section 11's remaining UI step (the full Trading Screen
  visual/functional redesign) and adds the Provisioner's dedicated
  male/female renderer. Does NOT implement BUYBACK, restocking, the
  tutorial quest, or natural Provisioner spawning — all still
  explicitly deferred, per task instruction.

  1. QUANTITY BUG ROOT CAUSE (audited and reproduced via code review, not
     live play — this environment has no client input-injection tool).
     Manually confirmed symptom: the quantity slider visually reached
     100, but only a quantity of 1 ever successfully bought. The bug
     was NOT in `BuyItemPayload`, session state, or a hardcoded-1
     regression — the OLD `TradingScreen` genuinely read and
     transmitted whatever quantity the slider held. The actual defect:
     the old screen's quantity cap was a flat `MAX_QUANTITY = 100`
     constant that NEVER read `ShopEntryDisplayData.limitedStock()`/
     `availableStock()` at all (fields Phase 3 Part J already added
     specifically for this purpose) — so the player could freely
     select a quantity above a Provisioner entry's REAL current stock.
     The server correctly rejected the resulting BUY
     (`OUT_OF_STOCK`), but the old screen had ZERO rejection-feedback
     path (nothing was sent to the client on failure at all before
     this phase), so an over-quantity purchase simply appeared to
     silently do nothing — reproducing the exact reported symptom for
     any entry whose real stock was below the selected quantity. Fixed
     by (a) a new `TradingQuantityMath` (pure, dependency-free) that is
     now the ONE client-side source of truth for the valid quantity
     range — `min(100, availableStock-if-limited, maxAffordable)`,
     never a flat 100 — and (b) real rejection feedback (`TradeRejectionPayload`,
     item 4 below) so any future edge case is visible, not silent.

  2. PROVISIONER MALE/FEMALE TEXTURES. New `ProvisionerNpcRenderer`/
     `ProvisionerNpcRenderState` (`client/renderer/entity/npc/`,
     `client/renderer/entity/state/npc/`), following the EXACT
     `BankerNpcRenderer` pattern (a synced `gender` field on the render
     state, resolved to one of two textures in `getTextureLocation`) —
     its OWN texture pair, never the Banker's:
     `textures/entity/npc/provisioner/{male,female}.png` (already
     provided). `ModEntities.PROVISIONER`'s entity renderer registration
     in `TotalityClient` switched from the generic `TotalityNpcRenderer`
     to the new `ProvisionerNpcRenderer`. The gender-to-texture mapping
     is extracted as a pure `public static Identifier textureFor(NpcGender)`
     specifically so it's directly testable without a GL context; a
     `null` gender (defensive only — the render state field is never
     actually null in practice) falls back to the generic NPC texture
     rather than guessing or crashing. Generated gender/name behavior
     and persistence are completely unchanged — no new skin-category
     axis was introduced (Section 0/8 still holds).

  3. TRADING SCREEN — FULL REDESIGN (`screen/shop/TradingScreen.java`,
     rewritten). Visual direction from `trade_screen.png`, rendered in
     Totality's established flat-color near-black + cyan + gold/amber
     palette (no stone/gold texture art yet, same "logic now, visuals
     later" split as every other Totality screen). Layout: header
     (title, merchant name + archetype, merchant Credits, player
     Credits) → BUY/SELL/BUYBACK tabs → main content (BUY: scrollable
     stock catalog left + selected-item detail panel right; SELL:
     a hint panel left + selected-slot detail panel right) → a
     constant player-inventory grid along the bottom (vanilla-layout
     36 main slots, hotbar below 3 storage rows) → a rejection banner
     when relevant. All geometry is computed once per frame by a single
     `Layout` record shared by both drawing AND click-hit-testing code
     (the old screen had this duplicated inline in each method — a real
     source of subtle geometry mismatches; the new screen has exactly
     one geometry function per region). The OLD detached global
     quantity slider (its own dedicated row below the grid, sized only
     off a flat `MAX_QUANTITY = 100`) is REMOVED outright — quantity
     controls now live inside whichever detail panel is showing
     (BUY or SELL), per task instruction. BUYBACK is a visible, clickable
     tab showing a clear "Coming Later" placeholder — no buyback
     transaction or inventory of any kind exists.

     CREDITS SYMBOL: the task text specified "Ȼ" (U+023B), but this
     conflicts with an existing, deliberate project decision — Stefan
     previously reported Ȼ renders like a lowercase "e" in the
     Minecraft font, and every existing Credits display in the mod
     (Bank Teller, the old Trading Screen, `CreditsItem`) already uses
     "₵" (U+20B5, CEDI SIGN) for exactly that reason. Flagged and
     confirmed with Stefan before implementation; the new screen uses
     ₵, matching the rest of the mod, not the task text's literal Ȼ.

  4. BUY MODE. Catalog cards show icon, (possibly enchantment-aware)
     name, unit price, current stock ONLY for `limitedStock()` entries
     (an unlimited legacy shop never shows a misleading finite count),
     selected/hover/sold-out states (sold-out cards are dimmed via a
     semi-transparent overlay, bordered red, and their click is
     consumed as a no-op rather than allowing selection). The catalog
     scrolls (mouse wheel) with a thin scrollbar indicator whenever more
     entries exist than fit the visible rows — no entry is ever
     silently omitted. The detail panel shows a larger icon, full name,
     vanilla/custom tooltip lines (unchanged from the old screen's
     approach — no fabricated descriptions), unit price, current stock
     (if limited), the shared quantity control (minus/box/plus, click
     the box to type a number directly), a checked-arithmetic total
     cost (`TradingQuantityMath.checkedTotal`, returns a sentinel rather
     than silently wrapping on overflow), and BUY/Cancel buttons.
     Selecting a new entry begins at quantity 1 when at least one unit
     is valid, 0 (BUY disabled) when sold out or genuinely unaffordable.
     A state refresh after a successful BUY preserves the current
     quantity if still valid, otherwise clamps DOWN — never up
     (`TradingQuantityMath.reconcileAfterRefresh`). The BUY packet
     always sends the exact displayed `buyQuantity` — never hardcoded.

  5. SELL MODE — now fully wired to Phase 2's backend. The player's own
     inventory (main storage + hotbar, vanilla layout) is always shown
     along the bottom; in SELL mode each slot is visually distinguished:
     empty (unobtrusive), rejected-category (red-tinted — determined
     CLIENT-SIDE via the vanilla-synced `ModTags.PROVISIONER_BUYS` item
     tag, needing no new networking at all), accepted-but-unvalued
     (dimmed — `ItemValueRegistry` is server-only data with NO
     client-side equivalent, so this needs the new
     `valuedInventorySlots` snapshot, item 6 below), sellable (normal/
     cyan-tinted), and the currently selected slot (cyan border,
     thicker frame). Clicking a sellable slot selects it and requests a
     live server quote (`RequestSellQuotePayload`/`SellQuoteResultPayload`,
     item 6) — the detail panel shows item, unit payout, the player's
     stack count, and (once the quote returns) the shared quantity
     control capped at the quote's own `effectiveMaxQuantity`
     (constrained by BOTH the stack count and the merchant's available
     Credits, exactly mirroring `MerchantSellQuoteView`), a checked
     total payout, and SELL/Cancel buttons. If the selected slot's
     stack changes identity or count while selected (item picked up,
     partially consumed elsewhere), the screen detects the mismatch
     against its last-requested snapshot and re-requests a fresh quote
     automatically — never silently stale. A successful SELL never adds
     anything to ordinary Provisioner stock (unchanged Phase 3
     guarantee, untouched by this phase).

  6. NETWORKING ADDITIONS (Part F) — extends existing state, does not
     invent a second protocol:
       - `ShowShopStatePayload` gained `merchantArchetype` (a real
         translatable `Component`, e.g. "Provisioner" — resolved from a
         new `MerchantRuntime.archetypeTranslationKey()` default method,
         `"totality.trading.archetype.merchant"` by default,
         overridden by `ProvisionerNpcEntity`) and
         `valuedInventorySlots` (the player's own inventory slot
         indices whose current stack has a resolvable central
         `ItemValueRegistry` value, recomputed every `sendState` call).
       - New C2S `RequestSellQuotePayload(slotIndex)` / S2C
         `SellQuoteResultPayload` (mirrors `MerchantSellQuoteView`'s own
         fields directly — one source of truth) — a NEW
         `TradeSessionManager.requestSellQuote` computes this without
         mutating anything, reusing the exact same
         `revalidateNpc`/`MerchantSellQuoteView.compute` machinery
         `handleSell` commits against, and piggybacks a full
         `sendState` refresh so browsing SELL mode keeps Credits/
         valued-slots reasonably current without a dedicated
         inventory-change watcher.
       - New S2C `TradeRejectionPayload(buy, reasonKey)` — a failed BUY
         or SELL previously produced ZERO client-visible feedback at
         all; `BuyItemHandler`/`SellItemHandler` now send this on every
         rejection. `reasonKey` is resolved SERVER-side by a new
         `TradeRejectionKeys` from the EXISTING `BuyResult.Reason`/
         `SellResult.Reason` enums (reused directly, no parallel
         rejection-code system) — for SELL's `NOT_SELLABLE`, the fixed,
         known `MerchantSellQuoteView` detail strings ("Merchant does
         not accept this item" / "no resolvable value" / "cannot
         afford that quantity") are matched to the correct specific key
         so the three distinct required messages don't collapse into
         one generic line. The client never receives or trusts raw
         server text for this, only the resolved key.
     `MerchantSellVerification`/`ProvisionerVerification`'s existing 97
     checks were unaffected — no existing payload's wire FORMAT for
     already-shipped fields changed, only new fields/payloads were
     added.

  7. UI-STATE SAFETY (Part G). No item selected → hint text, both
     modes. Empty/sold-out shop → catalog simply renders 0/all-dimmed
     cards, nothing crashes. Selected item sells out after a refresh →
     `applyUpdate` clamps `buyQuantity` down (to 0 if now sold out),
     confirm button disables itself from the same state. Player/
     merchant balance changes while open → both come from the same
     server-issued `ShowShopStatePayload` that already drives the
     header and afford checks, no separate cache to go stale.
     Inventory slot changes while SELL-selected, or the selected slot's
     item components change → detected and re-quoted automatically
     (item 5). Screen resize / GUI-scale change → all geometry is
     relative-fraction based off `width`/`height` (matching the old
     screen's approach), recomputed fresh every frame via one `Layout`
     function — nothing is cached across a resize. Session/merchant
     becomes invalid → the server's `ended=true` state push (unchanged
     existing mechanism) closes the screen exactly as before. Very long
     item names / large Credit balances → both use a scale-down-to-fit
     text helper (never scales up), and Credit values are formatted
     with thousands separators. Zero-price item → `TradingQuantityMath`
     explicitly branches before ever dividing by a unit price. More
     catalog entries than fit → scrollable, per item 4. Legacy
     unlimited shop / limited Provisioner shop → both paths already
     existed server-side (Phase 3 Part J); the new screen is simply the
     first client that actually reads `limitedStock()`/`availableStock()`
     at all.

  8. LOCALIZATION (Part H). All new user-visible strings added to
     `ModEnglishLangProvider` (this project's existing datagen-based
     lang pipeline — `gradlew runDatagen` regenerates
     `src/main/generated/assets/totality/lang/en_us.json`; there was no
     hand-written lang file to edit directly) under a new
     `totality.trading.*` key namespace — title, archetype labels
     (merchant/provisioner), Buy/Sell/Buyback, "Coming Later", Price
     Each/Payout Each/Stock/Quantity/Total Cost/Total Payout/Sold Out/
     Select an Item, and one `totality.trading.reject.*` key per
     distinct rejection message the task required (trade ended,
     invalid quantity, cannot afford, stock changed, item gone, not
     accepted, no value, merchant cannot afford, plus one generic
     fallback). No new user-visible English string is hardcoded
     directly into `TradingScreen`'s rendering code — every label goes
     through `Component.translatable(...)`.

  9. VERIFICATION (Part I) — TWO new suites, neither weakening the
     existing 97 checks (still 19 + 33 + 45; see item 10 below for why
     `ProvisionerVerification` itself grew further this phase too):
       - `TradingScreenVerification` (new, `api/shop/`, server-side —
         registered from `Totality.onInitialize`, same
         `VerificationReporter` convention): 14 checks covering every
         Part I item except the two texture checks — `TradingQuantityMath`'s
         BUY-quantity math (limited stock, the 100 server cap,
         affordability, zero-price/no-division, sold-out → 0,
         preserve-vs-clamp-down-never-up across a refresh, unlimited
         shops still capped at 100), quantity-16-not-1 transmission
         fidelity (`BuyItemPayload` field-level, plus the new
         `ShowShopStatePayload` fields' actual `STREAM_CODEC` wire
         round-trip — genuinely new hand-written serialization code
         that deserved a direct test), SELL max-quantity respecting
         stack count and merchant Credits (via `MerchantSellQuoteView`,
         unchanged from Phase 2/3 but exercised through the new
         `SellQuoteResultPayload` shape), `TradeRejectionKeys`'
         NOT_SELLABLE-detail-to-specific-key mapping, and a session-close
         regression guard (`endTrade` stops `isTrading`).
       - `ProvisionerRendererVerification` (new,
         `client/renderer/entity/npc/`, CLIENT-only — registered from
         `TotalityClient.onInitializeClient`, deliberately NOT part of
         the server-side suite): 4 checks — male → male texture,
         female → female texture, never the Banker's textures, and a
         null-gender fallback never throws. Kept client-only rather
         than folded into `ProvisionerVerification` specifically because
         `ProvisionerNpcRenderer` extends a client-only
         `HumanoidMobRenderer` — referencing it from a class that ALSO
         runs on `SERVER_STARTED` would risk a `NoClassDefFoundError` on
         a dedicated server if that environment's separate, pre-existing
         classloading issue (explicitly out of scope) is ever fixed
         independently; this phase introduces no new instance of that
         class of risk.

  10. `gradlew compileJava`: SUCCESSFUL on first attempt. `gradlew
      runDatagen`: SUCCESSFUL, regenerated the lang file with all 30
      new `totality.trading.*`/entity keys.

  11. RUNTIME-VERIFIED (2026-07-14, `gradlew runClient
      --args="--quickPlaySingleplayer \"Testing World\""`, integrated
      server, world auto-loaded, player joined):
      `[ProvisionerRendererVerification] All 4 self-test checks passed.`
      (client init, before any world);
      `[ItemValueVerification] All 19 self-test checks passed.`;
      `[MerchantSellVerification] All 33 self-test checks passed.`;
      `Loaded 1 provisioner assortment pool(s), 0 rejected`;
      `[ProvisionerVerification] All 67 self-test checks passed.`
      (unchanged from the Phase 3 hardening pass — nothing in Phase 4
      touched Provisioner assortment/stock/credits logic);
      `[TradingScreenVerification] All 14 self-test checks passed.`;
      ~2 seconds after world join,
      `[ProvisionerEntityBackedSmokeTest] All 4 self-test checks passed.`
      — 141 checks total across all six suites, zero failures. Exactly
      four `ERROR` lines in the entire log, all expected/self-generated
      by `ProvisionerVerification`'s own synthetic negative-test
      scenarios (unchanged carry-over from the Phase 3 hardening pass,
      re-confirmed still passing) — no unrelated, unexpected
      `WARN`/`ERROR` anywhere in the `totality` namespace. Zero `PASS`
      lines printed by default.

  12. MANUAL, INTERACTIVE VERIFICATION — HONESTLY LIMITED, same standing
      limitation as every prior phase: this environment cannot drive
      the actual Minecraft client window (no input-injection tool). The
      20-item manual checklist in the task instruction (spawn male/
      female Provisioners and confirm skins, open Dialogue → Trade,
      confirm header fields, select/quantity/buy/sold-out, SELL
      multiple items, rejected-item feedback, BUYBACK inertness, close-
      releases-lock, second Provisioner independence) was NOT performed
      by Claude Code this session and must not be claimed as passed.
      The "Testing World" client was left running (world loaded, player
      joined, all six automated suites green) specifically for Stefan
      to drive this checklist manually.

  NOT implemented, explicitly out of scope per task instruction:
  functional BUYBACK, buyback history, restocking, assortment
  rotation, merchant-Credit replenishment, the tutorial quest, the
  Provisioner Trading Post structure, natural Provisioner spawning,
  village integration, home/wander-radius, Relationship, reputation,
  Persuasion price modifiers, Investment, Pickpocket, Contacts,
  Blacksmith, Alchemist, an economy ledger/refund framework, and the
  unrelated dedicated-server client-classloading fix.

--------------------------------------------------------------------------------
14. Phase 4 correction pass — accountless SELL payout, Provisioner
    persistence (2026-07-16, after the MC 26.2 migration closeout)
--------------------------------------------------------------------------------
  First correction pass following Stefan's manual test of Phase 4 (13,
  above). Two economy/entity bugs, kept deliberately separate from the
  same pass's unrelated combat/input corrections (documented in
  `TOTALITY_COMBAT_INPUT_AND_HUD.md`, Sections 1-2). Does NOT begin the
  Trading Screen visual/layout pass — still deferred to a later,
  dedicated Fable session per task instruction.

  14a. ACCOUNTLESS SELL PAYOUT (Part A).
  ROOT CAUSE: `TradeSessionManager.handleSell` always called
  `CreditPaymentHelper.receive` (the Wallet/account credit path)
  regardless of whether the selling player had ever opened a bank
  account. `WalletComponent` exists on every player unconditionally
  (Section 0/2a already established this — it is NOT gated behind
  account creation), so its presence was never a valid signal of
  account ownership. The real signal already existed, just unused by
  SELL: the `has_account` narrative flag the Banker's own
  `open_account` dialogue actions set (`banker_intro.json`).

  FIX: `CreditPaymentHelper.hasOpenAccount(ServerPlayer)` (new) reads
  that exact flag via `DialogueComponents.FLAGS`. `handleSell` now
  branches: an account holder still receives the payout via the
  existing `receive`/`canReceive` (Wallet) path, unchanged; a player
  with no account receives it via two new symmetric methods,
  `CreditPaymentHelper.receivePhysical`/`canReceivePhysical`, which
  deliver physical `totality:credits` items via
  `CurrencyItems.CREDITS.createStacks(amount)` — the SAME
  `MAX_PER_STACK`-splitting + "add to inventory, drop at feet only if
  full" fallback `BankTellerHandler.withdraw` and BUY's item delivery
  already use, not a new ground-drop mechanism invented for this path.
  Validation (`canReceive`/`canReceivePhysical`) still happens BEFORE
  `inventory.removeItem` — the existing validate-then-mutate ordering
  is unchanged, just branched on account status. SELL never sets
  `has_account` itself (no implicit account opening). BUY is completely
  untouched — `CreditPaymentHelper.canAfford`/`pay` (Wallet-then-
  physical combined spend) still gate BUY exactly as before.

  New checks added to `MerchantSellVerification`: accountless SELL
  pays physical Credits and leaves the Wallet unchanged; accountless
  SELL never sets `has_account`; `receivePhysical` rejects a negative
  amount without mutation; `receivePhysical` delivers a large payout
  (250,000, spanning 25 stacks) without overflow/exception. Every
  pre-existing Wallet-payout SELL check (`checkSuccessfulSellPaysExactAmount`
  etc.) still passes unchanged — the suite's shared fixture player is
  now explicitly marked an account holder at suite start (matching
  what those checks always implicitly assumed), and the new checks
  flip the flag off/on around themselves, restoring it in `finally`.

  14b. PROVISIONER PERSISTENCE / NO NATURAL DESPAWN (Part B).
  ROOT CAUSE: `ProvisionerNpcEntity` (and `TotalityNpcEntity` generally)
  never overrode `Mob.isPersistenceRequired()`. Confirmed via
  decompiled 26.2 bytecode that `Mob.checkDespawn()` unconditionally
  discards a mob once `Level.getNearestPlayer` finds the nearest player
  beyond `MobCategory.getDespawnDistance()` (128 blocks for `MISC`,
  the Provisioner's category) — no randomness gates this branch, unlike
  the separate long-distance random-despawn branch. A player dying and
  respawning far away is exactly this scenario: the Provisioner's chunk
  is often still loaded for a moment post-respawn, `checkDespawn` runs,
  finds the player far away, and discards it immediately.

  FIX: `ProvisionerNpcEntity.isPersistenceRequired()` now unconditionally
  returns `true` — an override, not just calling the inherited
  `setPersistenceRequired()` once, so the invariant holds regardless of
  what NBT happens to contain on load (mirrors `defaultDialogueId()`'s
  "always enforce this invariant" approach). This is the ENTIRE fix —
  `Mob.checkDespawn()`'s own vanilla logic already skips its whole
  distance-despawn branch when `isPersistenceRequired()` is true; no
  other method needed changing. Death is unaffected (a Provisioner
  still dies normally when actually killed — nothing about
  invulnerability/damage was touched); NBT save/load, stock, and
  Credits persistence were already correct (Phase 3) and untouched.

  New checks added to `ProvisionerVerification`: a fresh Provisioner is
  always persistence-required; a level-added Provisioner survives a
  REAL `checkDespawn()` call 100,000 blocks from the only registered
  player (drives vanilla's actual, unmodified method — not a
  reimplementation); a negative CONTROL check confirms a plain
  `totality:totality_npc` (still not persistence-required, unaffected
  by this fix, matching this document's declared scope of Provisioner
  only) genuinely DOES despawn under the identical condition, proving
  the positive check isn't vacuously passing; a full save/load round
  trip (gender, dialogue id, assortment pool id, Credits, and stock
  together, via the real `addAdditionalSaveData`/`readAdditionalSaveData`
  chain, not just the Phase-3-only fields) preserves all of it at once.
  Zero/zero-stock survival, no-reroll-on-reload, and two-Provisioner
  independence were already covered by Phase 3's existing checks — not
  duplicated here.

  RUNTIME-VERIFIED (2026-07-16, `gradlew runClient
  --args="--quickPlaySingleplayer \"New Testing World\""`):
  `[MerchantSellVerification] All 37 self-test checks passed.` (was
  33 — +4 new Part A checks); `[ProvisionerVerification] All 71
  self-test checks passed.` (was 67 — +4 new Part B checks); every
  other pre-existing suite (`ItemValueVerification` 19,
  `TradingScreenVerification` 14, `ProvisionerEntityBackedSmokeTest` 4)
  unchanged and passing. `gradlew compileJava`/`build`: SUCCESSFUL.

  MANUAL TESTING: Stefan confirmed, 2026-07-16 — accountless SELL
  correctly provides physical Credits; account holders retain the
  intended Wallet payout; Provisioners remain persistent after moving
  away, death/respawn, and chunk unloading.

  NOT implemented, unchanged from scope: everything Section 13's "NOT
  implemented" list already covers, plus this pass adds nothing new to
  that list — no GUI work was touched (Trading Screen remains exactly
  as Section 13 left it, deferred to the Fable pass).

--------------------------------------------------------------------------------
15. Phase 4 — Trading Screen GUI refinement pass (2026-07-16, the
    "Fable pass" Sections 13/14 deferred to)
--------------------------------------------------------------------------------
  Native Minecraft rendering only — flat-color panels, normal item/text
  rendering, scissor regions, hover tooltips. The FULL custom textured
  GUI overhaul (background art, portrait system, decorative borders —
  the trade_screen_*_v1 mockups' visual polish) remains EXPLICITLY
  DEFERRED; those mockups were consulted for information hierarchy
  only. This pass is a client GUI task — TradeSessionManager,
  CreditPaymentHelper, stock/Credits storage, payloads, and every
  server-authoritative rule are untouched; NO backend or networking
  change was needed (all rejection-reason data was already
  client-available: the vanilla-synced #totality:provisioner_buys tag
  and ShowShopStatePayload.valuedInventorySlots).

  15a. GUI SCALE 4 LAYOUT — ROOT CAUSE AND FIX.
  At GUI Scale 4 on 1080p (480x270 logical) the old layout's fixed
  margins (width/20, height/14), 30px header, and 20px tabs around the
  fixed 102px inventory block left only 68px of content height. The
  BUY detail panel's top-down field flow (~52px) and bottom-anchored
  quantity/total/button controls (~46px) need ~98px combined — they
  physically overlapped. The binding constraint was VERTICAL, not
  horizontal (the 48%/52% catalog/detail split is fine at 480 wide).
  Fixed by the new pure `TradingScreenLayout` (`api/shop/`, sibling to
  `TradingQuantityMath`, zero client dependencies so
  `TradingScreenVerification` exercises the REAL layout math
  server-side): two-pass compute — normal metrics first, compact
  fallback (8/4px margins, 24px header, 16px tabs) whenever content
  height would fall below MIN_CONTENT_H (100px). At 480x270 compact
  yields 108px; larger screens never trigger it and keep their exact
  previous proportions. Additionally the detail panels now reserve a
  fixed bottom action band (DETAIL_BOTTOM_BAND_H = 46px) and both skip
  and scissor their top field flow at the band edge — no window size
  can make fields and controls overlap, even below the supported
  minimum. All geometry (regions, catalog cards, inventory slots) is
  computed fresh every frame/input event from one source; nothing is
  cached across resizes.

  15b. EXPLICIT SOLD-OUT PRESENTATION.
  Root cause of the old unclear state: the 0xB0 dark overlay was drawn
  AFTER the card's own "Sold Out" text, washing out its own label.
  Now: overlay first, then a crisp centered red "SOLD OUT" label
  (upper-cased from the existing totality.trading.sold_out key — no
  new key) over the dimmed card, red border drawn last, price shown
  dimmed. Sold-out entries are now SELECTABLE view-only (previously
  the click was swallowed): max quantity is 0, so quantity controls
  render inactive, BUY stays disabled, no payload is possible; the
  detail panel adds its own explicit red "Sold Out" line, and the
  card's hover tooltip repeats it. The entry never disappears from
  the catalog (Phase 3's zero-stock-stays-in-position guarantee,
  unchanged). No restocking.

  15c. REJECTED-SELL-ITEM EXPLANATIONS.
  Rejected items previously showed only an unexplained red tint. Now,
  in SELL mode: hovering ANY inventory item shows a tooltip (item name
  + status line) via the established setComponentTooltipForNextFrame
  pattern — a rejected-category item states "Merchant does not buy
  this type of item" (totality.trading.reject.not_accepted), an
  accepted-but-unvalued item states "This item has no known value"
  (totality.trading.reject.no_value) — the SAME keys the server's own
  TradeRejectionKeys resolves for a committed-SELL rejection, chosen
  by the new pure TradingScreenLayout.sellSlotIssueKey (acceptance
  outranks value), driven purely by server-derived data — no
  client-invented acceptance rule. Rejected slots additionally carry
  a corner "x" badge (trade_screen_sell_v1's red-X hierarchy cue,
  rendered as text, no art). Selecting a rejected item still routes
  through the live server quote and shows the reason in the detail
  panel (existing short keys, unchanged); its detail panel now also
  keeps the Cancel button available (previously rejected/loading
  selections had no buttons at all). Sellable items hover a new
  "Click to select for sale" hint (totality.trading.sellable_hint,
  new key). Rejected items remain fully visible, never hidden, and
  can never submit a SELL (no quantity controls exist for them; the
  server revalidates at commit regardless).

  15d. OTHER RESILIENCE/INTERACTION FIXES (each a real found defect):
    - Credit values now thousands-grouped EVERYWHERE (cards, Price/
      Payout Each, totals) via TradingScreenLayout.formatCredits —
      previously only the header balances were formatted. "Your
      Credits" retains its established meaning: Wallet + physical
      (total spendable funds, matching CreditPaymentHelper.canAfford).
    - Mouse wheel now scrolls the catalog only while the pointer is
      over it (previously it scrolled from anywhere on the screen).
    - Catalog click hit-testing now requires the click inside the
      catalog viewport — previously a partially-scrolled-out card's
      unclipped hitbox could catch clicks over the tabs/inventory
      area (rendered-position vs hitbox mismatch).
    - The minus button rendered disabled at quantity 1 but its click
      still decremented to 0 — all quantity-control hitboxes now obey
      the exact enabled predicates the rendering shows, via one shared
      geometry source per control (catalogCardRect/inventorySlotRect).
    - applyUpdate/applySellQuote dropped their Math.max(quantity, 1)
      bump — it could RAISE a clamped-to-0 quantity back to 1 on a
      later refresh (e.g. merchant Credits recovering), violating
      "a refresh must never increase a player-selected quantity";
      reconcileAfterRefresh alone is strictly non-increasing. A
      typed quantity commit clamps into [1, max] when transacting is
      possible (typed "0" becomes 1).
    - A SELL selection whose stack disappears is now cleared outright
      (both on state refresh and the per-frame staleness check) —
      previously the empty slot could be re-quoted and misreport as
      "not accepted".
    - The rejection banner wraps within the panel (previously one long
      line could overrun it), sits at the top of the content band
      (never over quantity/action controls), auto-expires after 6
      seconds (Screen#tick), and still clears immediately on any new
      server state, selection change, or tab switch. Reopening never
      retains stale feedback (fresh screen instance per session).
    - SELL detail adds a "Merchant can afford: N" row (new key
      totality.trading.merchant_can_afford, trade_screen_sell_v1's
      hierarchy) when the merchant's Credits — not the stack size —
      are the binding quantity limit; the zero case keeps the existing
      red merchant_cannot_afford message.
    - BUYBACK: unchanged — visible, "(Coming Later)", inert; no
      transaction path exists for it (confirmAction only handles
      BUY/SELL).

  15d-2. MOCKUP-INFORMED SECOND ITERATION (same day — driven by
  Stefan's live GUI Scale 4 screenshot, Context/Trading Test/
  trade_screen2.png, taken against the first iteration):
    - CATALOG CARD INTERIOR: the first iteration kept the old
      centered-column card (name top, icon center, price bottom) —
      at real card sizes the price line drew straight through the
      item icon. Rebuilt as icon-left with name and price stacked to
      its right (the trade_screen_buy_v1 hierarchy adapted to wide
      cells), zero interior collisions at any card width.
    - CATALOG_ROW_H 44 -> 36: the taller cards were hollow, and at
      GUI Scale 4's 108px content height showed two-and-a-half rows
      with a chopped third; 36px rows show exactly three full rows.
    - AUTO-SELECT ON OPEN: the screen now opens with the first
      purchasable entry pre-selected (mockup behavior) instead of an
      empty "Select an Item" detail pane; a deliberate Cancel is
      never overridden by a later refresh.
    - INVENTORY PANEL MARGINS: "Your Inventory" label in the left
      margin and, in SELL mode, the mockup's red-items legend
      ("Red-marked items cannot be sold to this merchant") wrapped in
      the right margin — the side space was previously dead. Two more
      keys: totality.trading.your_inventory,
      totality.trading.rejected_legend (4 new keys total this pass).
    All suites re-run green after this iteration (TradingScreen still
    20 — the scroll-bounds check's literals updated for the 36px row).

  15d-3. REFERENCE-STYLE THIRD ITERATION (same day — Stefan confirmed
  the mechanics work but asked for closer fidelity to the
  trade_screen_buy_v1/sell_v1 references; screenshots trade_screen3/
  4.png record the pre-iteration state). Still native rendering only —
  the references' PALETTE and HIERARCHY, not their texture art:
    - PALETTE SHIFT: dark navy surfaces (panel 0xFF0A0E16, cells
      0xFF0D131D) with steel-cyan structural borders (0xFF2A4A5A)
      replacing the previous gold-dominant frames; gold is now
      reserved for the title, prices, and Credit values — the
      references' color language.
    - HEADER: large centered "TRADING" title (scaled 1.4x, 1.1x
      compact) with flanking accent lines, merchant name + archetype
      centered beneath, and a bordered two-row Credits box top-right
      with right-aligned gold values — replacing the small top-left
      text block. "Your Credits" meaning unchanged (Wallet +
      physical).
    - DETAIL PANELS: shared header (framed 20px icon box + name,
      divider with a small gold center accent), then label/value rows
      with RIGHT-ALIGNED values (Price/Payout Each, Stock, Merchant
      can afford) per the references' two-column row treatment.
    - QUANTITY ROW: now labeled ("Quantity:" left, compact -/box/+
      controls right-aligned); the box shows just the quantity (the
      max lives in the Stock/Merchant-can-afford rows, as in the
      references).
    - ACTION BUTTONS: full-width split pair, chunkier (15px) —
      BUY mode: gold "Cancel" + cyan "Buy"; SELL mode: red "Clear" +
      green "Sell" (the references' per-mode button colors). Bottom
      band row offsets shared as constants between drawing and click
      hit-testing.
    - SELL LEFT PANE: now instructions ("Select an item from Your
      Inventory below to offer it") + the red-items legend — replacing
      the second "Select an Item" placeholder that duplicated the
      detail panel's (Stefan's trade_screen4.png showed both panes
      identical and looking broken).
    - INVENTORY: "YOUR INVENTORY" upper-cased steel-cyan header in
      the left margin; ordinary sellable slots now use quiet frames —
      only problems are highlighted (rejected red + x, unvalued gold
      + x, selection bright cyan), matching the references' restraint.
    Three more keys: totality.trading.sell_instructions/.cancel/
    .clear (7 new keys total this pass). No layout-math change
    (TradingScreenLayout untouched this iteration); all suites re-run
    green (TradingScreen 20).

  15d-4. PER-MODE INVENTORY LAYOUTS (same day — Stefan: "much
  better," but asked for the references' actual inventory placement;
  trade_screen5.png records the pre-iteration state and its
  credits-box row overlap):
    - BUY/BUYBACK (trade_screen_buy_v1): the bottom inventory area is
      now a single HOTBAR-ROW strip (HOTBAR_STRIP_H = 30px), not the
      four-row block. Display-only, hover tooltips still work;
      storage slots simply have no on-screen location in these modes.
    - SELL (trade_screen_sell_v1): the LEFT panel now HOSTS the full
      four-row inventory grid ("YOUR INVENTORY" header above it, the
      red-items legend beneath it) and there is NO bottom strip — the
      content band runs to the panel bottom. In the layout math the
      SELL Regions' inventory() IS the catalog rect; the left panel's
      width is guaranteed to fit the 9-column grid (GRID_W) before
      the detail panel takes the remainder.
    - TradingScreenLayout.compute now takes a sellLayout flag
      (2-arg overload = BUY layout); freeing ~72px of vertical space
      means the COMPACT fallback no longer triggers at 480x270 at all
      (BUY contentH 140, SELL 170, both on normal metrics) — it
      remains for genuinely short windows (engages at e.g. 480x200)
      with its header raised 24->26px so the credits-box rows can
      never collide again (the trade_screen5.png overlap: two 8px
      rows at +2/+8 in an 18px box; rows now derive from box height).
    - The sell_instructions key from 15d-3 became unused (the grid
      replaced the instruction pane) and was removed — 6 net new keys
      this pass. CATALOG_ROW_H 36 -> 35.
    Verification updated, not just re-run: the region-overlap check
    now exercises BOTH per-mode layouts at every size (asserting
    SELL's inventory==catalog identity), and the Scale-4 check now
    asserts both modes fit on normal metrics, the SELL left panel
    fits GRID_W, and compact still engages at 480x200. All suites
    green (TradingScreen 20).

  15e. VERIFICATION. TradingScreenVerification 14 -> 20 (+6, all pure
  logic, no framebuffer dependency): region non-overlap at 480x270/
  640x360/960x540/1920x1080/320x240 with every region inside the
  panel; GUI Scale 4 triggers compact with contentH >= MIN_CONTENT_H
  while 1920x1080 stays non-compact; scroll clamp bounds (never
  negative, never past the last row, zero when everything fits);
  thousands-grouping preserves digits (locale-agnostic assertion);
  sellSlotIssueKey distinguishes not_accepted/no_value/sellable with
  acceptance outranking value; sold-out semantics (limited+zero
  exactly, entry stays listed, max quantity 0). RUNTIME-VERIFIED
  (2026-07-16, quickplay "New Testing World"): all suites green —
  ItemValue 19, MerchantSell 37, Provisioner 71, TradingScreen 20,
  PowerAttack 9, Keybind 3, ProvisionerRenderer 4, NotificationTiming
  9, PowerAttackFlash 7, and ProvisionerEntityBackedSmokeTest 4/4 (the
  known short-run timing flake did not trigger this run). gradlew
  compileJava/build: SUCCESSFUL. Datagen regenerated only the lang
  file (2 new keys).

  MANUAL TESTING: PENDING — Stefan's 30-item checklist (GUI Scale 4
  layout, sold-out presentation, rejected-item tooltips/reasons,
  scrolling, resize, Escape/lock release, regression spot-checks).
  Not claimed passed until Stefan reports.

--------------------------------------------------------------------------------
16. Phase 4 correction pass #3 — underfunded-merchant SELL confirmation,
    structured rejection reasons (2026-07-17)
--------------------------------------------------------------------------------
  Post-review correction pass, distinct from the "Phase 4 correction pass"
  in Section 14 (accountless payout/Provisioner persistence, 2026-07-16)
  and the GUI refinement pass in Section 15. Combat/input corrections from
  the SAME task are documented separately in
  `TOTALITY_COMBAT_INPUT_AND_HUD.md` Section 7, per the same
  deliberate-separation convention Sections 13-15 already established.

  16a. ROOT CAUSE OF THE SILENTLY-CLAMPED SELL VALUE.
  `MerchantSellQuoteView.compute` computed `maxQuantityByMerchant =
  merchant.currentCredits() / unitPayout` and then set
  `effectiveMaxQuantity = min(maxQuantityByStack, maxQuantityByMerchant)`
  — the SELECTABLE quantity itself was capped by merchant affordability,
  not just the payout. Selling 19 units at a merchant that could only
  afford 18 was therefore never actually representable: the UI could
  only ever select up to 18, and `totalPayout` was computed FROM that
  already-clamped quantity — there was no code path that ever computed
  "the real value of the quantity the player actually wanted" at all,
  so there was nothing to silently substitute; the substitution was
  structural (the wrong quantity was selectable in the first place),
  not a display-layer bug over a correct number.

  16b. FINAL DATA MODEL. `MerchantSellQuoteView` (`api/shop/`,
  rewritten) now separates SELECTION from AFFORDABILITY:
    - `maxQuantityByStack`/`effectiveMaxQuantity` — bounded by the
      player's stack count and the server cap (100) ONLY. Equal to each
      other whenever the merchant has any positive Credits; both 0 when
      the merchant has exactly zero Credits (Section 16d) or the item
      isn't sellable at all. Merchant affordability no longer bounds
      quantity SELECTION.
    - `totalValue` — the FULL quoted value of `requestedQuantity` units,
      computed from `unitPayout * requestedQuantity`, NEVER clamped to
      what the merchant can currently pay.
    - `merchantCredits` — the merchant's live balance snapshot (never
      negative; a corrupt negative balance reads as 0 here, matching
      `TradeSessionManager`'s existing separate refusal to transact
      against one at all).
    - `payableAmount = min(totalValue, merchantCredits)` — what the
      merchant can actually pay right now.
    - `forfeitedValue = totalValue - payableAmount` — what the player
      would forfeit by accepting the reduced payout. Zero whenever the
      merchant can fully afford the sale.
    - `requiresConfirmation = forfeitedValue > 0` (only meaningful when
      the quote is otherwise `sellable()` — a SEPARATE concern from
      sellability itself: an underfunded-but-sellable quote is still
      `sellable()`, it just additionally needs the player's explicit
      consent before committing).
    - `rejectionReason` — see Section 16f (structured, Part E).

  The Trading Screen's SELL detail panel (`screen/shop/TradingScreen.java`)
  now shows, per the canonical presentation:
    `Payout Each: ₵16`, `Total Value: ₵304` (the real, full, never-
    clamped quote — unchanged in principle from before, renamed from
    "Total Payout" to "Total Value" to match the task's exact wording),
    and — ONLY when `forfeitedValue > 0` — two additional rows,
    `Merchant Can Pay: ₵300` and `Forfeited: ₵4`, so the ordinary
    fully-funded case stays exactly as uncluttered as before.

  16c. CONFIRMATION PROTOCOL. Pressing SELL when the client's own
  locally-computed `totalValue > state.merchantCredits() > 0` opens a
  modal popup (`TradingScreen.PendingSellConfirmation` — slotIndex,
  quantity, totalValue, payableAmount captured at the moment SELL was
  pressed) instead of submitting anything: "These items are worth ₵304,
  but this merchant can only pay ₵300. Sell them for ₵300 anyway?" with
  `Cancel`/`Sell for ₵300` buttons (new `totality.trading.underfunded_confirm`/
  `totality.trading.sell_for` keys, dynamic — no hardcoded English in
  rendering code). The modal swallows all other input (clicks and Esc)
  until dismissed; Esc closes only the modal, never the whole Trading
  Screen (which would otherwise release the NPC interaction lock out
  from under an open confirmation).

    - CONFIRM: sends `SellItemPayload(slotIndex, quantity,
      confirmedReducedPayout=true, confirmedTotalValue, confirmedPayableAmount)`
      — a typed consent field plus the exact terms the player saw and
      accepted, per the task's "clearly typed confirmation field in the
      existing SELL request" option. The client-supplied amounts are
      NEVER trusted as authoritative — see Section 16e.
    - CANCEL: sends nothing. No items removed, no Credits moved, no
      merchant state changed — the popup simply closes.

  16d. ZERO-CREDIT MERCHANT. A merchant with `currentCredits <= 0` is a
  DISTINCT, non-confirmation-eligible case
  (`SellRejectionReason.MERCHANT_ZERO_CREDITS`) — `MerchantSellQuoteView.compute`
  returns `effectiveMaxQuantity = 0` and an explicit rejection reason
  the moment `merchantCredits <= 0` is observed, before quantity/value
  math even runs. The Trading Screen shows "This merchant has no
  Credits remaining." (new `totality.trading.merchant_zero_credits`
  key), disables quantity controls and the SELL button entirely, and
  never offers a confirmation popup — SELL for ₵0 is never presented as
  a legitimate transaction. No donation/disposal/trash/recycling/skill-
  XP behavior was added; that remains explicitly future scope per the
  task instruction.

  16e. SERVER AUTHORITY, ATOMICITY, AND STALE-CONFIRMATION PROTECTION.
  `TradeSessionManager.handleSell` (extended, new overload
  `handleSell(player, slotIndex, quantity, confirmedReducedPayout,
  confirmedTotalValue, confirmedPayableAmount)`; the original 3-arg
  method is now a thin `confirmedReducedPayout=false` delegate, so every
  pre-existing production/verification call site is unaffected)
  RECOMPUTES `MerchantSellQuoteView` fresh from live server state on
  every call — session, NPC revalidation, slot, stack, acceptance,
  value, and merchant Credits are all re-read exactly as before. If the
  freshly recomputed quote `requiresConfirmation()`:
    - no `confirmedReducedPayout` -> rejected
      (`SellResult.Reason.CONFIRMATION_REQUIRED`), nothing mutated.
    - `confirmedReducedPayout` present but `confirmedTotalValue`/
      `confirmedPayableAmount` do NOT exactly match the just-recomputed
      `quote.totalValue()`/`quote.payableAmount()` -> rejected as stale
      (`SellResult.Reason.STALE_CONFIRMATION`), nothing mutated. This is
      the conservative EXACT-MATCH check the task asked for (over a
      revision-counter scheme) — any change to merchant Credits, price,
      inventory, or requested quantity between popup-open and click
      changes the recomputed values and is therefore caught. The
      client-supplied amounts are used ONLY to prove what the player
      confirmed, never as the authoritative payout.
    - Matching confirmation -> commits at `payableAmount` (== the
      merchant's current Credits exactly), removing the FULL requested
      quantity, paying the player `payableAmount`, and reducing merchant
      Credits to exactly 0 — one atomic commit, same "validate
      everything, mutate nothing until it can't fail" discipline the
      unconfirmed path already used.
  A fully-funded SELL (`requiresConfirmation() == false`) is completely
  unaffected — same validation order, same atomic commit, at the full
  `totalValue` — as before this pass.

  16f. STRUCTURED SELL REJECTION REASONS (Part E). New
  `SellRejectionReason` enum (`api/shop/`) — `NONE`, `NOT_ACCEPTED`,
  `NO_VALUE`, `MERCHANT_ZERO_CREDITS`, `INVALID_QUANTITY`, `OVERFLOW`,
  `GENERIC` — produced directly by `MerchantSellQuoteView.compute`
  (never derived from prose). `SellResult` (`api/shop/`) replaces its
  previous free-text `detail` field with a typed `quoteReason` of this
  enum, and gains two new top-level `Reason` values,
  `CONFIRMATION_REQUIRED`/`STALE_CONFIRMATION` (Section 16e), alongside
  the existing session/slot/stack reasons. `TradeRejectionKeys.forSell`
  (rewritten) switches on the real enum values — the previous
  `detail.contains("does not accept")`/`"no resolvable value"`/etc.
  English-string inspection is gone entirely, replaced by an exhaustive
  `switch` over `SellRejectionReason` (an unrecognized/future value —
  practically only reachable via `NONE`/`OVERFLOW`/`GENERIC`, none of
  which `NOT_SELLABLE` should ever carry in practice — falls back
  safely to `totality.trading.reject.generic`, never crashes). New
  localization keys: `totality.trading.reject.merchant_zero_credits`,
  `totality.trading.reject.confirmation_required`,
  `totality.trading.reject.stale_confirmation`. The client still never
  receives or trusts raw server text for this — only the resolved key,
  unchanged from Section 13's original design.

  16g. NETWORKING CHANGES (all additive/trimmed, not a new protocol).
    - `SellItemPayload` gained `confirmedReducedPayout` (boolean),
      `confirmedTotalValue`/`confirmedPayableAmount` (`long`, VarLong
      wire-encoded) — Section 16c/16e.
    - `SellQuoteResultPayload` DROPPED `maxQuantityByMerchant` (no
      longer a meaningful concept — quantity is never merchant-capped)
      and otherwise unchanged; the client derives `totalValue`/
      `payableAmount`/`forfeitedValue` for the currently SELECTED
      quantity itself from `unitPayout` (quantity-independent, still
      carried) and the already-present `ShowShopStatePayload.merchantCredits`
      (piggybacked on every `RequestSellQuotePayload` response, as
      before) — never a new round-trip per quantity change.
    - `ShowShopStatePayload`/`RequestSellQuotePayload` themselves:
      UNCHANGED.
  Both changed payloads gained direct `STREAM_CODEC` round-trip
  verification checks (Section 16h) per the task's "add codec
  verification if payload structure changes" instruction.

  16h. VERIFICATION (2026-07-17). `MerchantSellQuoteView`'s pure-quote
  checks in `MerchantSellVerification` were extended/corrected:
  `checkMerchantWithInsufficientCreditsRejects` (which asserted the OLD,
  now-wrong behavior of outright rejecting an underfunded quote) became
  `checkMerchantWithInsufficientCreditsRequiresConfirmation` (asserts
  `sellable() && requiresConfirmation() && payableAmount/forfeitedValue`
  are correct); new `checkMerchantWithZeroCreditsRejectsOutright` and
  `checkSellQuantityNotCappedByMerchantAffordability` (a 4-unit stack
  vs. a 1-unit-affordable merchant still offers `effectiveMaxQuantity ==
  4`). Four new REAL session/commit checks drive
  `TradeSessionManager.handleSell` directly: an underfunded SELL without
  confirmation is rejected and changes nothing; a matching confirmation
  completes atomically at the payable amount, removes the full
  quantity, and leaves the merchant at exactly 0; a confirmation whose
  terms no longer match a since-changed quote (merchant Credits
  manually altered mid-check) is rejected as stale and changes nothing;
  a zero-Credit merchant rejects SELL outright with no confirmation
  path. `TradingScreenVerification` gained
  `checkSellItemPayloadRoundTripsConfirmationFields`/
  `checkSellQuoteResultPayloadRoundTrips` (direct `STREAM_CODEC`
  encode/decode round trips) and `checkRejectedItemsCannotSubmitSell`
  was rewritten against the real `SellRejectionReason` enum instead of
  string literals; `checkSellMaxRespectsMerchantCredits` became
  `checkSellMaxNotCappedByMerchantCredits` (asserts the corrected
  behavior directly).

  RUNTIME-VERIFIED (2026-07-17, `gradlew runClient
  --args="--quickPlaySingleplayer \"New Testing World\""`):
  `[ItemValueVerification] All 19 self-test checks passed.`;
  `[MerchantSellVerification] All 43 self-test checks passed.` (was 37
  — +6 this pass); `[ProvisionerVerification] All 71 self-test checks
  passed.` (unchanged — nothing in this pass touched Provisioner entity
  logic); `[TradingScreenVerification] All 22 self-test checks passed.`
  (was 20 — +2). Combat-side suite counts
  (`PowerAttackVerification`/`KeybindVerification`) are reported in
  `TOTALITY_COMBAT_INPUT_AND_HUD.md` Section 7. Zero unexpected
  `WARN`/`ERROR` lines from the `totality` namespace — the only `ERROR`
  lines present are `ProvisionerVerification`'s own known, unchanged
  synthetic negative-test scenarios (corrupt-data self-tests), confirmed
  by grep. `gradlew compileJava`/`build`: SUCCESSFUL. `gradlew
  runDatagen`: regenerated ONLY the lang file (9 new keys); no other
  generated output changed.

  MANUAL TESTING: CONFIRMED (2026-07-17, Stefan) — fully funded SELL;
  underfunded SELL full-value display; Merchant Can Pay/Forfeited Value
  display; the underfunded confirmation popup; Cancel and Escape
  behavior; a confirmed reduced payout; the merchant reaching ₵0;
  zero-Credit merchant SELL prevention; accountless physical-Credit
  payout; account-holder Wallet payout; structured rejection reasons;
  BUY and SOLD OUT regression behavior. Stale-confirmation refresh
  specifically was exercised only indirectly (no report of a dedicated
  stale-terms repro) — not called out as a separate failure, but not
  independently itemized either.

  --------------------------------------------------------------------------
  16i. POST-REVIEW CORRECTION (2026-07-17): REDUCED-PAYOUT CONFIRMATION
       STALE ON A NEWLY FULLY-FUNDED MERCHANT
  --------------------------------------------------------------------------
  The regenerated Phase 4 review bundle passed archive validation and
  static review, but flagged one remaining implementation defect in the
  confirmation-staleness check added in Section 16e, fixed this pass.

  Reported scenario: a player's goods are worth ₵304, the merchant has
  ₵300, the player confirms terms offering the reduced ₵300 payout, and
  before the server processes that confirmation the merchant's Credits
  rise to ₵304 or more (a concurrent BUY from another source, a Credit
  top-up, etc.). Section 16e's original staleness check only re-validated
  a confirmed REDUCED payout against a quote that STILL required
  confirmation (i.e., still underfunded) — it never considered the case
  where the quote had crossed the line into full funding. The confirmed
  ₵300 terms therefore silently completed as an ordinary fully-funded
  sale, using the OLD confirmed numbers rather than the current ones —
  a real terms mismatch slipping through undetected, exactly opposite of
  the intended "any change to confirmed terms requires fresh review"
  rule.

  Root cause: `TradeSessionManager.handleSell`'s confirmation branching
  was structured around the CURRENT quote's `requiresConfirmation()`
  state first, not around what the request itself claimed the player had
  confirmed. Once the current quote stopped requiring confirmation (the
  fully-funded case), the code fell straight into the ordinary/no-
  confirmation-needed branch regardless of whether the request was
  actually carrying stale confirmed-reduced-payout fields from a moment
  earlier.

  Fixed by restructuring the branch to check the REQUEST's claim first,
  exactly per the canonical rule ("any change to confirmed terms requires
  fresh player review, whether the change is better or worse"):

    IF the request says the player confirmed a reduced payout:
        IF the current quote no longer requires confirmation:
            reject as STALE_CONFIRMATION
        IF the confirmed total value differs from the current total value:
            reject as STALE_CONFIRMATION
        IF the confirmed payable amount differs from the current payable amount:
            reject as STALE_CONFIRMATION
        payout = current payable amount
    ELSE:
        IF the current quote requires confirmation:
            reject as CONFIRMATION_REQUIRED
        payout = current total value

  A confirmed-reduced-payout request is now rejected as
  `STALE_CONFIRMATION` the instant the merchant becomes fully (or over-)
  funded, in addition to the pre-existing case where the merchant's
  affordability shifts while still underfunded. Rejection remains fully
  atomic on every path — no items removed, no Wallet/physical Credits/
  merchant Credits changed, and no partial transaction — unchanged from
  Section 16e's original atomicity guarantee. No new quote-token or
  stack-fingerprint protocol was introduced; this is a pure re-validation
  of the existing quote fields against the existing request fields (see
  Section 16j).

  Extended `MerchantSellVerification` with 4 new REAL session/commit
  checks (43 -> 47), all driving `TradeSessionManager.handleSell`
  directly against a `standardShop()` fixture (Bread, payout ₵6/unit,
  quantity 17, totalValue ₵102): confirming a reduced payout while the
  merchant is still underfunded, then having the merchant's Credits rise
  further but remain underfunded, correctly still rejects the old
  confirmation as stale (`checkMerchantRisingWhileStillUnderfundedInvalidatesOldConfirmation`);
  the merchant's Credits reaching exactly the total value (the reported
  bug's exact boundary) correctly rejects the old reduced-payout
  confirmation as stale rather than silently completing it
  (`checkFullyFundedMerchantInvalidatesOldReducedConfirmation`); the
  merchant's Credits rising past the total value (overfunded) correctly
  rejects the same way
  (`checkOverfundedMerchantInvalidatesOldReducedConfirmation`); and,
  after a stale rejection, a fresh SELL request carrying no confirmation
  claim against the now-fully-funded quote correctly succeeds at the
  full current total value
  (`checkStaleRejectionAllowsFreshFullValueSale`) — proving the fix
  rejects only the stale request, not the merchant's ability to
  transact at all. Every check asserts full atomicity on rejection
  (unchanged item count, unchanged Wallet/merchant Credits).

  RUNTIME-VERIFIED (2026-07-17, `gradlew runClient
  --args="--quickPlaySingleplayer \"New Testing World\""`, bounded dev
  client boot): `[MerchantSellVerification] All 47 self-test checks
  passed.` (was 43). Every other suite passed unchanged: ItemValue 19,
  Provisioner 71, TradingScreen 22 — combat-side suite counts (Block
  rebind fix, illegal-offhand-rejection fix, `KeybindVerification` 13,
  new `OffhandAttackVerification` 5) are reported in
  `TOTALITY_COMBAT_INPUT_AND_HUD.md` Section 8f. **213 checks total
  across 11 suites, zero failures.** `gradlew compileJava`/`gradlew
  build`: SUCCESSFUL. No datagen was run for this fix — no new
  localization keys or other generated resources were needed.

  --------------------------------------------------------------------------
  16j. QUOTE-TOKEN / STACK-FINGERPRINT HARDENING (NOT IMPLEMENTED)
  --------------------------------------------------------------------------
  Same note as `TOTALITY_COMBAT_INPUT_AND_HUD.md` Section 8g: a
  server-issued quote token or stack-fingerprint protocol was raised as
  an optional hardening idea during review, and was NOT implemented this
  pass. Section 16i's fix fully closes the reported bug by re-validating
  existing quote fields against existing request fields — no new
  client/server payload fields were added. This remains a possible
  FUTURE hardening step only, not scheduled work.

  MANUAL TESTING (2026-07-17, Stefan): ordinary fully funded SELL,
  underfunded SELL confirmation, and zero-Credit merchant SELL
  prevention were all re-confirmed working after this pass's fix, and
  BUY remains unchanged.

  The fully-funded-while-confirming-a-reduced-payout transition itself
  is recorded as **AUTOMATED-ONLY, not manually tested** — Stefan
  reports it could not be produced safely in a solo GUI session, since
  the merchant/session locking makes concurrently changing the
  merchant's Credits mid-confirmation impractical to trigger by hand
  with only one client. This is not treated as a gap in the fix itself:
  the exact reported bug scenario (₵304 goods, ₵300 merchant, confirm at
  ₵300, merchant rises to ≥₵304 before the server processes the
  confirmation) is covered by the 4 new real session/commit checks in
  Section 16h/16i
  (`checkFullyFundedMerchantInvalidatesOldReducedConfirmation`,
  `checkOverfundedMerchantInvalidatesOldReducedConfirmation`, and their
  siblings), which drive `TradeSessionManager.handleSell` directly
  rather than through the GUI and do not have this locking limitation.
  All previously-confirmed economy/SELL manual testing above is
  unaffected and remains valid — this fix only changes which requests
  get rejected as stale, not any already-tested successful path's
  numbers.

================================================================================
END OF DOCUMENT
================================================================================
