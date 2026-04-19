package zcylas.totality.client.config;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import zcylas.totality.api.config.FaceConfig;

import java.util.EnumMap;
import java.util.Map;

public class EnergyFaceConfigClientCache {

    private static BlockPos cachedPos = null;
    private static final Map<Direction, FaceConfig> cachedFaceMap = new EnumMap<>(Direction.class);

    // Called when server sends full sync
    public static void set(BlockPos pos, Map<Direction, FaceConfig> faceMap) {
        cachedPos = pos;
        cachedFaceMap.clear();
        cachedFaceMap.putAll(faceMap);
    }

    // Called optimistically on client click
    public static void set(BlockPos pos, Direction face, FaceConfig config) {
        if (!pos.equals(cachedPos)) return;
        cachedFaceMap.put(face, config);
    }

    public static FaceConfig get(BlockPos pos, Direction face) {
        if (!pos.equals(cachedPos)) return FaceConfig.OUTPUT;
        return cachedFaceMap.getOrDefault(face, FaceConfig.OUTPUT);
    }

    public static boolean isFor(BlockPos pos) {
        return pos.equals(cachedPos);
    }

    private EnergyFaceConfigClientCache() {}
}