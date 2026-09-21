package zcylas.totality.networking.resource;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

/**
 * C2S — "my generic Resource view is stale, please send a full resync." Deliberately carries no
 * player-identifying or target-selecting field: the server always resolves the request against the
 * sending connection's own player only (see {@link ResourceResyncRequestHandler}), so this payload
 * is structurally incapable of requesting another player's state.
 */
public record ResourceResyncRequestPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ResourceResyncRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "resource_sync_resync_request"));

    public static final StreamCodec<FriendlyByteBuf, ResourceResyncRequestPayload> CODEC =
            StreamCodec.unit(new ResourceResyncRequestPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
