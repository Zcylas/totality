package zcylas.totality.api.rpg.resources;

import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

/**
 * Stable resource/adapter identifiers for the Generic Player Resource API. Kept as constants
 * rather than inlined strings so a definition and its adapter can never drift apart, and so tests
 * reference the same identifiers production code registers under.
 *
 * The adapter id intentionally equals the resource id for Health, Food, Breath, Mana, and Stamina,
 * matching the canonical examples ({@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §25.1/§25.2:
 * "Adapter: totality:health" / "Adapter: totality:food").
 *
 * Phase 2B adds {@code totality:breath} — the canonical name for what vanilla calls "air supply."
 * Deliberately not {@code totality:oxygen}/{@code totality:air}: see the Phase 2B report's vanilla
 * air audit for why "Breath" was chosen as the stable, forward-looking name.
 *
 * <p><b>Correction (2026-09-16, Phase 8 V1 readiness audit):</b> the next three paragraphs describe
 * Mana/Stamina (Phase 2C), Spell Slots (Phase 2D), and Rage (Phase 2E) as "transitional {@code
 * EXTERNAL_ADAPTER}" identifiers whose migration to {@code GENERIC_COMPONENT} was still future work.
 * That migration has since happened for all three (Mana/Stamina: Phase 4, 2026-09-15; Rage: Phase 5,
 * 2026-09-15; Spell Slots: Phase 6, 2026-09-16) — all four ({@code totality:mana}, {@code
 * totality:stamina}, {@code totality:rage}, {@code totality:spell_slots}) are now real, authoritative
 * {@code GENERIC_COMPONENT} resources, identical in kind to Thirst/Sanity/Ki below except that these
 * four are actively granted/spent/restored by real gameplay systems rather than dormant. The ids
 * themselves never changed, exactly as each paragraph below already anticipated — only {@code
 * stateAuthority}/{@code definitionVersion} changed, per {@code ProductionResourceDefinitions}. See
 * each resource's own migration implementation report for the full detail. The paragraphs below are
 * preserved for history rather than rewritten.
 *
 * <p>Phase 2C adds {@code totality:mana} and {@code totality:stamina} — transitional
 * {@code EXTERNAL_ADAPTER} identifiers over the legacy-authoritative {@code PlayerResourceComponent}
 * store (see {@code ManaResourceAdapter}/{@code StaminaResourceAdapter}). These are the same stable
 * ids the resources will keep after a future migration to {@code GENERIC_COMPONENT} authority — only
 * the definition's {@code stateAuthority}/{@code externalAdapterId}/{@code definitionVersion} change
 * at that point, never the id.
 *
 * <p>Phase 2D adds {@code totality:spell_slots} — a transitional, {@code PARTITIONED_POOL}-model,
 * {@code EXTERNAL_ADAPTER}-authority identifier over the legacy-authoritative {@code SpellSlotComponent}
 * store (see {@code StandardSpellSlotsResourceAdapter}). This is the Resource API's first
 * {@code PARTITIONED_POOL} production resource. Note: the legacy {@code TotalityComponent} registered
 * under the component id {@code totality:spell_slots} ({@code SpellSlotComponents.SPELL_SLOTS}) and
 * this Resource API resource id are deliberately allowed to share the same literal namespaced string
 * — they live in entirely separate registries ({@code ComponentRegistry} vs {@code
 * PlayerResourceRegistry}) and are never looked up interchangeably, so no rename of either is needed.
 *
 * <p>Phase 2E adds {@code totality:rage} — a transitional, {@code SCALAR}-model, {@code
 * EXTERNAL_ADAPTER}-authority identifier over one entry of the legacy-authoritative, generically
 * {@code Identifier}-keyed {@code PlayerChargesComponent} charge-pool map (see {@code RageResourceAdapter}).
 * Deliberately <b>not</b> the same literal string as its legacy backing key: the existing charge pool
 * is keyed by {@code BarbarianRageAbility.CHARGE_ID = totality:barbarian_rage}, which is preserved
 * exactly, unrenamed — the Resource API resource id and the legacy pool key are two different,
 * independent identifiers by design (unlike Phase 2D's spell-slot component/resource id overlap,
 * which was coincidental, not deliberate).
 *
 * <p>Phase 7A adds {@code totality:health_recovery_dice} (see its own field doc below).
 *
 * <p><b>2026-09-17 Food 0-100 migration</b> (post-V1, using V1's established extension points, not
 * a V1 foundation change): {@code totality:food} migrates from {@code EXTERNAL_ADAPTER} (querying
 * vanilla {@code FoodData}, 0-20) to {@code GENERIC_COMPONENT} authority (a true resource, baseline
 * maximum 100 via {@code FoodMaximumResolver}, extensible for exceptional future physiology) — the
 * same shape as the Mana/Stamina/Rage/Spell Slot migrations above, at {@code definitionVersion = 2}.
 * Unlike those four, vanilla's own {@code FoodData.foodLevel} is kept alive, but strictly as a
 * lossy, endpoint-preserving <i>compatibility projection</i>, never a second authority — see
 * {@code FoodVanillaCompatibilityBridge}. Ordinary vanilla eating, Cake, the Saturation mob effect,
 * and hunger-exhaustion's depletion are all bridged into authoritative Food; vanilla's own Food-based
 * sprint gate is bypassed (Stamina owns sprint endurance) and its direct starvation Health damage is
 * disabled (natural Food-based Health regen was already disabled). Vanilla Saturation remains
 * temporary depletion/compatibility machinery only, intended to evolve into the future Metabolic
 * Reserve concept; sustained-underfeeding consequences belong to the future Diet/Metabolism/Fatigue
 * direction, not to this migration.
 *
 * <p><b>Corrected 2026-09-17 (real-client manual test correction):</b> two further issues surfaced
 * only by testing on a real client/server, not by the automated test suite. First, Peaceful
 * difficulty's automatic Food restore — originally bridged into authoritative Food like the other
 * vanilla-triggered changes above — is now disabled entirely ({@code
 * ServerPlayerPeacefulFoodRestoreAuthorityMixin} suppresses it rather than translating it): Food
 * represents physical fullness/hunger and must never regenerate merely because the difficulty is
 * Peaceful. Second, the client Resource façade ({@code
 * zcylas.totality.client.resource.TotalityClientResourceReaders}) had never been updated to route
 * {@code totality:food} to the generic-synchronized reader after this migration — it was still wired
 * to the native reader, which answers straight out of vanilla {@code FoodData}, so the HUD displayed
 * the lossy 0-20 mirror (20/20, 10/20) instead of the true resource (100/100, 50/100). Both are now
 * fixed; see {@code TOTALITY_FOOD_0_100_AND_TOTALITY_FOOD_ITEM_IMPLEMENTATION_REPORT_2026-09-17.md}
 * (§33 is authoritative) for the full detail.
 *
 * <p>The dormant Resource Registration pass adds {@code totality:thirst}, {@code totality:sanity},
 * and {@code totality:ki} — at the time of that pass, the first {@code GENERIC_COMPONENT}-authority
 * production identifiers with no owning system yet (still true — see each field's own "Dormant" doc
 * below); Mana/Stamina/Rage/Spell Slots/Health Recovery Dice are also {@code GENERIC_COMPONENT} now,
 * but all five are actively owned, unlike these three.
 * Unlike every id above, none of these three has an adapter constant: they are registered with no
 * owning system, no grant provider, and no live query path yet (see
 * {@code TOTALITY_DORMANT_RESOURCE_REGISTRATION_IMPLEMENTATION_REPORT.md}). {@code totality:fatigue}
 * and {@code totality:temperature} are deliberately absent — see that report's Fatigue/Temperature
 * sections for why.
 */
