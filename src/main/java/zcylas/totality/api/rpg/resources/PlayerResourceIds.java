package zcylas.totality.api.rpg.resources;

import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

/**
 * Stable resource/adapter identifiers for the Generic Player Resource API. Kept as constants
 * rather than inlined strings so a definition and its adapter can never drift apart, and so tests
 * reference the same identifiers production code registers under.
 *
 * The adapter id intentionally equals the resource id for Health, Food, and Breath, matching the
 * canonical examples ({@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §25.1/§25.2: "Adapter:
 * totality:health" / "Adapter: totality:food").
 *
 * Phase 2B adds {@code totality:breath} — the canonical name for what vanilla calls "air supply."
 * Deliberately not {@code totality:oxygen}/{@code totality:air}: see the Phase 2B report's vanilla
 * air audit for why "Breath" was chosen as the stable, forward-looking name.
 */
public final class PlayerResourceIds {

    public static final Identifier HEALTH = Identifier.fromNamespaceAndPath(Totality.MOD_ID, "health");
    public static final Identifier FOOD = Identifier.fromNamespaceAndPath(Totality.MOD_ID, "food");
    public static final Identifier BREATH = Identifier.fromNamespaceAndPath(Totality.MOD_ID, "breath");

    public static final Identifier HEALTH_ADAPTER = HEALTH;
    public static final Identifier FOOD_ADAPTER = FOOD;
    public static final Identifier BREATH_ADAPTER = BREATH;

    private PlayerResourceIds() {}
}
