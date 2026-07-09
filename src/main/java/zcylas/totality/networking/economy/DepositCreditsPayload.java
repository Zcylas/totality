package zcylas.totality.networking.economy;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

/** C2S — deposits {@code amount} physical Credits into the player's account (Wallet). */
public record DepositCreditsPayload(long amount) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DepositCreditsPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "deposit_credits"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DepositCreditsPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeVarLong(p.amount()),
                    buf -> new DepositCreditsPayload(buf.readVarLong())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
