package zcylas.totality.networking.config;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;
import zcylas.totality.api.industrial.energy.base.SimpleSidedUEContainer;

public record SideModePayload(BlockPos pos, Direction face, SimpleSidedUEContainer.SideMode mode)
        implements CustomPacketPayload {

    public static final Type<SideModePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "side_mode"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SideModePayload> CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, SideModePayload::pos,
                    Direction.STREAM_CODEC, SideModePayload::face,
                    ByteBufCodecs.INT.map(
                            i -> SimpleSidedUEContainer.SideMode.values()[i],
                            SimpleSidedUEContainer.SideMode::ordinal),
                    SideModePayload::mode,
                    SideModePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}