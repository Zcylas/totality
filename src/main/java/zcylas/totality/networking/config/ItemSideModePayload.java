package zcylas.totality.networking.config;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;
import zcylas.totality.api.item.ItemSideMode;

public record ItemSideModePayload(BlockPos pos, Direction face)
        implements CustomPacketPayload {

    public static final Type<ItemSideModePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "item_side_mode"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ItemSideModePayload> CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, ItemSideModePayload::pos,
                    Direction.STREAM_CODEC, ItemSideModePayload::face,
                    ItemSideModePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}