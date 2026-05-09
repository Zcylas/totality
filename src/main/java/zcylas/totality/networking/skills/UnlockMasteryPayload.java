package zcylas.totality.networking.skills;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;
import zcylas.totality.api.rpg.skills.core.Skill;

/**
 * Sent client → server when player clicks Unlock on a mastery node.
 */
public record UnlockMasteryPayload(Skill skill, String masteryId)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UnlockMasteryPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "unlock_mastery"));

    public static final StreamCodec<FriendlyByteBuf, UnlockMasteryPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> { buf.writeEnum(p.skill()); buf.writeUtf(p.masteryId()); },
                    buf -> new UnlockMasteryPayload(buf.readEnum(Skill.class), buf.readUtf())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}