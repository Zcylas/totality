// networking/ancestry/SelectAncestryHandler.java
package zcylas.totality.networking.ancestry;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.Totality;
import zcylas.totality.api.ability.AbilityComponent;
import zcylas.totality.api.ability.AbilityComponents;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.rpg.ancestry.*;
import zcylas.totality.api.rpg.classes.ClassComponents;
import zcylas.totality.networking.classes.OpenClassSelectionPayload;

import java.util.List;

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
        Totality.LOGGER.info("SelectAncestryHandler: species={} origin={}",
                payload.speciesName(), payload.originName());

        Identifier speciesId = parseId(payload.speciesName());
        Identifier originId  = payload.originName() != null ? parseId(payload.originName()) : null;

        if (speciesId == null) return;
        if (SpeciesRegistry.get(speciesId) == null) {
            Totality.LOGGER.warn("Unknown species: {}", speciesId);
            return;
        }
        if (originId != null && OriginRegistry.get(originId) == null) {
            Totality.LOGGER.warn("Unknown origin: {}", originId);
            return;
        }

        AbilityComponent abilities = AbilityComponents.ABILITIES.get((ComponentProvider) player);

        // Remove all origin-granted abilities that the new origin doesn't grant
        OriginData newOrigin = originId != null ? OriginRegistry.get(originId) : null;
        List<Identifier> newAbilities = newOrigin != null
                ? newOrigin.getStartingAbilities() : List.of();

        OriginRegistry.all().stream()
                .flatMap(o -> o.getStartingAbilities().stream())
                .filter(id -> !newAbilities.contains(id))
                .forEach(abilities::forget);

        PlayerAncestryComponent ancestry = AncestryComponents.PLAYER_ANCESTRY.get(
                (ComponentProvider) player);
        ancestry.clearAncestry();
        ancestry.selectAncestry(speciesId, originId);

        // Unlock new origin's starting abilities
        if (newOrigin != null && !newOrigin.getStartingAbilities().isEmpty()) {
            for (Identifier id : newOrigin.getStartingAbilities()) {
                abilities.unlock(id);
            }
            AbilityComponents.ABILITIES.sync((ComponentProvider) player);
        }

        if (!ClassComponents.get(player).hasAnyClass()) {
            ServerPlayNetworking.send(player, new OpenClassSelectionPayload());
        }
    }

    /**
     * Parses an Identifier from a payload string.
     * Handles backward compat: old enum names like "KRYPTONIAN" become "totality:kryptonian".
     */
    private static Identifier parseId(String s) {
        if (s == null || s.isEmpty()) return null;
        if (!s.contains(":")) {
            return Identifier.fromNamespaceAndPath("totality", s.toLowerCase());
        }
        try {
            return Identifier.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    private SelectAncestryHandler() {}
}