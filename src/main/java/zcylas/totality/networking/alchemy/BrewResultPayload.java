package zcylas.totality.networking.alchemy;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Sent server → client after a successful brew.
 * Contains the potion name, and the list of newly discovered effects with their ingredient names.
 * If discoveredEffects is empty, the player already knew this potion — show only the toast message.
 * If discoveredEffects is non-empty, show the full popup.
 */
public record BrewResultPayload(
        String potionName,
        List<DiscoveredEffect> discoveredEffects
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BrewResultPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath("totality", "brew_result")
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, BrewResultPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUtf(payload.potionName());
                        buf.writeInt(payload.discoveredEffects().size());
                        for (DiscoveredEffect de : payload.discoveredEffects()) {
                            buf.writeUtf(de.effectName());
                            buf.writeUtf(de.ingredientName());
                        }
                    },
                    buf -> {
                        String potionName = buf.readUtf();
                        int size = buf.readInt();
                        List<DiscoveredEffect> effects = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) {
                            effects.add(new DiscoveredEffect(buf.readUtf(), buf.readUtf()));
                        }
                        return new BrewResultPayload(potionName, effects);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record DiscoveredEffect(String effectName, String ingredientName) {}
}