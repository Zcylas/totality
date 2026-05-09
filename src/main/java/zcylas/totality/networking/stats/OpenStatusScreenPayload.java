package zcylas.totality.networking.stats;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

/**
 * Sent server → client to open the StatusScreen.
 * Triggered by /totality stats command.
 * TODO: Remove when TAB radial menu is implemented.
 */
public record OpenStatusScreenPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenStatusScreenPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "open_status_screen"));

    public static final StreamCodec<FriendlyByteBuf, OpenStatusScreenPayload> CODEC =
            StreamCodec.of((buf, payload) -> {}, buf -> new OpenStatusScreenPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}