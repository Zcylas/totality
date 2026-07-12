package zcylas.totality.networking.rest;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;
import zcylas.totality.api.rpg.rest.RestType;

/** S2C — periodic (and on state-change) sync of the client's rest HUD countdown. */
public record RestTimeSyncPayload(boolean active, boolean inGrace, RestType restType,
                                   int remainingTicks, int graceRemainingTicks)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RestTimeSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "rest_time_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RestTimeSyncPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeBoolean(p.active());
                        if (!p.active()) return;
                        buf.writeBoolean(p.inGrace());
                        buf.writeEnum(p.restType());
                        buf.writeVarInt(p.remainingTicks());
                        buf.writeVarInt(p.graceRemainingTicks());
                    },
                    buf -> {
                        boolean active = buf.readBoolean();
                        if (!active) return cleared();
                        boolean inGrace = buf.readBoolean();
                        RestType type = buf.readEnum(RestType.class);
                        int remaining = buf.readVarInt();
                        int graceRemaining = buf.readVarInt();
                        return new RestTimeSyncPayload(true, inGrace, type, remaining, graceRemaining);
                    }
            );

    public static RestTimeSyncPayload cleared() {
        return new RestTimeSyncPayload(false, false, RestType.SHORT, 0, 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
