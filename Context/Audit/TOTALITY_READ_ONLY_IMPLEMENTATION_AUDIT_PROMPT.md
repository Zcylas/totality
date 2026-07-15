# Totality API Documentation vs. Implementation Audit

Perform a **read-only implementation audit** of the current Totality codebase against the supplied design documents.

## Critical restriction

**Do not modify any code, configuration, resources, data files, or documentation during this pass.**

Do not:

* Refactor code.

* Rename classes, methods, fields, packages, identifiers, screens, components, or resources.

* Add missing features.

* Delete unused code.

* Fix bugs.

* Update documentation.

* Generate migrations.

* Apply formatting changes.

This pass is strictly for inspection and reporting. Even when a correction appears obvious, report it rather than applying it.

## Documents to audit

Use the current versions of:

* `TOTALITY_FOOD_ECOSYSTEM_CURRENT`

* `TOTALITY_ECONOMY_BANKING_TRADING`

* `TOTALITY_PHONE_PLATFORM`

* `TOTALITY_REST_AND_FATIGUE`

* The current Disease / Spell Ownership and Capstone Intervention document.

* The current Shared Cross-System Foundations document.

* The current Balance Design document.

* The current Future Ideas document.

Respect every status marker literally:

* `ADOPTED`

* `RECOVERED — ADOPTED`

* `VERIFY AGAINST CODE`

* `PARTIAL`

* `PROPOSED`

* `TBD`

* `STUB`

* `PLACEHOLDER`

* `SUGGESTED`

* `PLANNED`

Do not treat proposed, partial, placeholder, planned, stubbed or TBD material as mandatory implementation.

## Audit scope

### 1. Economy, Banking and Trading

Inspect:

* Credits storage and representation.

* Physical Credit items.

* Player accounts.

* Account tiers.

* Banker entity, dialogue, screens and quests.

* Deposit and withdrawal behavior.

* Currency exchange.

* ATM code.

* Transaction history.

* Merchant and shop definitions.

* Item values.

* Stock and restocking.

* Merchant Credit pools.

* Buy and sell pricing.

* Buyback.

* Trading screens.

* Related networking, persistence and synchronization.

### 2. Smartphone Platform

Inspect:

* Basic Phone item.

* Initialization/setup state.

* Phone data components.

* Dedicated equipment slot.

* TAB/menu gating.

* Phone tiers.

* App registration and unlocking.

* Character, Skills, Quests, Codex, Spells, Abilities, Wallet, Map, Classes, Inventory, Mail and Settings integrations.

* Mail and Phone Store code, where present.

* Banking App code, where present.

* Networking, persistence and synchronization.

### 3. Rest and Fatigue

Inspect:

* Rest ability.

* Bed interception.

* Short Rest and Long Rest entry points.

* Rest session tracking.

* Rest durations.

* Nap, Meditation and Reading activities.

* Animations.

* Cancellation and interruption.

* Session resumption.

* Short Rest limits.

* HP, Hit Dice, Stamina, spell-slot and class-resource recovery.

* Warlock Pact Magic.

* Wizard Arcane Recovery.

* Monk Ki.

* Barbarian Rage.

* Multiplayer rest behavior.

* Ancestry/species rest overrides.

* Outdoor-rest events.

* Comfort.

* Any existing Fatigue, Rest Need, Sleep Debt, Exhaustion or anti-rest-spam code.

### 4. RPG Architecture

Document the implementation that actually exists for:

* Player level and XP.

* Attributes.

* Attribute modifiers and derived statistics.

* Skills and skill XP.

* Masteries.

* Classes and class levels.

* Subclasses.

* Feats.

* Spell definitions.

* Spell learning and ownership.

* Spell slots.

* Mana.

* Casting.

* Cantrips.

* Abilities.

* Species and Origins.

* Rest integration.

* Components.

* Persistence.

* Synchronization.

* Registries.

* Events.

* Networking.

