package zcylas.totality.client.config;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import zcylas.totality.api.industrial.item.ItemSideMode;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ItemSideModeClientCache {

    private static final Map<BlockPos, Map<Direction, ItemSideMode>> cache = new HashMap<>();

    public static ItemSideMode get(BlockPos pos, Direction dir) {
        return cache.getOrDefault(pos, Map.of()).getOrDefault(dir, ItemSideMode.NONE);
    }

    public static void set(BlockPos pos, Direction dir, ItemSideMode mode) {
        cache.computeIfAbsent(pos, k -> new EnumMap<>(Direction.class)).put(dir, mode);
    }

    public static void setAll(BlockPos pos, Map<Direction, ItemSideMode> modes) {
        cache.put(pos, new EnumMap<>(modes));
    }

    public static Set<BlockPos> getAllPositions() {
        return cache.keySet();
    }

    public static Map<Direction, ItemSideMode> getAll(BlockPos pos) {
        return cache.getOrDefault(pos, Map.of());
    }

    public static void invalidate(BlockPos pos) {
        cache.remove(pos);
    }

    private ItemSideModeClientCache() {}
}