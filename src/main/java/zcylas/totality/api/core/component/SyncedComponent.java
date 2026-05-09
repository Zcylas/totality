package zcylas.totality.api.core.component;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/**
 * Extend this if your component needs to be synced to the client.
 * Default implementation uses NBT round-trip via readData/writeData.
 */
public interface SyncedComponent extends TotalityComponent {

    void writeSyncPacket(RegistryFriendlyByteBuf buf, ServerPlayer recipient);
    void applySyncPacket(RegistryFriendlyByteBuf buf);

    default boolean shouldSyncWith(ServerPlayer player) {
        return true;
    }
}