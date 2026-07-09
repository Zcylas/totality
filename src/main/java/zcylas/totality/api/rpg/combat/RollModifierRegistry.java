package zcylas.totality.api.rpg.combat;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.dice.DiceBonus;
import zcylas.totality.api.dice.RollType;
import zcylas.totality.api.rpg.check.AbilityCheckResolver;
import zcylas.totality.api.rpg.stats.AbilityScore;

import java.util.*;

public final class RollModifierRegistry {

    public interface RollModifier {
        RollType modifySave(AbilityScore score, RollType current);
        AbilityCheckResolver.RollMode modifyCheck(AbilityScore score, AbilityCheckResolver.RollMode current);

        /** Labeled attack bonuses (roll dice here). Shown in the hit notification. */
        default List<DiceBonus> getAttackBonusList(AbilityScore score) { return List.of(); }
        /** Labeled save bonuses (roll dice here). Shown in dice-screen bonuses. */
        default List<DiceBonus> getSaveBonusList(AbilityScore score) { return List.of(); }

        /** Total attack bonus — derived from getAttackBonusList. */
        default int attackBonus(AbilityScore score) {
            return getAttackBonusList(score).stream().mapToInt(DiceBonus::value).sum();
        }
        /** Total save bonus — derived from getSaveBonusList. */
        default int saveBonus(AbilityScore score) {
            return getSaveBonusList(score).stream().mapToInt(DiceBonus::value).sum();
        }
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

    /** Returns labeled attack bonuses from all active modifiers (dice are rolled here). */
    public static List<DiceBonus> resolveAttackBonusList(ServerPlayer player, AbilityScore score) {
        Map<Identifier, RollModifier> map = MODIFIERS.get(player.getUUID());
        if (map == null || map.isEmpty()) return List.of();
        List<DiceBonus> result = new ArrayList<>();
        for (RollModifier mod : map.values()) result.addAll(mod.getAttackBonusList(score));
        return result;
    }

    /** Total attack bonus — sums resolveAttackBonusList. */
    public static int resolveAttackBonus(ServerPlayer player, AbilityScore score) {
        return resolveAttackBonusList(player, score).stream().mapToInt(DiceBonus::value).sum();
    }

    /** Returns labeled save bonuses from all active modifiers (dice are rolled here). */
    public static List<DiceBonus> resolveSaveBonusList(ServerPlayer player, AbilityScore score) {
        Map<Identifier, RollModifier> map = MODIFIERS.get(player.getUUID());
        if (map == null || map.isEmpty()) return List.of();
        List<DiceBonus> result = new ArrayList<>();
        for (RollModifier mod : map.values()) result.addAll(mod.getSaveBonusList(score));
        return result;
    }

    /** Total save bonus — sums resolveSaveBonusList. */
    public static int resolveSaveBonus(ServerPlayer player, AbilityScore score) {
        return resolveSaveBonusList(player, score).stream().mapToInt(DiceBonus::value).sum();
    }

    /** Call on player disconnect to clean up. */
    public static void clearPlayer(UUID playerId) {
        MODIFIERS.remove(playerId);
    }

    private RollModifierRegistry() {}
}