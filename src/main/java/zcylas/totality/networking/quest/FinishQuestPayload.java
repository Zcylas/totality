package zcylas.totality.networking.quest;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

/** C2S — player pressed "Finish Quest" on a quest whose objectives are all complete. */
public record FinishQuestPayload(String questId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FinishQuestPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "finish_quest"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FinishQuestPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeUtf(p.questId()),
                    buf -> new FinishQuestPayload(buf.readUtf())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
