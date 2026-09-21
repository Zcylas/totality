# Soul Gem Foundation — Implementation Report

**Date:** 2026-09-17
**Scope:** First small Soul Gem foundation. Two registered vessels (Petty, Common), a minimal
persistent captured-soul data model, an authored acceptance-rule architecture, and a MobRank safety
fix required to support the new ranks. Explicitly NOT the full Soul Gem system — see §21-23.

---

## 1. Starting branch / HEAD

- Branch: `feature/food-system`
- HEAD: `5ea2bc6d087db1d30134c81643b1909f1aead564` (`feat(food): add authoritative food system and totality food items`)

Both matched the task's expected values exactly.

## 2. Baseline dirty tree

`git status --porcelain` returned 111 lines at session start (42 `" M"` + 67 `"??"` + 2 `"A "`). The
2 staged entries (`common_soul_gem.json` model + `common_soul_gem.png` texture — the user's new Common
Soul Gem assets) were unexpectedly pre-staged, matching the exact same external-tool auto-staging
anomaly seen repeatedly during the Food task. Per that established convention, they were safely
unstaged via `git restore --staged` (index-only, non-destructive), leaving a clean 109-path baseline
(42 + 67) — see `Context/Audit/Review/REVIEW_BASELINE.md` for the full snapshot recorded *before* any
implementation edit.

## 3. New feature branch

