package zcylas.totality.networking.classes;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

/**
 * Sent server → client to open the {@link zcylas.totality.screen.classes.SubclassSelectionScreen}
 * when a player's class level reaches the subclass unlock threshold.
 *
 * Carries the class ID so the client knows which class's subclasses to show.
 */
public record OpenSubclassSelectionPayload(String classId) implements CustomPacketPayload {

    public static final Type<OpenSubclassSelectionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "open_subclass_selection"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenSubclassSelectionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, OpenSubclassSelectionPayload::classId,
                    OpenSubclassSelectionPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}