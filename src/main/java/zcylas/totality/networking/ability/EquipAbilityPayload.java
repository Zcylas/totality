package zcylas.totality.networking.ability;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.Totality;

public record EquipAbilityPayload(@Nullable Identifier abilityId)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EquipAbilityPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "equip_ability"));

    public static final StreamCodec<FriendlyByteBuf, EquipAbilityPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeBoolean(p.abilityId() != null);
                        if (p.abilityId() != null) buf.writeIdentifier(p.abilityId());
                    },
                    buf -> new EquipAbilityPayload(buf.readBoolean() ? buf.readIdentifier() : null)
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}