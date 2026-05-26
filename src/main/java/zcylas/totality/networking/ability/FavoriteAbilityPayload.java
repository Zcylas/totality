package zcylas.totality.networking.ability;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Sent client → server to toggle an ability in the player's favorites list.
 * Server toggles (add if absent, remove if present) and syncs back.
 */
public record FavoriteAbilityPayload(Identifier abilityId)
        implements CustomPacketPayload {

    public static final Type<FavoriteAbilityPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("totality", "favorite_ability"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FavoriteAbilityPayload> CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeIdentifier(payload.abilityId()),
                    buf -> new FavoriteAbilityPayload(buf.readIdentifier())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}