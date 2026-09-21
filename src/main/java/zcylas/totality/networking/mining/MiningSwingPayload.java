package zcylas.totality.networking.mining;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

/**
 * Server -> client, once per NORMAL mining swing, at the moment the server begins it: the swing's real
 * timings in ticks (pre-contact delay, then recovery), including cadence (Efficiency/Haste/Fatigue).
 * The client animates exactly this schedule, so the visual cycle is the gameplay cycle. It carries no
 * authority: the server still decides whether an impact happens.
 */
public record MiningSwingPayload(int windUpTicks, int recoveryTicks) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MiningSwingPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "mining_swing"));

    public static final StreamCodec<FriendlyByteBuf, MiningSwingPayload> CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeVarInt(p.windUpTicks()); buf.writeVarInt(p.recoveryTicks()); },
            buf -> new MiningSwingPayload(buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
