package zcylas.totality.networking.shop;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

/**
 * C2S — player sells {@code quantity} of the stack in inventory slot {@code slotIndex} to the
 * currently active trade session's merchant. The server re-reads and revalidates the real stack
 * at that slot; nothing about sellability or payout is trusted from the client.
 *
 * <p>Phase 4 correction pass, Part A: {@code confirmedReducedPayout} is a typed consent flag for
 * the underfunded-merchant confirmation flow — the Trading Screen sets it only after the player
 * has explicitly clicked "Sell for ₵X" on the confirmation popup. {@code confirmedTotalValue}/
 * {@code confirmedPayableAmount} echo the exact terms the player saw and accepted; the server
 * NEVER treats these as authoritative — it independently recomputes the current quote and rejects
 * as stale ({@code SellResult.Reason.STALE_CONFIRMATION}) unless both match exactly. A normal,
 * fully-funded SELL leaves {@code confirmedReducedPayout} false and the two amount fields at 0 —
 * they are unused in that path.
 */
public record SellItemPayload(
        int slotIndex, int quantity, boolean confirmedReducedPayout, long confirmedTotalValue, long confirmedPayableAmount
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SellItemPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "sell_item"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SellItemPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeInt(p.slotIndex());
                        buf.writeInt(p.quantity());
                        buf.writeBoolean(p.confirmedReducedPayout());
                        buf.writeVarLong(p.confirmedTotalValue());
                        buf.writeVarLong(p.confirmedPayableAmount());
                    },
                    buf -> new SellItemPayload(
                            buf.readInt(), buf.readInt(), buf.readBoolean(), buf.readVarLong(), buf.readVarLong())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
