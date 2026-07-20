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
 * Phase 2C adds {@code totality:mana} and {@code totality:stamina} — transitional
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
 */
public final class PlayerResourceIds {

    public static final Identifier HEALTH = Identifier.fromNamespaceAndPath(Totality.MOD_ID, "health");
    public static final Identifier FOOD = Identifier.fromNamespaceAndPath(Totality.MOD_ID, "food");
    public static final Identifier BREATH = Identifier.fromNamespaceAndPath(Totality.MOD_ID, "breath");
    public static final Identifier MANA = Identifier.fromNamespaceAndPath(Totality.MOD_ID, "mana");
    public static final Identifier STAMINA = Identifier.fromNamespaceAndPath(Totality.MOD_ID, "stamina");
    public static final Identifier SPELL_SLOTS = Identifier.fromNamespaceAndPath(Totality.MOD_ID, "spell_slots");

    public static final Identifier HEALTH_ADAPTER = HEALTH;
    public static final Identifier FOOD_ADAPTER = FOOD;
    public static final Identifier BREATH_ADAPTER = BREATH;
    public static final Identifier MANA_ADAPTER = MANA;
    public static final Identifier STAMINA_ADAPTER = STAMINA;
    public static final Identifier SPELL_SLOTS_ADAPTER = SPELL_SLOTS;

    private PlayerResourceIds() {}
}
