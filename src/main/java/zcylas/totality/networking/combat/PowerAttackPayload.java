package zcylas.totality.networking.combat;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

public record PowerAttackPayload()
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PowerAttackPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "power_attack"));

    public static final StreamCodec<FriendlyByteBuf, PowerAttackPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {},
                    buf -> new PowerAttackPayload()
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}