package zcylas.totality.networking.ancestry;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.Totality;
import zcylas.totality.api.ability.AbilityComponent;
import zcylas.totality.api.ability.AbilityComponents;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.rpg.ancestry.*;

public final class SelectAncestryHandler {

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(
                SelectAncestryPayload.TYPE,
                (payload, context) -> {
                    ServerPlayer player = context.player();
                    context.server().execute(() -> handle(player, payload));
                }
        );
    }

    private static void handle(ServerPlayer player, SelectAncestryPayload payload) {
        Totality.LOGGER.info("SelectAncestryHandler called: species=" + payload.speciesName() + " origin=" + payload.originName());
        // Parse species
        Species species;
        try { species = Species.valueOf(payload.speciesName()); }
        catch (IllegalArgumentException e) { return; }

        // Parse origin
        Origin origin = null;
        if (payload.originName() != null) {
            try { origin = Origin.valueOf(payload.originName()); }
            catch (IllegalArgumentException e) { return; }
        }

        // Apply ancestry — handles stats, height, sync
        PlayerAncestryComponent ancestry = AncestryComponents.PLAYER_ANCESTRY.get(
                (ComponentProvider) player);
        ancestry.clearAncestry();
        ancestry.selectAncestry(species, origin);

        // Unlock starting abilities
        if (origin != null && !origin.getStartingAbilities().isEmpty()) {
            AbilityComponent abilities = AbilityComponents.ABILITIES.get(
                    (ComponentProvider) player);
            for (Identifier id : origin.getStartingAbilities()) {
                abilities.unlock(id);
            }
            AbilityComponents.ABILITIES.sync((ComponentProvider) player);
        } else {
            Totality.LOGGER.info("No starting abilities for origin: " + (origin != null ? origin.name() : "null"));
        }
    }

    private SelectAncestryHandler() {}
}