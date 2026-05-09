package zcylas.totality.networking.stats;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;
import zcylas.totality.api.rpg.stats.AbilityScore;

/**
 * Sent client → server when the player clicks a + button on the status screen.
 * Server validates the player has unspent points and spends one into the given score.
 */
public record SpendAttributePointPayload(AbilityScore score)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SpendAttributePointPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "spend_attribute_point"));

    public static final StreamCodec<FriendlyByteBuf, SpendAttributePointPayload> CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeEnum(payload.score()),
                    buf -> new SpendAttributePointPayload(buf.readEnum(AbilityScore.class))
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}