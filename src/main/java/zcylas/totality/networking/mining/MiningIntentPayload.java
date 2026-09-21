package zcylas.totality.networking.mining;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

/**
 * Client -> server mining INTENT, sent only when it changes (never per tick). It carries no target,
 * no claim of a hit and NO force value: for Power Mining the server times the hold itself
 * ({@code POWER_START} .. {@code POWER_RELEASE}) and derives the force from its own clock, so a modified
 * client cannot request an impossible force, nor a power swing that was never started.
 */
public record MiningIntentPayload(Action action) implements CustomPacketPayload {

    public enum Action { HOLD_START, HOLD_STOP, POWER_START, POWER_RELEASE, POWER_CANCEL }

    public static final CustomPacketPayload.Type<MiningIntentPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "mining_intent"));

    public static final StreamCodec<FriendlyByteBuf, MiningIntentPayload> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeEnum(p.action()),
            buf -> new MiningIntentPayload(buf.readEnum(Action.class)));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
