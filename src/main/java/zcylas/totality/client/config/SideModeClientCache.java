package zcylas.totality.client.config;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import zcylas.totality.api.energy.base.SimpleSidedUEContainer;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class SideModeClientCache {

    // Persistent cache — stores modes for all known blocks
    private static final Map<BlockPos, Map<Direction, SimpleSidedUEContainer.SideMode>> cache
            = new HashMap<>();

    // Last opened GUI position — for the config tab
    private static BlockPos lastOpenedPos = null;

    public static void set(BlockPos pos, Map<Direction, SimpleSidedUEContainer.SideMode> modes) {
        cache.put(pos, new EnumMap<>(modes));
        if (lastOpenedPos == null) lastOpenedPos = pos;
    }

    public static void set(BlockPos pos, Direction face, SimpleSidedUEContainer.SideMode mode) {
        cache.computeIfAbsent(pos, p -> new EnumMap<>(Direction.class)).put(face, mode);
    }

    public static SimpleSidedUEContainer.SideMode get(BlockPos pos, Direction face) {
        Map<Direction, SimpleSidedUEContainer.SideMode> modes = cache.get(pos);
        if (modes == null) return SimpleSidedUEContainer.SideMode.NONE;
        return modes.getOrDefault(face, SimpleSidedUEContainer.SideMode.NONE);
    }

    public static Map<Direction, SimpleSidedUEContainer.SideMode> getAll(BlockPos pos) {
        return cache.getOrDefault(pos, Map.of());
    }

    public static boolean has(BlockPos pos) {
        return cache.containsKey(pos);
    }

    public static void setLastOpenedPos(BlockPos pos) {
        lastOpenedPos = pos;
    }

    public static BlockPos getLastOpenedPos() {
        return lastOpenedPos;
    }

    public static void clear() {
        cache.clear();
        lastOpenedPos = null;
    }
    public static Set<BlockPos> getAllPositions() {
        return cache.keySet();
    }

    private SideModeClientCache() {}
}