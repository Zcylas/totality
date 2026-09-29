package zcylas.totality.api.equipment;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.core.component.ComponentKey;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.core.component.ComponentRegistry;
import zcylas.totality.api.core.component.PlayerComponentEvents;
import zcylas.totality.api.core.component.RespawnStrategy;

public final class EquipmentComponents {

    public static final ComponentKey<PlayerEquipmentComponent> EQUIPMENT =
            ComponentRegistry.getOrCreate(
                    Identifier.fromNamespaceAndPath("totality", "equipment"),
                    PlayerEquipmentComponent.class
            );

    public static void register() {
        PlayerComponentEvents.registerForPlayers(
                EQUIPMENT,
                PlayerEquipmentComponent::new,
                RespawnStrategy.ALWAYS_COPY
        );
        PlayerComponentEvents.registerClientComponent(
                EQUIPMENT,
                () -> new PlayerEquipmentComponent(null)
        );
        // The respawn copy replaces the component: tell the owner's client what was carried over (after death,
        // only Soulbound items remain unless keepInventory is on).
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                EQUIPMENT.sync((ComponentProvider) newPlayer));
    }

    public static PlayerEquipmentComponent get(ServerPlayer player) {
        return EQUIPMENT.get((ComponentProvider) player);
    }

    private EquipmentComponents() {}
}