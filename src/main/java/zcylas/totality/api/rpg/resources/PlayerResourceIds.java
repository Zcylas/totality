package zcylas.totality.api.rpg.resources;

import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

/**
 * Stable resource/adapter identifiers for the Generic Player Resource API. Kept as constants
 * rather than inlined strings so a definition and its adapter can never drift apart, and so tests
 * reference the same identifiers production code registers under.
 *
 * The adapter id intentionally equals the resource id for both Health and Food, matching the
 * canonical examples ({@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §25.1/§25.2: "Adapter:
 * totality:health" / "Adapter: totality:food").
 */
public final class PlayerResourceIds {

    public static final Identifier HEALTH = Identifier.fromNamespaceAndPath(Totality.MOD_ID, "health");
    public static final Identifier FOOD = Identifier.fromNamespaceAndPath(Totality.MOD_ID, "food");

    public static final Identifier HEALTH_ADAPTER = HEALTH;
    public static final Identifier FOOD_ADAPTER = FOOD;

    private PlayerResourceIds() {}
}
