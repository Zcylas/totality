package zcylas.totality.networking.stamina;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

public record SyncStaminaPayload(int stamina, int maxStamina) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncStaminaPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "sync_stamina"));

    public static final StreamCodec<FriendlyByteBuf, SyncStaminaPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, SyncStaminaPayload::stamina,
                    ByteBufCodecs.INT, SyncStaminaPayload::maxStamina,
                    SyncStaminaPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}