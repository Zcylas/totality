package zcylas.totality.networking.movement;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Sent client → server when the player toggles biological flight
 * via the Movement Power Key + Space.
 */
public record ToggleFlightPayload(boolean enabled)
        implements CustomPacketPayload {

    public static final Type<ToggleFlightPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("totality", "toggle_flight"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleFlightPayload> CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeBoolean(payload.enabled()),
                    buf -> new ToggleFlightPayload(buf.readBoolean())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}