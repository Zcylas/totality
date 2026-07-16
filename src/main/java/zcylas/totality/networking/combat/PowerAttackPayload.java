package zcylas.totality.networking.combat;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

/**
 * Sent when the player's mainhand power-attack charge completes. Carries the entity id the
 * client saw under its crosshair at that moment (correction pass, Part C) — the server never
 * trusts this alone; it re-resolves the entity and re-validates it via
 * {@link zcylas.totality.api.rpg.combat.PowerAttackManager#isValidTarget} before consuming any
 * Stamina or marking advantage.
 */
public record PowerAttackPayload(int targetEntityId)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PowerAttackPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "power_attack"));

    public static final StreamCodec<FriendlyByteBuf, PowerAttackPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeVarInt(p.targetEntityId()),
                    buf -> new PowerAttackPayload(buf.readVarInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}