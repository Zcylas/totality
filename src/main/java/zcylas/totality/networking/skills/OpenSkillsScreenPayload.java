package zcylas.totality.networking.skills;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

/**
 * Sent server → client to open the Skills screen.
 * Triggered by /totality skills command.
 */
public record OpenSkillsScreenPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenSkillsScreenPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "open_skills_screen"));

    public static final StreamCodec<FriendlyByteBuf, OpenSkillsScreenPayload> CODEC =
            StreamCodec.of((buf, payload) -> {}, buf -> new OpenSkillsScreenPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
