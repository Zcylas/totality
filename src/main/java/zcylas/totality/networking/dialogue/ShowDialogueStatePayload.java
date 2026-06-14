package zcylas.totality.networking.dialogue;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

import java.util.List;

/** S2C — opens or updates the dialogue screen. Also used to close it (ended = true). */
public record ShowDialogueStatePayload(
        int npcEntityId,
        Component npcName,
        Component npcText,
        List<ChoiceDisplayData> choices,
        boolean unskippable,
        boolean ended
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ShowDialogueStatePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "show_dialogue_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShowDialogueStatePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeInt(p.npcEntityId());
                        ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.encode(buf, p.npcName());
                        ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.encode(buf, p.npcText());
                        ChoiceDisplayData.LIST_STREAM_CODEC.encode(buf, p.choices());
                        buf.writeBoolean(p.unskippable());
                        buf.writeBoolean(p.ended());
                    },
                    buf -> new ShowDialogueStatePayload(
                            buf.readInt(),
                            ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.decode(buf),
                            ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.decode(buf),
                            ChoiceDisplayData.LIST_STREAM_CODEC.decode(buf),
                            buf.readBoolean(),
                            buf.readBoolean()
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
