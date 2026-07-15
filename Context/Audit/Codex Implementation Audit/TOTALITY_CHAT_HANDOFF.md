# Totality Chat Handoff

Audit date: 2026-07-15.

## 1. What Totality is

Totality is a private Fabric Minecraft mod with RPG, magic, combat, economy, narrative, equipment, industrial, fluid, ritual and world-content systems. It is a large in-progress codebase, not a collection of fully finished APIs.

## 2. Current technical environment

`gradle.properties`, `build.gradle` and `fabric.mod.json` declare Minecraft 26.1.2, Fabric Loader 0.19.2, Fabric API 0.146.0+26.1.2, Loom 1.16-SNAPSHOT and Java 25. Main/client entry points are `zcylas.totality.Totality` and `TotalityClient`.

## 3. Current repository architecture

`Totality.onInitialize()` registers components, content, entities, menus, packet codecs/handlers, APIs, gameplay events, server ticks, resource reloaders and worldgen. `TotalityClient` registers renderers, screens, HUD/key/input behavior and client packet handlers. A custom component system is injected into server/local players and owns much persistence/sync. Data packs feed mob stats, dialogue, NPC names, quests, shops, Provisioner assortments, item values and ritual recipes.

## 4. Systems definitely implemented

Persistent/synced player components; attributes/level/XP; skills/masteries core; ability checks/dice; ability registry and activation; class/ancestry selection backends; spell registry and multiple executable spells; spell slots/concentration; custom combat and weapon rules; rest sessions; Credits wallet/physical item/banking; data-driven item values; BUY and SELL trading; Provisioner assortment; dialogue actions; quests; phone shell/apps; custom equipment slots and attunement handlers; alchemy brewing vertical slice; fluid tanks; UE energy/machines; rituals; NPCs; many items/blocks/entities; HUD/screens/renderers; custom biomes/features.

## 5. Systems implemented but incomplete

Classes/subclasses/species/origins and their grants; skills/mastery coverage; spell access/known/prepared rules; concentration cleanup; generic resources; rest recovery/multiplayer semantics; phone app/tier progression; quest objective generality; durable merchant runtime; equipment death/Soulbound; worldgen verification; generalized Alchemy/Fluid/Enchantment foundations.

## 6. Scaffolded, unreachable, debug-only, or uncertain systems

Development `*Verification` startup checks are debug-only. Some phone apps, rune/formula features, screens and registered content need normal-game reachability tests. `ShortRestActivity.READ`, some skill icons and a Disintegrate result are explicit placeholders. Relationship, backgrounds, universal thirst, disease/medicine/cooking/architecture and vehicles are not proven implemented.

## 7. Important player components and persistence behavior

Components include stats, skills, masteries, abilities, generic resources, combat state, ancestry, movement, classes/charges, rest, spell slots, concentration, equipment, currency, dialogue flags and quests. `MixinServerPlayer` creates and serializes the container; a generic component packet syncs compatible state to the local player. Inspect each component’s respawn strategy before assuming death-copy behavior. No global schema-version/migration layer was found.

## 8. Current major-domain status

- **Classes/origins:** selectable and persistent; feature content/ownership incomplete.
- **Spells/abilities:** executable and UI-integrated; authorization and source grants incomplete.
- **Resources:** mana/stamina values share `PlayerResourceComponent`; definitions, calculations, ticks and some client paths are not yet the complete generic API.
- **Economy:** Credits, banking, BUY, SELL, prices and Provisioner work in code; merchant runtime durability/account tiers/ATM remain incomplete.
- **Rest:** session engine and UI work; full recovery and multiplayer/time rules need tests.
- **UI:** extensive and registered, but not every screen has a proven normal-game access path.
- **Equipment:** custom slots and attunement exist; death drops and Soulbound are not a completed policy.

## 9. Major implementation gaps

26.2 regression baseline; completion of generic resource definitions/behavior; U/A/E implementation; component schema migration; server-owned spell authorization; death/equipment policy; durable merchant state; packet-negative tests; lifecycle/multiplayer/worldgen verification.

## 10. Highest-priority architecture improvements

Migrate to 26.2 separately; version/test component saves; finish generic resource definitions/behavior around shared storage; implement source-aware entitlements; centralize spell authorization; define death handling across vanilla/custom inventories; harden packet context validation; persist merchant identity/state.

## 11. Current migration concerns

The code has many version-sensitive mixins, worldgen hooks, GUI/render internals, payload codecs, Fabric Transfer integration and mapped accessors. Do not combine 26.2 mapping changes with foundational semantic rewrites.

## 12. Important design/implementation discrepancies

The July 12/13 audit predates functional item values/SELL/Provisioner and several newer APIs. Canonical post-audit decisions are newer than that report. Conversely, canonical Generic Resource and U/A/E documents describe intended APIs that are not yet fully implemented.

## 13. Detailed documents to consult

Read `TOTALITY_IMPLEMENTED_SYSTEMS_AUDIT.md` for evidence, `TOTALITY_IMPLEMENTATION_INDEX.md` for navigation, `TOTALITY_IMPLEMENTATION_GAPS.md` for missing work, `TOTALITY_ARCHITECTURE_IMPROVEMENTS.md` for tasks, and `TOTALITY_DEPENDENCY_AND_READINESS_MAP.md` for sequencing.

## 14. Subjects requiring earlier design conversations

Final multiclass acquisition/allocation, backgrounds, starter equipment, physical Credits, Soulbound, spell known/prepared rules, Fluid API breadth, disease lifecycle, Medicine, Farming/Cooking quality, Enchantment/Smithing magical properties, Architecture, electricity/plumbing/sewage/environment, vehicles/fuels.

## 15. Suggested next-chat questions

1. Which 26.2 acceptance tests and old-save fixtures are mandatory?
2. What exact legacy resource keys map into the Generic Resource API?
3. How should existing spell/ability/class/origin grants map to U/A/E sources?
4. What is the authoritative death matrix for every inventory and Soulbound?
5. Which planned domain should be designed only after those foundations?

## Receiving-chat rules

Production code is the source of truth for implementation status. Earlier chats and canonical documents are required for final design intent. Newer decisions override older discussions. Uncertain items must not be guessed.
