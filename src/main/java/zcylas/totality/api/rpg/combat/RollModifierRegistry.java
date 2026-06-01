package zcylas.totality.api.rpg.combat;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.dice.RollType;
import zcylas.totality.api.rpg.check.AbilityCheckResolver;
import zcylas.totality.api.rpg.stats.AbilityScore;

import java.util.*;

public final class RollModifierRegistry {

    public interface RollModifier {
        RollType modifySave(AbilityScore score, RollType current);
        AbilityCheckResolver.RollMode modifyCheck(AbilityScore score, AbilityCheckResolver.RollMode current);
    }

    private static final Map<UUID, Map<Identifier, RollModifier>> MODIFIERS = new HashMap<>();

    public static void register(ServerPlayer player, Identifier sourceId, RollModifier modifier) {
        MODIFIERS.computeIfAbsent(player.getUUID(), k -> new LinkedHashMap<>())
                .put(sourceId, modifier);
    }

    public static void remove(ServerPlayer player, Identifier sourceId) {
        Map<Identifier, RollModifier> map = MODIFIERS.get(player.getUUID());
        if (map != null) map.remove(sourceId);
    }

    public static RollType resolveSave(ServerPlayer player, AbilityScore score, RollType base) {
        Map<Identifier, RollModifier> map = MODIFIERS.get(player.getUUID());
        if (map == null || map.isEmpty()) return base;
        RollType result = base;
        for (RollModifier mod : map.values()) result = mod.modifySave(score, result);
        return result;
    }

    public static AbilityCheckResolver.RollMode resolveCheck(ServerPlayer player,
                                                             AbilityScore score,
                                                             AbilityCheckResolver.RollMode base) {
        Map<Identifier, RollModifier> map = MODIFIERS.get(player.getUUID());
        if (map == null || map.isEmpty()) return base;
        AbilityCheckResolver.RollMode result = base;
        for (RollModifier mod : map.values()) result = mod.modifyCheck(score, result);
        return result;
    }

    /** Call on player disconnect to clean up. */
    public static void clearPlayer(UUID playerId) {
        MODIFIERS.remove(playerId);
    }

    private RollModifierRegistry() {}
}