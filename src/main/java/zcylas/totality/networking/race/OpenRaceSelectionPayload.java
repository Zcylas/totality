package zcylas.totality.networking.race;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Sent server → client to tell the client to open the race selection screen.
 * Carries no data — the client reads Race enum values directly.
 */
public record OpenRaceSelectionPayload()
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenRaceSelectionPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath("totality", "open_race_selection")
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenRaceSelectionPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {},
                    buf -> new OpenRaceSelectionPayload()
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}