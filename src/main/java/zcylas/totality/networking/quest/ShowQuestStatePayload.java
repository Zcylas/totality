package zcylas.totality.networking.quest;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

import java.util.List;

/** S2C — full snapshot of the player's known quests, resolved server-side (template + progress). */
public record ShowQuestStatePayload(List<QuestEntryDisplayData> quests) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ShowQuestStatePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "show_quest_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShowQuestStatePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> QuestEntryDisplayData.LIST_STREAM_CODEC.encode(buf, p.quests()),
                    buf -> new ShowQuestStatePayload(QuestEntryDisplayData.LIST_STREAM_CODEC.decode(buf))
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
