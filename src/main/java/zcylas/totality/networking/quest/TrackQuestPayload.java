package zcylas.totality.networking.quest;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

/** C2S — player toggled tracking on a quest. */
public record TrackQuestPayload(String questId, boolean tracked) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TrackQuestPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "track_quest"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TrackQuestPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> { buf.writeUtf(p.questId()); buf.writeBoolean(p.tracked()); },
                    buf -> new TrackQuestPayload(buf.readUtf(), buf.readBoolean())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
