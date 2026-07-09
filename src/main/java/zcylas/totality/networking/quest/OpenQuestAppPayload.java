package zcylas.totality.networking.quest;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

/** C2S — player opened the Quests app on their phone. */
public record OpenQuestAppPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenQuestAppPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "open_quest_app"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenQuestAppPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> {}, buf -> new OpenQuestAppPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
