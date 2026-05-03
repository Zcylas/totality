package zcylas.totality.networking.alchemy;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Sent client -> server when the player clicks Craft.
 * Contains the registry names of the selected ingredient items (2-3).
 */
public record BrewPayload(List<Identifier> ingredientIds)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BrewPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath("totality", "brew")
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, BrewPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeInt(payload.ingredientIds().size());
                        for (Identifier id : payload.ingredientIds()) {
                            buf.writeIdentifier(id);
                        }
                    },
                    buf -> {
                        int size = buf.readInt();
                        List<Identifier> ids = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) {
                            ids.add(buf.readIdentifier());
                        }
                        return new BrewPayload(ids);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}