public final class PlayerResourceIds {

    public static final Identifier HEALTH = Identifier.fromNamespaceAndPath(Totality.MOD_ID, "health");
    public static final Identifier FOOD = Identifier.fromNamespaceAndPath(Totality.MOD_ID, "food");
    public static final Identifier BREATH = Identifier.fromNamespaceAndPath(Totality.MOD_ID, "breath");
    public static final Identifier MANA = Identifier.fromNamespaceAndPath(Totality.MOD_ID, "mana");
    public static final Identifier STAMINA = Identifier.fromNamespaceAndPath(Totality.MOD_ID, "stamina");
    public static final Identifier SPELL_SLOTS = Identifier.fromNamespaceAndPath(Totality.MOD_ID, "spell_slots");
    public static final Identifier RAGE = Identifier.fromNamespaceAndPath(Totality.MOD_ID, "rage");
    /** Spendable/restorable Health recovery pool — NOT the future Hit Die API (a separate,
     *  unimplemented Character Creation/Progression system that will eventually govern
     *  class/resource growth rolls). See {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §25.10. */
    public static final Identifier HEALTH_RECOVERY_DICE = Identifier.fromNamespaceAndPath(Totality.MOD_ID, "health_recovery_dice");

    /** Dormant — registered with no owning system, no grant provider, no adapter. */
    public static final Identifier THIRST = Identifier.fromNamespaceAndPath(Totality.MOD_ID, "thirst");
    /** Dormant — registered with no owning system, no grant provider, no adapter. */
    public static final Identifier SANITY = Identifier.fromNamespaceAndPath(Totality.MOD_ID, "sanity");
    /** Dormant — registered with no owning system, no grant provider, no adapter, no authored maximum. */
    public static final Identifier KI = Identifier.fromNamespaceAndPath(Totality.MOD_ID, "ki");

    public static final Identifier HEALTH_ADAPTER = HEALTH;
    public static final Identifier FOOD_ADAPTER = FOOD;
    public static final Identifier BREATH_ADAPTER = BREATH;
    public static final Identifier MANA_ADAPTER = MANA;
    public static final Identifier STAMINA_ADAPTER = STAMINA;
    public static final Identifier SPELL_SLOTS_ADAPTER = SPELL_SLOTS;
    public static final Identifier RAGE_ADAPTER = RAGE;

    private PlayerResourceIds() {}
}
