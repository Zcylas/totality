package zcylas.totality.networking.item;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

/**
 * Sent by the client to the server when the player successfully holds R
 * over a TotalityItem for the full 10-second attunement duration.
 *
 * {@code slotIndex} is the container slot the item occupies.
 */
public record AttunementPayload(int slotIndex) implements CustomPacketPayload {

    public static final Identifier ID =
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "attune_item");

    public static final Type<AttunementPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, AttunementPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, AttunementPayload::slotIndex,
                    AttunementPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}