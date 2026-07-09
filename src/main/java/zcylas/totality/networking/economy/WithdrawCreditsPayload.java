package zcylas.totality.networking.economy;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

/** C2S — withdraws {@code amount} from the player's account (Wallet) as physical Credits. */
public record WithdrawCreditsPayload(long amount) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<WithdrawCreditsPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "withdraw_credits"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WithdrawCreditsPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeVarLong(p.amount()),
                    buf -> new WithdrawCreditsPayload(buf.readVarLong())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
