package zcylas.totality.networking.movement;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.api.core.movement.MovementMode;

public record MovementStaminaPayload(MovementMode mode) implements CustomPacketPayload {

    public static final Type<MovementStaminaPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("totality", "movement_stamina"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MovementStaminaPayload> CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeEnum(payload.mode()),
                    buf -> new MovementStaminaPayload(buf.readEnum(MovementMode.class))
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}