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

import java.util.HashMap;
import java.util.Map;

public record SideModeSyncPayload(BlockPos pos, Map<Direction, SimpleSidedUEContainer.SideMode> sideModes)
        implements CustomPacketPayload {

    public static final Type<SideModeSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "side_mode_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SideModeSyncPayload> CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, SideModeSyncPayload::pos,
                    ByteBufCodecs.map(
                            HashMap::new,
                            Direction.STREAM_CODEC,
                            ByteBufCodecs.INT.map(
                                    i -> SimpleSidedUEContainer.SideMode.values()[i],
                                    SimpleSidedUEContainer.SideMode::ordinal)
                    ), SideModeSyncPayload::sideModes,
                    SideModeSyncPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}