package zcylas.totality.networking.item;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

public record UnAttunePayload(int slotIndex) implements CustomPacketPayload {

    public static final Type<UnAttunePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "unattune_item"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UnAttunePayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, UnAttunePayload::slotIndex,
                    UnAttunePayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}