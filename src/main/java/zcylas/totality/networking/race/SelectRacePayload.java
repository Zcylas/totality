package zcylas.totality.networking.race;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Sent client → server when the player confirms their race selection.
 * Carries the Race enum name as a string.
 */
public record SelectRacePayload(String raceName)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SelectRacePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath("totality", "select_race")
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, SelectRacePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeUtf(payload.raceName()),
                    buf -> new SelectRacePayload(buf.readUtf())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}