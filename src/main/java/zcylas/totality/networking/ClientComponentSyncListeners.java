package zcylas.totality.networking;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Registry for client-side component sync listeners.
 * Each system that needs to react to a component sync registers here
 * instead of adding hardcoded string checks to TotalityClientPacketHandlers.
 *
 * Register during client init:
 *   ClientComponentSyncListeners.register(id, buf -> MyClientManager.sync(buf));
 */
public final class ClientComponentSyncListeners {

    private static final Map<Identifier, Consumer<RegistryFriendlyByteBuf>> LISTENERS = new HashMap<>();

    public static void register(Identifier keyId, Consumer<RegistryFriendlyByteBuf> listener) {
        LISTENERS.put(keyId, listener);
    }

    public static void dispatch(Identifier keyId, byte[] data, RegistryAccess registryAccess) {
        Consumer<RegistryFriendlyByteBuf> listener = LISTENERS.get(keyId);
        if (listener == null) return;
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(
                Unpooled.wrappedBuffer(data), registryAccess);
        listener.accept(buf);
    }

    private ClientComponentSyncListeners() {}
}