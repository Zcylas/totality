package zcylas.totality.networking.mining;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.Totality;
import zcylas.totality.api.mining.MiningResult;

/**
 * Server -> client, once per released Power swing: what the strike actually did, so the Power Mining HUD
 * shows impact feedback only for a real hit (never for a miss, a void swing or a dropped release).
 * Presentation only; it carries no authority and changes nothing.
 */
public record PowerStrikeResultPayload(Outcome outcome) implements CustomPacketPayload {

    public enum Outcome {
        /** The block took damage and still stands. */
        DAMAGED,
        /** The block broke. */
        BROKEN,
        /** A block was struck with no effect (tier too low, refused, not a valid target). */
        NO_EFFECT,
        /** Nothing was struck (no target at contact, obstructed, source changed, dropped release). */
        MISS;

        public static Outcome of(@Nullable MiningResult result) {
            if (result == null) return MISS;
            return switch (result.outcome()) {
                case DAMAGED -> DAMAGED;
                case BROKEN -> BROKEN;
                default -> NO_EFFECT;
            };
        }

        public boolean landed() {
            return this == DAMAGED || this == BROKEN;
        }
    }

    public static final CustomPacketPayload.Type<PowerStrikeResultPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "power_strike_result"));

    private static final Outcome[] OUTCOMES = Outcome.values();

    public static final StreamCodec<FriendlyByteBuf, PowerStrikeResultPayload> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeByte(p.outcome().ordinal()),
            buf -> {
                int i = buf.readByte();
                return new PowerStrikeResultPayload(i >= 0 && i < OUTCOMES.length ? OUTCOMES[i] : Outcome.MISS);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
