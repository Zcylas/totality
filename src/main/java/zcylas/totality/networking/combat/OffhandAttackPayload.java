package zcylas.totality.networking.combat;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

/** Sent when the player right-clicks a target while dual-wielding one-handed weapons. */
public record OffhandAttackPayload(int targetEntityId, boolean powerAttack)
        implements CustomPacketPayload {

    public static final Type<OffhandAttackPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "offhand_attack"));

    public static final StreamCodec<FriendlyByteBuf, OffhandAttackPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> { buf.writeVarInt(p.targetEntityId()); buf.writeBoolean(p.powerAttack()); },
                    buf -> new OffhandAttackPayload(buf.readVarInt(), buf.readBoolean())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
