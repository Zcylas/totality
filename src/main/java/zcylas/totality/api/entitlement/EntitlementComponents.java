package zcylas.totality.api.entitlement;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.core.component.ComponentKey;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.core.component.ComponentRegistry;
import zcylas.totality.api.core.component.PlayerComponentEvents;

public final class EntitlementComponents {

    public static final ComponentKey<PlayerEntitlementComponent> ENTITLEMENTS = ComponentRegistry.getOrCreate(
            Identifier.fromNamespaceAndPath("totality", "entitlements"),
            PlayerEntitlementComponent.class
    );

    private EntitlementComponents() {}

    public static void register() {
        // Durable facts and persisted grants always survive respawn; UNTIL_DEATH grants end on a real death
        // (the framework's "lossless" flag is false exactly when the player died).
        PlayerComponentEvents.registerForPlayers(
                ENTITLEMENTS,
                PlayerEntitlementComponent::new,
                (from, to, registries, lossless, keepInventory) -> to.copyForRespawn(from, !lossless)
        );
    }

    public static PlayerEntitlementComponent get(ServerPlayer player) {
        return ENTITLEMENTS.get((ComponentProvider) player);
    }
}
