# Totality Implementation Gaps

Audit date: 2026-07-15. Evidence is current production code; `Inspiration Mods/` is excluded.

## 1. Executive summary

The largest gaps are ownership and lifecycle boundaries, not absence of code. Totality has many working vertical slices, but generic and legacy resources coexist; feature grants are not governed by a unified entitlement mechanism; merchant runtime state is temporary; spell access/preparation is incomplete; and verification is dominated by development startup checks rather than repeatable integration tests.

## 2. Critical foundations

### Generic resource storage exists, but the generic definition/behavior layer does not

- **Affected system/current status:** resources; partially implemented.
- **Evidence:** `PlayerResourceComponent.java` is explicitly the single value store for mana/stamina and their managers delegate to it; however, it has fixed fields while ticks, calculations, client managers and packets remain domain-specific.
- **Why it matters/missing:** adding Thirst, Ki or Pact Magic still requires bespoke storage fields and behavior/sync/HUD paths rather than registered generic definitions.
- **Affected systems:** Generic Player Resource API, Thirst, class resources, rest, Spell API.
- **Priority/confidence/evidence sufficiency:** Required before dependent API; high; inspection sufficient for architectural fact, runtime tests required for migration behavior.

### Unlock/access/entitlement foundation is absent

- **Affected system/current status:** grants; designed/documented but not implemented.
- **Evidence:** canonical `Context/Audit/TOTALITY_UNLOCK_ACCESS_AND_ENTITLEMENT_API.md`; current grants remain distributed through ability, class, ancestry, spell and item systems.
- **Why it matters/missing:** no shared source attribution, revocation, temporary grant, visibility, or availability semantics.
- **Affected systems:** spells, abilities, classes, origins, equipment, Soulbound, phone apps.
- **Priority/confidence/evidence sufficiency:** Required before dependent API; high; inspection sufficient.

## 3. Blocks another system

### Spell ownership and preparation are incomplete

- **Status/evidence:** implemented but incomplete; `api/magic/spell/Spell.java`, `SpellRegistry`, slot/concentration components, spell implementations and selection handlers exist, while access is still tied to default/temporary behavior and no complete known/prepared entitlement model was found.
- **Why/missing/affected:** class/origin completion, multiclass spell access, component pouch/focus, Dispel Magic and disease intervention require authoritative access and preparation rules.
- **Priority/confidence/testing:** Required before dependent API; high; runtime negative tests required.

### Equipment death and Soulbound policy is not complete

- **Status/evidence:** custom equipment is implemented (`api/equipment/*`, accessory menu and packets), but no complete central policy covering vanilla inventory, custom slots, attuned items, starter gear, physical Credits, and Soulbound was found.
- **Why/missing/affected:** outfitting and Soulbound can duplicate or lose items without one death-copy/drop owner.
- **Priority/confidence/testing:** Required before dependent API; medium-high; runtime death/respawn tests required.

### Durable merchant state is missing

- **Status/evidence:** SELL and Provisioner are implemented, but `api/shop/MerchantRuntimeRegistry.java` explicitly describes a temporary stand-in keyed runtime model.
- **Why/missing/affected:** restart persistence, per-entity identity, restock, buyback and merchant credit pools cannot be assumed durable.
- **Priority/confidence/testing:** Recommended soon; high; restart/unload tests required.

## 4. Important incomplete implementations

- **Classes/subclasses:** backend levels and selection exist, but per-class features and multiclass acquisition rules are uneven. Priority: recommended soon; confidence high.
- **Species/origins:** registries and selection exist; grant/revocation and content breadth are incomplete. Priority: required before class/origin completion; confidence high.
- **Skills/masteries:** persistence/UI exist, but gameplay hooks and authored mastery coverage are uneven. Priority: important, not blocking; confidence high.
- **Rest:** sessions and UI exist, but HP/Hit Dice, class resources, eligibility and multiplayer/time behavior are not a complete adopted implementation. Priority: recommended soon; confidence high, runtime required.
- **Phone:** item and screens are reachable; app inventory, upgrade/tier state, gating and delivery/store/mail remain partial. Priority: important, not blocking; confidence high.
- **Alchemy:** a functional brewing vertical slice does not establish the planned generalized Alchemy ecosystem. Priority: future consideration; confidence high.
- **World generation:** code is registered, but correctness and biome placement cannot be established without a fresh-world run. Priority: migration prerequisite; confidence medium.

