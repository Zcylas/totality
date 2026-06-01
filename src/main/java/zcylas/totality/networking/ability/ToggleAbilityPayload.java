// networking/ability/ToggleAbilityPayload.java
package zcylas.totality.networking.ability;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ToggleAbilityPayload(Identifier abilityId, boolean active)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ToggleAbilityPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath("totality", "toggle_ability")
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleAbilityPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeIdentifier(payload.abilityId());
                        buf.writeBoolean(payload.active());
                    },
                    buf -> new ToggleAbilityPayload(
                            buf.readIdentifier(),
                            buf.readBoolean()
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}