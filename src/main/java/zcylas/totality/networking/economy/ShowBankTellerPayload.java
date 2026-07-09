package zcylas.totality.networking.economy;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

/** S2C — opens or refreshes the Banker Teller screen with the player's current balances. */
public record ShowBankTellerPayload(long walletBalance, long physicalCredits) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ShowBankTellerPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "show_bank_teller"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShowBankTellerPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeVarLong(p.walletBalance());
                        buf.writeVarLong(p.physicalCredits());
                    },
                    buf -> new ShowBankTellerPayload(buf.readVarLong(), buf.readVarLong())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
