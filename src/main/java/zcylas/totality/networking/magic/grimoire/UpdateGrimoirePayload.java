package zcylas.totality.networking.magic.grimoire;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;
import zcylas.totality.api.magic.grimoire.GrimoireCaster;

public record UpdateGrimoirePayload(GrimoireCaster caster)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UpdateGrimoirePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "update_grimoire"));

    public static final StreamCodec<FriendlyByteBuf, UpdateGrimoirePayload> CODEC =
            StreamCodec.composite(
                    GrimoireCaster.STREAM_CODEC, UpdateGrimoirePayload::caster,
                    UpdateGrimoirePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}