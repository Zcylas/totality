package zcylas.totality.networking.movement;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PowerSprintStatePayload(boolean active) implements CustomPacketPayload {

    public static final Type<PowerSprintStatePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("totality", "power_sprint_state")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, PowerSprintStatePayload> CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeBoolean(payload.active()),
                    buf -> new PowerSprintStatePayload(buf.readBoolean())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}