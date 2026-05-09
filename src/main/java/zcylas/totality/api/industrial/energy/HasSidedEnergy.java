package zcylas.totality.api.industrial.energy;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.industrial.energy.base.SimpleSidedUEContainer;
import zcylas.totality.networking.config.SideModeSyncPayload;

import java.util.EnumMap;
import java.util.Map;

public interface HasSidedEnergy {

    SimpleSidedUEContainer getEnergy();

    default void syncSideModes(ServerPlayer player, BlockPos pos) {
        Map<Direction, SimpleSidedUEContainer.SideMode> modes = new EnumMap<>(Direction.class);
        for (Direction dir : Direction.values()) {
            modes.put(dir, getEnergy().getSideMode(dir));
        }
        ServerPlayNetworking.send(player, new SideModeSyncPayload(pos, modes));
    }
}