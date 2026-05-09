package zcylas.totality.networking.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

/**
 * Sent server → client to open the MainMenuScreen via TAB keybind.
 * TODO: wire TAB keybind on client side directly without a packet.
 */
public record OpenMainMenuPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenMainMenuPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "open_main_menu"));

    public static final StreamCodec<FriendlyByteBuf, OpenMainMenuPayload> CODEC =
            StreamCodec.of((buf, p) -> {}, buf -> new OpenMainMenuPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}