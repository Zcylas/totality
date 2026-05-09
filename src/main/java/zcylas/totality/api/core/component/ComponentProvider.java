package zcylas.totality.api.core.component;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Implemented by any object that can host components — players, block entities, etc.
 * Mix this into your target class and store a ComponentContainer on it.
 */
public interface ComponentProvider {

    ComponentContainer getComponentContainer();

    /**
     * Returns the players that should receive sync packets for this provider's components.
     * Override this in your provider implementation.
     */
    default Iterable<ServerPlayer> getComponentSyncRecipients() {
        return List.of();
    }

    /**
     * Called by ComponentKey.sync() — sends the component's sync packet to all recipients.
     * The default implementation handles the standard case via getComponentSyncRecipients().
     * Override only if you need custom routing.
     */
    default <C extends SyncedComponent> void syncComponent(ComponentKey<C> key, C component) {
        for (ServerPlayer player : getComponentSyncRecipients()) {
            if (component.shouldSyncWith(player)) {
                ComponentSync.sendSyncPacket(key, component, player);
            }
        }
    }
}