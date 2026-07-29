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

        /**
         * Whether this modifier's underlying source is still actually active right now.
         * Consulted on every {@code resolve*} call, so a modifier that has outlived its intended
         * lifetime — e.g. because the removal callback for the effect that registered it was
         * missed, or two independent systems both think they own removing it — is excluded from
         * the result and lazily evicted from the registry, instead of continuing to apply
         * indefinitely until an unrelated full reset (player disconnect).
         *
         * <p>A modifier backed by a Minecraft status effect should implement this by checking the
         * entity's actual current effect state (e.g. {@code player.hasEffect(ModEffects.BLESS)}),
         * which Minecraft itself always keeps correct the instant the effect's duration ends,
         * independent of any callback/event wiring. Defaults to {@code true} for a modifier with no
         * independent liveness signal of its own (e.g. one removed solely by an explicit one-shot
         * action with no duration to outlive).
         */
        default boolean isActive() { return true; }
    }

    private static final Map<UUID, Map<Identifier, RollModifier>> MODIFIERS = new HashMap<>();

    public static void register(ServerPlayer player, Identifier sourceId, RollModifier modifier) {
        register(player.getUUID(), sourceId, modifier);
    }

    public static void remove(ServerPlayer player, Identifier sourceId) {
        remove(player.getUUID(), sourceId);
    }

    public static RollType resolveSave(ServerPlayer player, AbilityScore score, RollType base) {
        Collection<RollModifier> mods = activeModifiers(player.getUUID());
        if (mods.isEmpty()) return base;
        RollType result = base;
        for (RollModifier mod : mods) result = mod.modifySave(score, result);
        return result;
    }

    public static AbilityCheckResolver.RollMode resolveCheck(ServerPlayer player,
                                                             AbilityScore score,
                                                             AbilityCheckResolver.RollMode base) {
        Collection<RollModifier> mods = activeModifiers(player.getUUID());
        if (mods.isEmpty()) return base;
        AbilityCheckResolver.RollMode result = base;
        for (RollModifier mod : mods) result = mod.modifyCheck(score, result);
        return result;
    }

    /** Returns labeled attack bonuses from all currently active modifiers (dice are rolled here). */
    public static List<DiceBonus> resolveAttackBonusList(ServerPlayer player, AbilityScore score) {
        Collection<RollModifier> mods = activeModifiers(player.getUUID());
        if (mods.isEmpty()) return List.of();
        List<DiceBonus> result = new ArrayList<>();
        for (RollModifier mod : mods) result.addAll(mod.getAttackBonusList(score));
        return result;
    }

    /** Total attack bonus — sums resolveAttackBonusList. */
    public static int resolveAttackBonus(ServerPlayer player, AbilityScore score) {
        return resolveAttackBonusList(player, score).stream().mapToInt(DiceBonus::value).sum();
    }

    /** Returns labeled save bonuses from all currently active modifiers (dice are rolled here). */
    public static List<DiceBonus> resolveSaveBonusList(ServerPlayer player, AbilityScore score) {
        Collection<RollModifier> mods = activeModifiers(player.getUUID());
        if (mods.isEmpty()) return List.of();
        List<DiceBonus> result = new ArrayList<>();
        for (RollModifier mod : mods) result.addAll(mod.getSaveBonusList(score));
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

    // ── UUID-keyed implementation ────────────────────────────────────────────
    // The ServerPlayer-taking public API above only ever needs the player's UUID, so the actual
    // storage/resolution logic is UUID-keyed — this also lets it be exercised directly in tests
    // without constructing a live ServerPlayer (which requires a bootstrapped Minecraft registry).

    private static void register(UUID playerId, Identifier sourceId, RollModifier modifier) {
        MODIFIERS.computeIfAbsent(playerId, k -> new LinkedHashMap<>()).put(sourceId, modifier);
    }

    private static void remove(UUID playerId, Identifier sourceId) {
        Map<Identifier, RollModifier> map = MODIFIERS.get(playerId);
        if (map != null) map.remove(sourceId);
    }

    /**
     * Returns every currently-registered modifier for {@code playerId} whose {@link
     * RollModifier#isActive()} is still {@code true}, lazily evicting (removing from the
     * registry) any that report {@code false} — so a stale modifier is both excluded from this
     * result and cleaned up, rather than being re-checked and re-excluded on every future call
     * forever. The only source of truth for "is this modifier still active" is the modifier
     * itself; this method never guesses.
     */
    private static Collection<RollModifier> activeModifiers(UUID playerId) {
        Map<Identifier, RollModifier> map = MODIFIERS.get(playerId);
        if (map == null || map.isEmpty()) return List.of();
        map.entrySet().removeIf(entry -> !entry.getValue().isActive());
        if (map.isEmpty()) {
            MODIFIERS.remove(playerId);
            return List.of();
        }
        return map.values();
    }

    private RollModifierRegistry() {}

    // ── Test-only hooks ──────────────────────────────────────────────────────
    // UUID-keyed, mirroring the ServerPlayer-taking public API exactly — lets the eviction/
    // liveness behavior be exercised with a real registered RollModifier under plain JUnit,
    // without needing a live ServerPlayer.

    static void registerForTest(UUID playerId, Identifier sourceId, RollModifier modifier) {
        register(playerId, sourceId, modifier);
    }

    static void removeForTest(UUID playerId, Identifier sourceId) {
        remove(playerId, sourceId);
    }

    static List<DiceBonus> resolveAttackBonusListForTest(UUID playerId, AbilityScore score) {
        Collection<RollModifier> mods = activeModifiers(playerId);
        if (mods.isEmpty()) return List.of();
        List<DiceBonus> result = new ArrayList<>();
        for (RollModifier mod : mods) result.addAll(mod.getAttackBonusList(score));
        return result;
    }

    static int registeredCountForTest(UUID playerId) {
        Map<Identifier, RollModifier> map = MODIFIERS.get(playerId);
        return map == null ? 0 : map.size();
    }

    static void clearForTest() {
        MODIFIERS.clear();
    }
}
