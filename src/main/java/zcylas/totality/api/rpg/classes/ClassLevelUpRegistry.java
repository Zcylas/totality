package zcylas.totality.api.rpg.classes;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ClassLevelUpRegistry {

    @FunctionalInterface
    public interface LevelUpHandler {
        void onLevelUp(ServerPlayer player, int playerLevel, int classLevel);
    }

    private static final Map<Identifier, LevelUpHandler> HANDLERS = new LinkedHashMap<>();

    public static void register(Identifier classId, LevelUpHandler handler) {
        HANDLERS.put(classId, handler);
    }

    public static void fire(ServerPlayer player, Identifier classId, int playerLevel) {
        LevelUpHandler handler = HANDLERS.get(classId);
        if (handler != null) {
            int classLevel = PlayerClassComponent.toClassLevel(playerLevel);
            handler.onLevelUp(player, playerLevel, classLevel);
        }
    }

    private ClassLevelUpRegistry() {}
}