## 5. Persistence and synchronization gaps

- **No explicit data-version/migration layer located** for the custom player-component NBT stored by `mixin/MixinServerPlayer.java`. Saved schema evolution is therefore a migration risk. Required before broad 26.2/component redesign; high confidence.
- **Merchant runtime durability is uncertain/temporary** (`MerchantRuntimeRegistry`). Restart/entity-unload testing required.
- **One-time grants/source revocation are distributed**, so idempotency cannot be established globally. U/A/E required; high confidence.
- **Client state uses both generic component sync and dedicated managers/packets** for mana/stamina and other caches. Packet ordering/reconnect clearing requires runtime tests; high confidence.
- **Concentration cleanup is incomplete:** `ConcentrationComponent.java` contains a TODO to notify an active spell for effect cleanup. Recommended soon; high confidence.

## 6. Client/server and networking gaps

- `TotalityServerPacketHandlers` side-configuration handlers accept a block position and mutate a matching BE; code inspection did not establish proximity/menu ownership checks. Runtime/adversarial packet testing is required. Priority: recommended soon; confidence medium.
- Grimoire update packets replace item component state from client payload after only locating a held grimoire. Validate slot bounds/formulas server-side before expanding multiplayer use. Priority: required before Spell API; confidence medium-high.
- UI reachability is broad, but some screens depend on debug commands, server packets, equipped-phone state, or content-specific NPCs. Manual reachability matrix required. Priority: important, not blocking.

## 7. Unregistered or unreachable functionality

- Several classes and assets are broader than their registrations/content. In particular, magic formula/rune classes, individual phone apps, and some screens require normal-game path verification.
- `ShortRestActivity.READ` is explicitly a placeholder.
- Development verification classes are debug-only even when they exercise production logic.
- No claim is made that every registered item/block/entity has a survival acquisition route.

## 8. Hardcoded or temporary behavior

- `QuestManager.java` states first-pass objective handling is hardcoded per quest.
- `MerchantRuntimeRegistry.java` is a temporary runtime-state stand-in.
- `Skill.java` uses placeholder icons/items for at least Unarmed and Hunting.
- `DisintegrateSpell.java` uses sulphur dust as a placeholder result.
- `ProficiencyBonus.java`, `SavingThrow.java`, and weapon classes retain class/proficiency TODOs; `CombatResolver.java` retains mob-stat TODOs.

## 9. Missing or weak verification

Development startup verification exists for item values, merchant SELL, Provisioner, trading UI, and renderer behavior, but there is no conventional `src/test` suite in the inspected repository. Missing coverage includes component schema migration, death/respawn across all components, reconnect packet ordering, malicious packets, dedicated-server classloading, multiplayer rest, merchant restart persistence, and world-generation smoke tests.

## 10. Minecraft 26.2 migration risks

- Thirty-plus mixins in `totality.mixins.json` target mapped methods/classes and are the highest breakage surface.
- Worldgen includes `MultiNoiseBiomeSourceParameterListMixin`, custom climate/surface rules and features.
- GUI/render internals and special renderers are version-sensitive.
- Payload codecs, data components, Fabric Transfer API fluid lookup, menus, loot accessors and Java 25/Loom snapshot must be revalidated against declared 26.2 versions.
- Migration should precede large foundational APIs to avoid simultaneous semantic and mapping changes.

## 11. Documentation/implementation mismatches

The 2026-07-12/13 implementation report is outdated in important areas: item values, functional SELL, Provisioner, quests, generic resource scaffolding, equipment/attunement, ability checks, fluids and worldgen now exist. The newer post-audit decisions remain authoritative for intended direction, but their planned APIs must not be treated as implemented.

## 12. Runtime questions requiring manual testing

Component copy-on-death policy per key; dimension transfer; restart/reconnect; multiplayer rest consensus; phone gating on every shortcut; normal survival access to every screen; merchant persistence; hostile packet validation; fluid/energy side access; worldgen placement; spell access negative cases; attunement limits and removal; concentration effect cleanup.

## 13. Cleanup and low-priority gaps

Update placeholder Fabric metadata in `fabric.mod.json`, remove or resolve stale TODOs only when their owning systems are implemented, and inventory unused assets after reachability tests. These are optional cleanup and not evidence of broken gameplay.
