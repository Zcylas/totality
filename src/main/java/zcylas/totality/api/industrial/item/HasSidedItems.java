package zcylas.totality.api.industrial.item;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.networking.config.ItemSideModeSyncPayload;

import java.util.EnumMap;
import java.util.Map;

public interface HasSidedItems {
    SimpleSidedItemContainer getItemSides();

    default void syncItemSideModes(ServerPlayer player, BlockPos pos) {
        Map<Direction, ItemSideMode> modes = new EnumMap<>(Direction.class);
        for (Direction dir : Direction.values()) {
            modes.put(dir, getItemSides().getSideMode(dir));
        }
        ServerPlayNetworking.send(player, new ItemSideModeSyncPayload(pos, modes));
    }
}