`feature/soul-gems`, created via `git checkout -b feature/soul-gems` from the Food commit. Branch
creation only moves the ref; all 109 unrelated dirty paths and the user's Soul Gem assets were
preserved exactly (confirmed by re-running `git status --porcelain` immediately after, still 109 for
the pre-existing baseline, plus this task's own new/edited files layered on top).

## 4. MobRank audit findings

`MobRank` (`api/mob/stats/MobRank.java`) had six ranks (E–S) before this pass, with **eight total
usage sites across three consumer files**, found via `grep -rn "MobRank"`:

| File | Usage | Ordinal-dependent? |
|---|---|---|
| `MobStatBlock.getFixedRank()` | `MobRank.valueOf(rank.toUpperCase())`, catch → `MobRank.E` | No — name/string-based, safe against reordering |
| `MobCombatStats` (field + constructor) | Stores resolved `MobRank`; **encodes `this.rank.ordinal()`** into `MobStatsSyncPayload` | **Yes** |
| `MobStatsSyncPayload` / `MobStatsClientCache` | Carry the ordinal as a raw `int` (`rankOrdinal`) over the network and into the client cache | Transient (never persisted), but fragile |
| `MobHealthBarHud.buildName()` | **Decodes via `MobRank.values()[Math.min(rankOrdinal, values().length-1)]`** | **Yes** |
| `TotalityHudCleanupSourceRegressionTest` | Source-regression guard pinning the literal `"MobRank.values()["` pattern | N/A — test needed updating in lockstep |

No mob-stat-block JSON datapack file needed a rank-value change — `MobStatBlock`'s only
rank-related literal was the pre-existing `"E"` Gson default.

## 5. MobRank safety change (exact)

- **`MobRank.java`**: added `F`, `Z`, `ZERO` constants (colors chosen for the new tiers), plus an
  explicit authored `order` field per constant (F=0, E=1, D=2, C=3, B=4, A=5, S=6, Z=7, ZERO=8) and:
  `order()`, `isAtMost`/`isAtLeast`/`isStrongerThan`/`isWeakerThan`/`compareStrength`, `getId()`
  (`"0"` for `ZERO`, `name()` otherwise), `fromId(String)` (name/id-based parse, `"0"`→`ZERO`,
  case-insensitive, falls back to `E` — exactly `MobStatBlock`'s old fallback, preserved not
  changed), and `fromOrder(int)` (stable authored-order decode).
- **`MobStatBlock.getFixedRank()`**: now `return MobRank.fromId(rank);` — same fallback-to-`E`
  behavior for anything unrecognized, now also accepting `"0"`.
- **`MobCombatStats`**: network-encode site changed from `this.rank.ordinal()` to
  `this.rank.order()`. The `MobStatsSyncPayload`/`MobStatsClientCache` field is still literally named
  `rankOrdinal` — deliberately **not** renamed, to keep this a minimal, surgical fix (only the value
  source and the decode logic changed, not every call site's name); it now carries the stable
  authored order, not the raw Java ordinal.
- **`MobHealthBarHud.buildName()`**: decode site changed from
  `MobRank.values()[Math.min(mobData.rankOrdinal(), MobRank.values().length - 1)].name()` to
  `MobRank.fromOrder(mobData.rankOrdinal()).getId()`.
- **`TotalityHudCleanupSourceRegressionTest`**: test 50 updated to assert the new
  `"MobRank.fromOrder("` pattern and the explicit absence of `"MobRank.values()["`, with a comment
  explaining this is an intentional part of this pass, not a threat-tier redesign.

No JSON/save-file persistence format changed — `MobStatBlock`'s rank field is authored datapack JSON
(string), unaffected by the enum's internal ordinal. The network wire format's *shape* (`int`) is
unchanged; only what integer value it carries changed (authored order, not ordinal), which is safe
because both the encode and decode sites live in the same build and were changed together.

## 6. Canonical rank order

```
F < E < D < C < B < A < S < Z < ZERO   (ZERO displays externally as "0")
```

`ZERO`'s external/display identity is `"0"` (`getId()`), matching the task's requirement that a
Java enum constant obviously cannot be named `0` while its display identity still is.

## 7. Rank 0 meaning

`MobRank.ZERO` = **Family Rank** — an apex narrative/story classification strictly above `Z`, never
the weakest rank. It is categorically uncapturable by any ordinary Soul Gem in this foundation (see
§8, §13); this is intentionally not a vessel-capacity failure. Rank remains purely authored
classification — never derived from level, Challenge Rating, stats, or any formula; `MobCombatStats`'s
`level` field and its own scaling logic were not touched beyond the one ordinal→order fix above.

## 8. CapturedSoul structure

`api/soulgem/CapturedSoul.java` — a plain record:

```java
public record CapturedSoul(UUID soulInstanceId, MobRank rank, SoulCategory category, Identifier sourceEntityType) {}
```

- `soulInstanceId` — identifies the captured **soul instance**, not the source mob's entity UUID.
  Two separately captured zombies get distinct instance ids even with identical rank/category/entity,
  so two such filled gems never accidentally compare equal (proven in `CapturedSoulTest` and, at the
  real `ItemStack` level, in `SoulGemSystemVerification`).
- `rank` — the soul's **actual** rank, never promoted to whatever ceiling the containing gem accepts
  (a Common gem holding a captured Rank F soul still records `MobRank.F`, not `MobRank.D`).
- `category` — `SoulCategory.ORDINARY` for everything in this pass.
- `sourceEntityType` — the real `Identifier` (e.g. `minecraft:zombie`), never a display name.

## 9. SoulCategory scope

`api/soulgem/SoulCategory.java` — a `StringRepresentable` enum with exactly one constant,
`ORDINARY`, plus a `Codec`/`StreamCodec` pair following `ItemTypeComponent`'s established
string-based network-encoding convention in this repo (not ordinal-indexed). No Black/sapient/
undead/boss/divine category was added — the architecture (an enum consulted only through {@code
SoulGemAcceptanceRule#acceptsCategory}) can grow a second constant later without touching
`SoulGemItem`, the acceptance-rule shape, or `CapturedSoulComponent`'s codec.

## 10. SoulGemItem architecture

`item/soulgem/SoulGemItem.java` — **one** reusable `Item` subclass for every ordinary tier:

```java
public class SoulGemItem extends Item {
    private final SoulGemAcceptanceRule acceptanceRule;
    public SoulGemItem(Properties properties, SoulGemAcceptanceRule acceptanceRule) { ... }
    public SoulGemAcceptanceRule acceptanceRule() { return acceptanceRule; }
}
```

No `PettySoulGemItem`/`CommonSoulGemItem` subclass exists. State (empty vs. filled) lives entirely in
`CapturedSoulComponent.CAPTURED_SOUL`'s presence/absence — no `_filled` item id, no per-rank/per-soul
filled variant. No `SoulGemUsePolicy` abstraction was introduced (would be overengineering for two
items with only one use policy each, per the task's own instruction) — actual soul
consumption/spending is not implemented.

## 11. Acceptance-rule architecture

`api/soulgem/SoulGemAcceptanceRule.java` — a small functional interface:

```java
public interface SoulGemAcceptanceRule {
    boolean acceptsCategory(SoulCategory category);
    boolean acceptsRank(MobRank rank);
    default boolean accepts(SoulCategory category, MobRank rank) { ... }
    static SoulGemAcceptanceRule categoryUpToRank(SoulCategory category, MobRank maxRank) { ... }
}
```

Category and rank are checked as two **separate** methods (not just one combined boolean) so
`SoulCaptureService` can report a precise rejection reason (`CATEGORY_REJECTED` vs.
`RANK_TOO_STRONG`) without any category-count-dependent heuristic. `categoryUpToRank` is a
convenience factory for the one shape both current gems need — a single category with an inclusive
rank ceiling, compared via `MobRank.isAtMost` (never `ordinal()`) — not a universal tier formula.
Acceptance is authored per registered gem; nothing in `SoulCaptureService` branches on an item ID.

## 12. Petty behavior

`MagicItems.PETTY_SOUL_GEM` — the **existing** item, converted in place from a plain `Item` to
`SoulGemItem` (no duplicate registration). Rarity (`COMMON`), item type (`MAGICAL`), classifications,
lore, and tooltip profile were all preserved unchanged. New acceptance rule:
`SoulGemAcceptanceRule.categoryUpToRank(SoulCategory.ORDINARY, MobRank.F)` — accepts ORDINARY Rank F
only; rejects E and above, any non-ORDINARY category, and Rank 0 (via the global eligibility gate,
§13 — independent of Petty's own ceiling).

## 13. Common behavior

> **Corrected in the 2026-09-17 review-correction pass (§24) — the original rarity choice below was
> WRONG and is kept here only for historical context.** `ItemRarity.UNCOMMON` was inferred from the
> Grimoire tier's rarity progression, but that inference was never canonical — a Soul Gem's vessel
> tier/name is not a rarity ladder. Common Soul Gem's actual, corrected rarity is `ItemRarity.COMMON`
> (same as Petty). See §24 for the full correction.

`MagicItems.COMMON_SOUL_GEM` — new registration, id `totality:common_soul_gem`. ~~Rarity bumped one
step to `UNCOMMON` (following the exact existing Grimoire precedent: Novice=RARE→Apprentice=EPIC→
Archmage=LEGENDARY, each successive tier one step up the Standard Progression ladder — not an
invented rule).~~ **(superseded — see the correction notice above; rarity is `COMMON`)** Item type
`MAGICAL`, same tooltip-profile/classification pattern as Petty. New acceptance rule:
`SoulGemAcceptanceRule.categoryUpToRank(SoulCategory.ORDINARY, MobRank.D)` — accepts F, E, D; rejects
C and above, any non-ORDINARY category, and Rank 0.

No universal tier formula was encoded: Petty's ceiling spans 1 rank (F only), Common's spans 3 (F–D)
— two independently authored spans, not "each tier = previous + 1."

## 14. Rank 0 rejection behavior (eligibility architecture)

`api/soulgem/SoulCaptureEligibility.isEligibleForCapture(MobRank)` returns `false` only for
`MobRank.ZERO`. `SoulCaptureService.attemptCapture` checks this **before** consulting the gem's own
`SoulGemAcceptanceRule`.

> **Updated (§24.A, review-correction pass) — the flow below was extended, not replaced.** The
> original conceptual flow described here (not-a-gem → already-filled → Rank-0 eligibility →
> category → rank-ceiling → write) is now, in full, the **current and accurate** flow:
>
> 1. `NOT_A_SOUL_GEM`
> 2. `ALREADY_FILLED`
> 3. `STACKED_VESSEL_REQUIRES_SPLIT` (new — rejects any `stack.getCount() > 1` attempt before it
>    reaches vessel-acceptance or soul-eligibility judgment)
> 4. `RANK_ZERO_INELIGIBLE`
> 5. `CATEGORY_REJECTED`
> 6. `RANK_TOO_STRONG`
> 7. write (`SUCCESS`)
>
> See §24.A for the full rationale, including exactly why `ALREADY_FILLED` was deliberately kept
> ahead of the new stacked-vessel check rather than after it.

This guarantees no current or future ordinary Soul Gem can accidentally admit Rank 0 merely because
its own ceiling comparison happens to also reject it — the rejection is structural, not incidental
(both are additionally proven independently: Petty's and Common's own `acceptsRank(ZERO)` also
correctly return `false`, tested in `SoulGemAcceptanceRuleTest`/`SoulGemArchitectureSourceRegressionTest`).

## 15. Persistence / capture model

> **Updated (§24.A, review-correction pass) — the result set below now matches the actual source
> exactly** (`STACKED_VESSEL_REQUIRES_SPLIT` was added; the original list omitted it).

`api/soulgem/SoulCaptureResult.java` — `SUCCESS`, `ALREADY_FILLED`, `STACKED_VESSEL_REQUIRES_SPLIT`,
`RANK_ZERO_INELIGIBLE`, `CATEGORY_REJECTED`, `RANK_TOO_STRONG`, `NOT_A_SOUL_GEM`.

`api/soulgem/SoulCaptureService.attemptCapture(ItemStack, CapturedSoul)` — the minimal domain helper
the task asked for. Not a live Entity-capture pipeline (no Soul Trap exists); a caller must already
have a `CapturedSoul` to offer. Guarantees: a failed attempt never mutates the stack; an
already-filled gem is never silently overwritten; a successful capture writes exactly one
`CapturedSoulComponent.CAPTURED_SOUL` component.

**One-vessel-only invariant:** `SoulCaptureService` mutates only an `ItemStack` representing exactly
one physical vessel (`stack.getCount() == 1`). A multi-count stack must be split down to one gem by a
future caller (Soul Trap / inventory logic) before capture is attempted — this service does not, and
will not in this pass, perform that split itself. See §24.A for the bug this prevents and the full
check-order rationale.

## 16. Data component

`api/soulgem/CapturedSoulComponent.java` — following `PotionDataComponent`'s established pattern
exactly: a `Codec<CapturedSoul>` (persistent) built via `RecordCodecBuilder`, plus a
`StreamCodec<RegistryFriendlyByteBuf, CapturedSoul>` (network) built via `StreamCodec.composite`, both
registered as one `DataComponentType<CapturedSoul>` (`totality:captured_soul`) through
`Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, ...)`, called from `Totality.registerApi()`
right after `TotalityItemComponents.register()`. `MobRank`'s own persistent/network sub-codecs go
through `MobRank.fromId`/`getId` (string-based, matching the same convention `MobStatBlock` already
used) — not ordinal-indexed. `SoulCategory`'s sub-codecs reuse its own `CODEC`/`STREAM_CODEC`
(string-based, matching `ItemTypeComponent`'s established convention in this repo).

## 17. Stacking behavior

Proven at the real `ItemStack` level in `SoulGemSystemVerification` (plain JUnit cannot construct a
real `Item`/`ItemStack` in this codebase — see §18):

- Two empty Petty stacks: `ItemStack.isSameItemSameComponents` → `true`.
- Two empty Common stacks: → `true`.
- Two filled Petty stacks holding **distinct** `soulInstanceId`s (everything else identical): →
  `false` — `CapturedSoul` is part of ItemStack component equality, entirely through Data Components;
  no custom inventory-stacking hack was written.
- Two filled Petty stacks holding the exact **same** `CapturedSoul` value: → `true` (equality is by
  value, not object identity — proves the distinctness above is caused by the differing instance id,
  not by filled-vs-filled always being incompatible).

## 18. Tests

**Plain JUnit** (no live registries needed — pure enums/records/Codecs/functional interfaces):

- `MobRankTest` (11 tests) — canonical order, Rank 0 above Z, `isAtMost`/`isAtLeast`/
  `compareStrength`, `getId`, `fromId` round-trip for all 9 ranks + case-insensitivity + fallback,
  `fromOrder` round-trip + fallback, distinct order values.
- `MobRankNetworkSourceRegressionTest` (3 tests) — source-text proof that `MobCombatStats` encodes
  via `order()` not `ordinal()`, `MobHealthBarHud` decodes via `fromOrder(` not `values()[`, and
  `MobStatBlock` parses via `fromId(rank)` not the old `valueOf(...)`.
- `SoulCategoryTest` (3 tests) — sole `ORDINARY` value, serialized name, codec round-trip.
- `CapturedSoulTest` (6 tests) — record equality (same fields+id → equal; different id → not equal;
  different entity type → not equal), actual-rank-not-promoted-to-ceiling, full codec round-trip, and
  a round-trip loop over **every** `MobRank` value (F through ZERO).
- `SoulGemAcceptanceRuleTest` (7 tests) — Petty accepts F only; Common accepts F/E/D only; both
  reject Rank 0 through their own ceiling too (independent of the eligibility gate); no shared tier
  formula; `acceptsCategory`/`acceptsRank` are independently callable. The "rejects a second,
  non-ORDINARY category" case is explicitly **deferred and documented** in this file's own class
  Javadoc, per the task's own instruction — `SoulCategory` has only `ORDINARY` today, so there is no
  real second value to assert a rejection against without inventing one.
- `SoulCaptureEligibilityTest` (2 tests) — Rank 0 never eligible; every other rank always eligible.
- `SoulGemArchitectureSourceRegressionTest` (7 tests) — no `.ordinal()` in
  `SoulGemItem`/`SoulCaptureService`/`SoulGemAcceptanceRule`; `SoulGemItem` declares exactly one
  class with no `extends SoulGemItem` subclass; no `PettySoulGemItem`/`CommonSoulGemItem` files
  exist; both registrations construct a real `SoulGemItem` with their exact authored rule; no
  Lesser/Greater/Grand/Black Soul Gem identifiers anywhere in `MagicItems.java`; no
  `soulStrength`/`soulCapacity`/`gemCapacity` identifiers anywhere in the new architecture files; no
  `BlackSoulGemItem`/`SoulTrap`/`BlackSoulCategory` file exists; neither gem's authored ceiling
  admits Rank 0.
- `TotalityHudCleanupSourceRegressionTest` (existing test 50, updated in place) — now asserts the new
  `fromOrder(` pattern and the absence of the old `values()[` pattern.

**Dev-server verification** (`api/soulgem/verification/SoulGemSystemVerification`, gated behind
`VerificationReporter.isDevEnvironment()`, run via `ServerLifecycleEvents.SERVER_STARTED` — mirrors
`FoodSystemVerification`'s established pattern exactly) — proves everything that genuinely needs a
real bootstrapped item registry/`ItemStack`: registration resolves to real `SoulGemItem` instances;
empty-Petty and empty-Common stacking; a real fill (writes exactly one component); an already-filled
gem rejects and keeps its original soul; Petty rejects Rank E (too strong); Common accepts F/E/D in
one combined check and rejects Rank C; Rank 0 rejected by the eligibility gate; capture into a
non-`SoulGemItem` (a Pizza Margherita stack) fails cleanly as `NOT_A_SOUL_GEM`; distinct-instance-id
stacking incompatibility; same-value stacking compatibility. **12/12 checks passed** on the real dev
server at the time this section was written (see §19) — **superseded by §24.A, which added 4 more
checks for the stacked-vessel fix; the current, final count is 16/16.**

Why the split: this repository's own established precedent
(`HealingPotionItemContractTest`'s Javadoc, confirmed by direct probing in an earlier session) proves
plain JUnit cannot construct a real `Item`/`ItemStack` here — the item registry is not bootstrapped
under plain JUnit, so anything needing a genuine `ItemStack`/component/registry lifecycle must run as
a dev-server verification instead, exactly as `FoodSystemVerification`/`FoodResourceDefinitionTest`
already split the Food system's own tests the same way.

## 19. Validation

> **Superseded by §24.D — this table records validation AS OF THE ORIGINAL FOUNDATION PASS ONLY**
> (before the review-correction pass added the stacked-vessel fix). It is kept for historical record.
> §24.D is the current, final validation state: 1717 tests/0 failures, clean build,
> `SoulGemSystemVerification` **16/16** (not 12/12), `FoodSystemVerification` 42/42.

| Check | Result |
|---|---|
| `./gradlew compileJava compileTestJava` | **BUILD SUCCESSFUL** (one compile error found and fixed during implementation — `StreamCodec` buffer-type variance for the MobRank sub-codec; see the code itself, `MOB_RANK_STREAM_CODEC` is typed `StreamCodec<ByteBuf, MobRank>`, matching `ItemTypeComponent.STREAM_CODEC`'s own established pattern, not `RegistryFriendlyByteBuf`) |
| `./gradlew test` | **BUILD SUCCESSFUL** — **1717 tests, 0 failures** (1677 before this pass + 40 new/updated) |
| `./gradlew clean build` | **BUILD SUCCESSFUL** (compile, datagen-affecting resources, test, jar, sourcesJar, check, all green) |
| `./gradlew runDatagen` | **BUILD SUCCESSFUL** — `written: 2` (new `common_soul_gem` item-definition JSON + updated lang file); Common's model has no generated-model counterpart, exactly like Petty (both hand-authored under `resources`, not datagen-owned) |
| `git diff --check` | Clean — only benign pre-existing LF→CRLF conversion notices (this repo's standing Windows/git line-ending behavior across many pre-existing files), no actual whitespace/conflict-marker errors |
| `./gradlew runServer` (dev-server self-tests) | `[SoulGemSystemVerification] All 12 self-test checks passed.` (now 16/16, see §24.D) / `[FoodSystemVerification] All 42 self-test checks passed.` (unaffected by this pass) |
| Known unrelated failures | `ProvisionerEntityBackedSmokeTest` (3/4 failed) and `OffhandAttackVerification` (3/5 failed) — both pre-existing, both about NPC/combat systems entirely unrelated to Soul Gems or MobRank, **not** caused or touched by this pass; not investigated or "fixed" per the task's explicit instruction |

## 20. Files changed

**New:**
- `src/main/java/zcylas/totality/api/soulgem/SoulCategory.java`
- `src/main/java/zcylas/totality/api/soulgem/CapturedSoul.java`
- `src/main/java/zcylas/totality/api/soulgem/CapturedSoulComponent.java`
- `src/main/java/zcylas/totality/api/soulgem/SoulGemAcceptanceRule.java`
- `src/main/java/zcylas/totality/api/soulgem/SoulCaptureEligibility.java`
- `src/main/java/zcylas/totality/api/soulgem/SoulCaptureResult.java`
- `src/main/java/zcylas/totality/api/soulgem/SoulCaptureService.java`
- `src/main/java/zcylas/totality/api/soulgem/verification/SoulGemSystemVerification.java`
- `src/main/java/zcylas/totality/item/soulgem/SoulGemItem.java`
- `src/test/java/zcylas/totality/api/mob/stats/MobRankTest.java`
- `src/test/java/zcylas/totality/api/mob/stats/MobRankNetworkSourceRegressionTest.java`
- `src/test/java/zcylas/totality/api/soulgem/SoulCategoryTest.java`
- `src/test/java/zcylas/totality/api/soulgem/CapturedSoulTest.java`
- `src/test/java/zcylas/totality/api/soulgem/SoulGemAcceptanceRuleTest.java`
- `src/test/java/zcylas/totality/api/soulgem/SoulCaptureEligibilityTest.java`
- `src/test/java/zcylas/totality/api/soulgem/SoulGemArchitectureSourceRegressionTest.java`
- `Context/Audit/Review/REVIEW_BASELINE.md`
- This report.

**Edited:**
- `src/main/java/zcylas/totality/api/mob/stats/MobRank.java` — F/Z/ZERO + explicit order/comparison/parsing surface.
- `src/main/java/zcylas/totality/api/mob/stats/MobStatBlock.java` — `getFixedRank()` uses `fromId`.
- `src/main/java/zcylas/totality/api/mob/stats/MobCombatStats.java` — network-encode via `order()`.
- `src/main/java/zcylas/totality/client/renderer/hud/MobHealthBarHud.java` — decode via `fromOrder(...).getId()`.
- `src/test/java/zcylas/totality/client/renderer/hud/TotalityHudCleanupSourceRegressionTest.java` — test 50 updated.
- `src/main/java/zcylas/totality/init/items/MagicItems.java` — Petty converted to `SoulGemItem`; Common added.
- `src/main/java/zcylas/totality/datagen/ModModelProvider.java` — Common's model wiring.
- `src/main/java/zcylas/totality/datagen/ModEnglishLangProvider.java` — Common's lang entry.
- `src/main/java/zcylas/totality/init/ModGroups.java` — Common added to the Magic creative tab.
- `src/main/java/zcylas/totality/Totality.java` — two new `register()` call lines.

**Untouched (per explicit instruction):** both Petty and Common's model/texture asset files —
verified unmodified before and after this task.

## 21. Deferred work

Explicitly not designed or scaffolded in this pass: the rest of the gem ladder (Lesser, Greater,
Grand), `SoulGemUsePolicy`/actual soul spending, mob-death capture integration, "smallest compatible
gem" inventory search, visual filled-gem model overrides, tooltip redesign for filled gems, Codex
integration, mob-stat-block JSON authoring for any specific mob's Rank (no mob currently has an
authored rank beyond the existing default), and any Mob API/level/CR redesign.

## 22. Black Soul Gems

**Intentionally not decided.** No `SoulCategory` beyond `ORDINARY` was added; no Black-specific
rejection rule, sapient/humanoid classifier, or alignment concept exists anywhere in this pass. The
architecture (category checked as an independent `acceptsCategory` predicate) is deliberately shaped
so a future category can be added as a pure enum addition, but which categories should exist and how
they interact with rank ceilings remains fully open.

## 23. Soul Trap

**Not implemented.** No live Entity-to-soul capture pipeline, no mob-death hook, no spell/effect, and
no automatic-capture logic of any kind exists. `SoulCaptureService.attemptCapture` requires a caller
to already hold a `CapturedSoul` value — exactly the same shape a future Soul Trap system's own
tests would still need for unit coverage of the vessel/acceptance logic in isolation from the capture
trigger itself.

---

## 24. Review-Correction Pass (2026-09-17)

A small correction pass on this same foundation, found in review. Scope: one real correctness bug,
one design correction, one documentation clarification. No expansion of the foundation's scope — no
Soul Trap, no gem ladder, no commit.

### 24.A. Stacked-vessel capture bug (real correctness bug)

**The bug:** `SoulCaptureService.attemptCapture`'s final step, `stack.set(CapturedSoulComponent.CAPTURED_SOUL, soul)`,
mutates the Data Component shared by *every physical item* an `ItemStack` with `count > 1`
represents — it is only safe when `stack.getCount() == 1`. Attempting capture against a stack of 2+
empty gems would have written the *same* `CapturedSoul` (the same `soulInstanceId`) onto every
physical gem in that stack — cloning one captured soul across multiple physical vessels. This is
exactly the failure `CapturedSoul.soulInstanceId`'s own distinctness invariant (§8, "two separately
captured zombies must receive distinct soul instance IDs") exists to prevent, so allowing it via a
stacked capture would have silently violated the foundation's own core guarantee.

**The fix — a one-vessel-only invariant:** `SoulCaptureService.attemptCapture` now checks
`stack.getCount() > 1` and returns a dedicated failure, `SoulCaptureResult.STACKED_VESSEL_REQUIRES_SPLIT`,
*before* touching the stack. No component is written, the stack's count/components/existing soul are
all left completely unchanged, no automatic splitting happens, no inventory search happens, and no
second `ItemStack` is created or placed — this service still operates on exactly one physical vessel
per call, nothing more.

**Exact check order chosen (see `SoulCaptureService`'s own Javadoc for the authoritative version):**
(1) `NOT_A_SOUL_GEM`, (2) `ALREADY_FILLED`, (3) `STACKED_VESSEL_REQUIRES_SPLIT`, (4)
`RANK_ZERO_INELIGIBLE`, (5) `CATEGORY_REJECTED`, (6) `RANK_TOO_STRONG`, (7) write. `ALREADY_FILLED`
was deliberately kept in its original position, ahead of the new stacked-vessel check: it is a fact
about the vessel's own state, independent of stack count, and the most informative failure when both
conditions happen to hold — a stacked (`count > 1`), already-filled gem reports `ALREADY_FILLED`, not
`STACKED_VESSEL_REQUIRES_SPLIT`, and its original soul is left completely unchanged (proven in
`SoulGemSystemVerification`'s new "CHECK ORDER" check). The stacked-vessel check itself was placed
before the soul-vs-rule checks (eligibility/category/rank) because it is still a precondition on the
vessel's physical shape, not a judgment about the soul being offered — a stacked vessel must never be
misreported as e.g. `RANK_TOO_STRONG`.

**Future scope (explicitly NOT this pass):** a future Soul Trap/inventory system is expected to find a
compatible empty stack, split off exactly one gem, pass that one-count gem into
`SoulCaptureService.attemptCapture`, and place the filled result appropriately. None of that inventory
logic belongs in this small domain helper, which remains exactly what it was: an operator on one
physical vessel.

**New tests (dev-server verification — real `ItemStack.getCount()`/component mutation, following the
established plain-JUnit-cannot-construct-a-real-`ItemStack` split, §18):**
- `ONE-COUNT STILL WORKS` — an explicit `count = 1` empty Petty gem still captures successfully (the
  fix did not break the ordinary single-vessel path).
- `STACKED VESSEL (Petty)` — a `count = 2` empty Petty stack rejects capture as
  `STACKED_VESSEL_REQUIRES_SPLIT`; count stays 2, no component is written.
- `STACKED VESSEL (Common)` — the same proof for a `count = 3` empty Common stack.
- `CHECK ORDER` — a `count = 2`, already-filled Petty gem (filled at count 1, then grown to 2) still
  reports `ALREADY_FILLED`, and its original soul is exactly preserved.
- Pre-existing empty-stack-compatibility and distinct-filled-gem-non-stack-compatibility checks were
  re-run unchanged and still pass — this fix does not touch stacking equality itself, only capture
  eligibility.

`SoulGemSystemVerification` went from 12/12 to **16/16** self-test checks passed.

### 24.B. Common Soul Gem rarity correction (design correction)

**What was wrong:** the original foundation pass set `MagicItems.COMMON_SOUL_GEM`'s rarity to
`ItemRarity.UNCOMMON`, inferred from the Grimoire item family's own tier-to-rarity progression
(Novice=RARE→Apprentice=EPIC→Archmage=LEGENDARY). That inference was never canonical for Soul Gems —
it borrowed a pattern from an unrelated item family.

**The fix:** `MagicItems.COMMON_SOUL_GEM`'s rarity is now `ItemRarity.COMMON` — the same rarity as
Petty. A code comment was added directly above the registration recording why.

**The design rule this establishes, going forward:** a Soul Gem's vessel tier/name ("Petty",
"Common", and any future "Lesser"/"Greater"/"Grand"/etc.) is a Soul Gem-specific classification, and
is a **separate concept** from Totality's `ItemRarity` ladder. One must never be inferred from the
other. This pass does not decide what rarity any future Soul Gem tier should have — that remains
open, exactly as undecided as the rest of the gem ladder (§21-22).

**§13 above** (Common behavior) has been annotated in place with a correction notice rather than
silently rewritten, preserving the historical record of the original (incorrect) reasoning.

**Tests:** no existing test asserted `UNCOMMON` for Common (confirmed by search before this pass), so
no test needed updating to stop expecting the old value; `MagicItems.java`'s own new comment is the
only in-repo point-of-truth for this rule besides this report.

### 24.C. MobRank colors — documented as provisional

No color values were changed. `MobRank.java`'s class Javadoc and each of the `F`/`Z`/`ZERO` enum
constant declarations now carry an explicit "color is PROVISIONAL, not yet canonically designed"
notice, so a future reader does not mistake the current placeholder hex values for a deliberate,
finished color design. The semantic rank order (`F < E < D < C < B < A < S < Z < 0`, §6) remains fully
canonical and is entirely unaffected — only the *presentation* colors for the three ranks this
foundation pass introduced are provisional. `E`/`D`/`C`/`B`/`A`/`S`'s pre-existing colors are
untouched.

### 24.D. Validation (this correction pass)

| Check | Result |
|---|---|
| `./gradlew compileJava compileTestJava` | **BUILD SUCCESSFUL** |
| `./gradlew test` | **BUILD SUCCESSFUL** — **1717 tests, 0 failures** (unchanged — all new coverage for the stacked-vessel fix lives in the dev-server verification, since it needs a real `ItemStack.getCount()`/component mutation that plain JUnit cannot construct here, §18) |
| `./gradlew clean build` | **BUILD SUCCESSFUL** |
| `git diff --check` | Clean — only the same pre-existing benign LF→CRLF notices as the original pass |
| `./gradlew runServer` (dev-server self-tests) | `[SoulGemSystemVerification] All 16 self-test checks passed.` (was 12; +4 for the stacked-vessel fix) / `[FoodSystemVerification] All 42 self-test checks passed.` (unaffected) |
| Known unrelated failures | `ProvisionerEntityBackedSmokeTest` (3/4 failed) and `OffhandAttackVerification` (3/5 failed) — identical to the original pass, still untouched, still not investigated per this task's explicit instruction |

### 24.E. Files touched by this correction pass

- `src/main/java/zcylas/totality/api/soulgem/SoulCaptureResult.java` — added `STACKED_VESSEL_REQUIRES_SPLIT`.
- `src/main/java/zcylas/totality/api/soulgem/SoulCaptureService.java` — added the stacked-vessel check + expanded Javadoc documenting the exact check order and its rationale.
- `src/main/java/zcylas/totality/api/soulgem/verification/SoulGemSystemVerification.java` — 4 new checks (12→16).
- `src/main/java/zcylas/totality/init/items/MagicItems.java` — Common's rarity `UNCOMMON` → `COMMON`, plus an explanatory comment.
- `src/main/java/zcylas/totality/api/mob/stats/MobRank.java` — F/Z/ZERO colors documented as provisional (no values changed).
- This report — §13 annotated with a correction notice; this §24 appended.
- `Context/Audit/Review/` bundle — regenerated (`REVIEW_CHANGED_FILES.md`, `REVIEW_GIT_DIFF.patch`, `REVIEW_DIFF_STAT.txt`, `REVIEW_SUMMARY.md`, and the ZIP); `REVIEW_BASELINE.md` kept as the original pre-foundation baseline, unmodified.

### 24.F. Still out of scope

Unchanged from §21-23: the rest of the gem ladder, Black Soul Gems/categories, Soul Trap, mob-death
capture integration, automatic gem splitting/best-fit selection, soul spending/enchanting, and any
Mob API/level/CR redesign. This correction pass did not expand scope in any direction.

### 24.G. Manual client verification — final status (2026-09-17)

Recorded precisely, no more and no less than what was actually confirmed:

- **Confirmed by the user in the real client:** Petty Soul Gem appears/renders correctly in-game;
  Common Soul Gem appears/renders correctly in-game.
- **NOT manually tested, and not claimed here:** empty-gem stacking, filled-gem stacking, capture
  (fill/already-filled/stacked-vessel/rank-rejection) behavior. None of these involve a Soul
  Trap-style player-facing interaction — there is no Soul Trap, so there is nothing for a player to
  manually trigger yet. This behavior is instead covered by real `ItemStack`-level dev-server
  verification (`SoulGemSystemVerification`, 16/16 — §24.D), which exercises the real, bootstrapped
  item registry and Data Component machinery directly, not a mock.
- **Not applicable / does not exist to test:** Soul Trap, soul capture on mob death, the rest of the
  gem ladder, Black Soul Gems.

**Final status:** the Soul Gem foundation is IMPLEMENTED, AUTOMATED-VERIFIED (1717 JUnit tests / 0
failures, `SoulGemSystemVerification` 16/16), and REAL-CLIENT-VERIFIED for the one thing a player can
currently observe about it — both items exist, are obtainable via the Magic creative tab, and render
correctly. Everything beyond rendering (capture, stacking, future gameplay) remains
dev-server-verified only until a real capture-triggering system (Soul Trap) exists for a player to
exercise by hand.
