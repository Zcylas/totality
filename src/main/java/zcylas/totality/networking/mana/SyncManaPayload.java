package zcylas.totality.networking.mana;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

public record SyncManaPayload(int mana, int maxMana) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncManaPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "sync_mana"));

    public static final StreamCodec<FriendlyByteBuf, SyncManaPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, SyncManaPayload::mana,
                    ByteBufCodecs.INT, SyncManaPayload::maxMana,
                    SyncManaPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}