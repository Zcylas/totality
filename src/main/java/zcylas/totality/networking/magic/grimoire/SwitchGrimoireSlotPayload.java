package zcylas.totality.networking.magic.grimoire;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SwitchGrimoireSlotPayload(int slot) implements CustomPacketPayload {

    public static final Type<SwitchGrimoireSlotPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("totality", "switch_grimoire_slot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SwitchGrimoireSlotPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT,
                    SwitchGrimoireSlotPayload::slot,
                    SwitchGrimoireSlotPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}