This RPG section must be derived from code rather than reconstructed from planning notes.

## Precedence

When implementation and documentation differ:

1. Do not automatically assume either side is correct.

2. Report the discrepancy.

3. Distinguish between:

  * implementation that is newer or more concrete,

  * implementation that contradicts an adopted design,

  * documentation terminology that differs without changing behavior,

  * missing implementation,

  * dormant or superseded implementation.

For systems marked `VERIFY AGAINST CODE`, treat the implementation as the primary evidence, but still identify meaningful differences from the recovered design.

## Required report structure

### A. Executive summary

Include:

* Systems inspected.

* Overall level of alignment.

* Major conflicts.

* Major missing implementations.

* Areas where implementation appears newer than the documents.

* Areas requiring Stefan’s design decision.

* Any parts that could not be verified.

### B. Discrepancy table

For every meaningful finding, include:

| ID | System | Document section | Code location | Category | Severity | Finding | Recommended next action |

| -- | ------ | ---------------- | ------------- | -------- | -------- | ------- | ----------------------- |

Use these categories exactly:

1. **DOCUMENT MATCHES IMPLEMENTATION**

2. **IMPLEMENTATION IS NEWER — UPDATE DOCUMENT**

3. **DOCUMENTED FEATURE IS NOT IMPLEMENTED**

4. **IMPLEMENTATION CONFLICTS WITH ADOPTED DESIGN**

5. **UNUSED, DUPLICATED OR SUPERSEDED CODE**

6. **SAFE DOCUMENTATION-ONLY NAMING DIFFERENCE**

7. **CANNOT VERIFY**

8. **REQUIRES STEFAN’S DESIGN DECISION**

Severity:

* `CRITICAL`

* `HIGH`

* `MEDIUM`

* `LOW`

* `INFORMATIONAL`

### C. Per-system analysis

Create separate sections for:

* Economy / Banking / Trading

* Phone

* Rest

* Fatigue / Rest Need

* RPG foundations

* Attributes

* Skills and Masteries

* Classes and Subclasses

* Spells and Spell Slots

* Species and Origins

* Cross-system foundations

For each section include:

* What exists.

* What matches.

* What differs.

* What is missing.

* What appears obsolete.

* Relevant code paths.

* Recommended next step.

### D. Current implementation map

Produce a concise architecture map containing:

* Important packages.

* Main managers/services.

* Components.

* Registries.

* Persistent data.

* Events.

* Network packets.

* Screens and screen handlers.

* Data/resource definitions.

* Dependencies between systems.

### E. Documentation updates recommended

List documentation changes that appear appropriate because implementation is clearly newer or more concrete.

Do not apply them.

### F. Code changes recommended

List implementation, refactor, migration and cleanup work that may eventually be needed.

Do not apply them.

Separate these into:

* Bug fix.

* Missing implementation.

* Refactor.

* Migration.

* Cleanup.

* Design decision required.

### G. Questions for Stefan

Only include questions that cannot be resolved from code or the supplied documents.

Do not ask questions merely because names differ or because a proposed/TBD feature is not implemented.

## Evidence requirements

Every finding must include:

* Exact file path.

* Relevant class, method, field, identifier or resource name.

* Line numbers where practical.

* Which document section it was compared against.

* A short explanation of why it belongs in that discrepancy category.

Do not make unsupported assumptions based only on class names. Trace actual behavior, registration, persistence, packets and call sites where necessary.

## Final confirmation

End the report with this exact checklist:

* [ ] No code was modified.

* [ ] No documentation was modified.

* [ ] No resources or data files were modified.

* [ ] No refactors or fixes were applied.

* [ ] All proposed changes are recommendations only.

Write the complete audit report to a new file:

`TOTALITY_IMPLEMENTATION_AUDIT_REPORT.md`

Do not overwrite, edit, rename, or reformat any existing code, resource, configuration, or documentation file.

The report file is the only file you may create during this pass.

