package zcylas.totality.networking.magic.grimoire;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

public record SyncGrimoireHudPayload(int currentSlot, String spellName)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncGrimoireHudPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "sync_grimoire_hud"));

    public static final StreamCodec<FriendlyByteBuf, SyncGrimoireHudPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, SyncGrimoireHudPayload::currentSlot,
                    ByteBufCodecs.STRING_UTF8, SyncGrimoireHudPayload::spellName,
                    SyncGrimoireHudPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}