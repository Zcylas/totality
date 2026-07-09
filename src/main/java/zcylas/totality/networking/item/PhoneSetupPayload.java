package zcylas.totality.networking.item;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

/**
 * Sent by the client when the player confirms the Phone's setup screen ("Begin").
 * {@code equipped} tells the server to resolve the Phone equipment slot instead of a hand;
 * {@code offhand} only matters when {@code equipped} is false.
 */
public record PhoneSetupPayload(boolean equipped, boolean offhand) implements CustomPacketPayload {

    public static final Identifier ID =
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "phone_setup_complete");

    public static final Type<PhoneSetupPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, PhoneSetupPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, PhoneSetupPayload::equipped,
                    ByteBufCodecs.BOOL, PhoneSetupPayload::offhand,
                    PhoneSetupPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
