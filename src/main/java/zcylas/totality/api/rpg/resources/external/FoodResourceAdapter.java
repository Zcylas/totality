package zcylas.totality.api.rpg.resources.external;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import zcylas.totality.api.rpg.resources.PlayerResourceDefinition;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.ResourceSnapshot;

import java.util.Set;

/**
 * Wraps vanilla {@code Player.getFoodData().getFoodLevel()}. Food/Hunger remains fully
 * authoritative for food level, saturation, exhaustion, eating eligibility, natural
 * regeneration/starvation interaction, and persistence — this adapter only ever reads the primary
 * Food level. See {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §25.2.
 *
 * Food is integral (no fractional Food value exists anywhere in vanilla or Totality), so
 * {@code unitScale = 1} — a raw {@code getFoodLevel()} int is already a valid unit amount, no
 * conversion needed. Saturation and exhaustion are deliberately never read here: they are
 * owner-specific metadata, not part of the generic primary-Food scalar (canonical §6.5's Food
 * section: "Saturation and exhaustion remain owner-specific state/metadata").
 */
public final class FoodResourceAdapter implements ExternalPlayerResourceAdapter {

    public static final Identifier ID = PlayerResourceIds.FOOD_ADAPTER;

    /** Vanilla hardcodes 20 as the food level ceiling; there is no {@code getMaxFoodLevel()}. */
    public static final int NATIVE_MAXIMUM = 20;

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public ResourceSnapshot snapshot(Player player, PlayerResourceDefinition definition) {
        int level = player.getFoodData().getFoodLevel();
        return new ResourceSnapshot(definition.id(), level, NATIVE_MAXIMUM, definition.unitScale());
    }

    @Override
    public Set<ExternalResourceOperationSupport> supportedOperations() {
        return Set.of(ExternalResourceOperationSupport.QUERY);
    }

    @Override
    public ExternalResourceClientMirrorMode clientMirrorMode() {
        return ExternalResourceClientMirrorMode.NATIVE_SYNCHRONIZATION;
    }

    public static final FoodResourceAdapter INSTANCE = new FoodResourceAdapter();
}
