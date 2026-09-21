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

    /**
     * Fires the class-level-up handler registered for {@code classId}, if any, passing that
     * SPECIFIC class's own actual stored level — not the player's overall Class Level
     * entitlement ({@link PlayerClassComponent#toClassLevel(int)}, which is a multiclass-wide
     * total across every class the player has invested in).
     *
     * <p><b>Bug fix:</b> this previously computed {@code classLevel} via
     * {@code PlayerClassComponent.toClassLevel(playerLevel)} — the global entitlement formula —
     * regardless of which class was actually being fired for. For a single-class character this
     * happens to look correct only because that one class's level always equals the global
     * entitlement; the moment a player multiclasses (spends their available points across more
     * than one class, exactly as the BG3-style design intends), every class's level-up handler
     * received the player's TOTAL entitlement instead of that class's own level — e.g. a
     * Barbarian at real Barbarian level 1 (with the rest of the player's points spent elsewhere)
     * could fire with {@code classLevel} such as 6, silently missing the {@code classLevel == 3}
     * Primal Path subclass trigger at the real Barbarian level 3, or triggering subclass-selection
     * logic at the wrong moment entirely. Now sources {@code classLevel} from
     * {@link PlayerClassComponent#getClassLevel(Identifier)} — the actual per-class stored value
     * every other consumer (the Class tab UI, {@code AddClassLevelHandler}) already reads.
     */
    public static void fire(ServerPlayer player, Identifier classId, int playerLevel) {
        LevelUpHandler handler = HANDLERS.get(classId);
        if (handler != null) {
            int classLevel = ClassComponents.get(player).getClassLevel(classId);
            handler.onLevelUp(player, playerLevel, classLevel);
        }
    }

    private ClassLevelUpRegistry() {}
}