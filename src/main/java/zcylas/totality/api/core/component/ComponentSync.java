package zcylas.totality.api.core.component;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public final class ComponentSync {

    public static final CustomPacketPayload.Type<Payload> PACKET_TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("totality", "component_sync"));

    public static final StreamCodec<FriendlyByteBuf, Payload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeIdentifier(payload.keyId());
                        buf.writeByteArray(payload.data());
                    },
                    buf -> new Payload(buf.readIdentifier(), buf.readByteArray())
            );

    private ComponentSync() {}

    public static <C extends SyncedComponent> void sendSyncPacket(
            ComponentKey<C> key,
            C component,
            ServerPlayer player
    ) {
        RegistryFriendlyByteBuf data = new RegistryFriendlyByteBuf(
                io.netty.buffer.Unpooled.buffer(),
                player.level().registryAccess()
        );
        try {
            component.writeSyncPacket(data, player);
            byte[] bytes = new byte[data.readableBytes()];
            data.readBytes(bytes);
            ServerPlayNetworking.send(player, new Payload(key.getId(), bytes));
        } finally {
            data.release();
        }
        System.out.println("[Totality] canSend: " + ServerPlayNetworking.canSend(player, ComponentSync.PACKET_TYPE));
    }

    public record Payload(Identifier keyId, byte[] data)
            implements CustomPacketPayload {

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return PACKET_TYPE;
        }
    }
}