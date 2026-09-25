package zcylas.totality.networking.mining;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

/**
 * Server -> client, at the contact frame of a NORMAL swing whose actual target changed its cadence: the swing's
 * corrected recovery in ticks (the wind-up and contact moment are unchanged). The client re-times the recovery of
 * the swing it is animating, so the visual cycle stays the gameplay cycle. It carries no authority.
 */
public record MiningRecoveryPayload(int recoveryTicks) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MiningRecoveryPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "mining_recovery"));

    public static final StreamCodec<FriendlyByteBuf, MiningRecoveryPayload> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeVarInt(p.recoveryTicks()),
            buf -> new MiningRecoveryPayload(buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
