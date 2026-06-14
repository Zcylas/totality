package zcylas.totality.networking.dialogue;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

/** C2S — player selects a dialogue choice by its 0-based index in the displayed list. */
public record DialogueChoicePayload(int choiceIndex) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DialogueChoicePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "dialogue_choice"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DialogueChoicePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeInt(p.choiceIndex()),
                    buf -> new DialogueChoicePayload(buf.readInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
