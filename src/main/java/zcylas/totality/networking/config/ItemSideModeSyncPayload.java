package zcylas.totality.networking.config;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;
import zcylas.totality.api.industrial.item.ItemSideMode;

import java.util.HashMap;
import java.util.Map;

public record ItemSideModeSyncPayload(BlockPos pos, Map<Direction, ItemSideMode> sideModes)
        implements CustomPacketPayload {

    public static final Type<ItemSideModeSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "item_side_mode_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ItemSideModeSyncPayload> CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, ItemSideModeSyncPayload::pos,
                    ByteBufCodecs.map(
                            HashMap::new,
                            Direction.STREAM_CODEC,
                            ByteBufCodecs.INT.map(
                                    i -> ItemSideMode.values()[i],
                                    ItemSideMode::ordinal)
                    ), ItemSideModeSyncPayload::sideModes,
                    ItemSideModeSyncPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}