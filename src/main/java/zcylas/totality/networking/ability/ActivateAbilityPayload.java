package zcylas.totality.networking.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.Totality;
import zcylas.totality.api.ability.AbilityContext;
import zcylas.totality.api.ability.AbilityRegistry;

/**
 * Sent client → server when the player presses the ability key.
 * Context is null for abilities with no block target (e.g. Short Rest).
 */
public record ActivateAbilityPayload(Identifier abilityId, @Nullable BlockPos pos)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ActivateAbilityPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "activate_ability"));

    public static final StreamCodec<FriendlyByteBuf, ActivateAbilityPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeIdentifier(p.abilityId());
                        buf.writeBoolean(p.pos() != null);
                        if (p.pos() != null) buf.writeBlockPos(p.pos());
                    },
                    buf -> {
                        Identifier id = buf.readIdentifier();
                        BlockPos pos = buf.readBoolean() ? buf.readBlockPos() : null;
                        return new ActivateAbilityPayload(id, pos);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}