package zcylas.totality.networking.rest;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;
import zcylas.totality.api.rpg.rest.RestType;

/**
 * S2C — periodic (and on state-change) sync of the client's rest HUD countdown.
 *
 * @param lyingDown true when the server is forcing Pose.SLEEPING on this player itself rather
 *                  than a genuine vanilla sleep (see RestSessionManager's class doc) — vanilla
 *                  locks the camera to third-person for real sleep on its own, but our fake pose
 *                  needs the client to do the same manually, or first-person sits at the pose's
 *                  much shorter eye height and looks broken.
 */
public record RestTimeSyncPayload(boolean active, boolean inGrace, RestType restType,
                                   int remainingTicks, int graceRemainingTicks, boolean lyingDown)
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
                        buf.writeBoolean(p.lyingDown());
                    },
                    buf -> {
                        boolean active = buf.readBoolean();
                        if (!active) return cleared();
                        boolean inGrace = buf.readBoolean();
                        RestType type = buf.readEnum(RestType.class);
                        int remaining = buf.readVarInt();
                        int graceRemaining = buf.readVarInt();
                        boolean lyingDown = buf.readBoolean();
                        return new RestTimeSyncPayload(true, inGrace, type, remaining, graceRemaining, lyingDown);
                    }
            );

    public static RestTimeSyncPayload cleared() {
        return new RestTimeSyncPayload(false, false, RestType.SHORT, 0, 0, false);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
