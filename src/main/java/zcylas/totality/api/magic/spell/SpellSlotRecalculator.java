package zcylas.totality.api.magic.spell;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.rpg.classes.ClassComponents;
import zcylas.totality.api.rpg.classes.PlayerClassComponent;

/**
 * The single authoritative "how many Standard Spell Slots should this player have" calculation —
 * used by {@code StandardSpellSlotMaximumResolver} (the live Generic Resource maximum) and {@code
 * StandardSpellSlotResources} (the grant-entitlement check: does this player qualify for the pool at
 * all). Kept in exactly one place so no second, drifting copy of "which classes count and how" can
 * exist in the resolver, the grant provider, or anywhere else.
 *
 * <p>Only FULL/HALF/THIRD caster progressions pool into the shared bank via
 * {@link SpellSlotTable#combinedCasterLevel} — WARLOCK is deliberately excluded (see
 * {@link CasterProgression}); its separate Pact Magic pool is Phase 7 scope, not modeled here.
 *
 * <p><b>Phase 6 migration (2026-09-16):</b> this class no longer writes to {@link SpellSlotComponent}
 * (retired as a mutation authority — see its class Javadoc) or performs any recalculation of its own
 * beyond this one pure formula. The class-change lifecycle no longer calls a separate "recalculate"
 * step here either: {@code totality:spell_slots}'s maximum is now resolved live, on every query,
 * through {@code StandardSpellSlotMaximumResolver} delegating back to {@link
 * #computeCombinedCasterLevel}, so there is nothing left to eagerly recompute and store — the same
 * reason no explicit "recalculate Rage's maximum" step exists either (see {@code
 * BarbarianRageAbility#getMaxRage}, resolved live the same way).
 */
public final class SpellSlotRecalculator {

    /** The combined multiclass caster level this player currently qualifies for — 0 if none of their
     *  classes contribute to the shared Standard Spell Slot pool. */
    public static int computeCombinedCasterLevel(ServerPlayer player) {
        PlayerClassComponent classComp = ClassComponents.get(player);
        if (classComp == null) return 0;

        int fullLevels = 0, halfLevels = 0, thirdLevels = 0;
        for (var entry : classComp.getAllClassLevels().entrySet()) {
            Identifier classId = entry.getKey();
            int level = entry.getValue();
            CasterProgression progression = SpellcastingProgressionRegistry.get(classId);
            if (progression == null) continue;
            switch (progression) {
                case FULL -> fullLevels += level;
                case HALF -> halfLevels += level;
                case THIRD -> thirdLevels += level;
                case WARLOCK -> { /* separate Pact Magic pool, not tracked here */ }
            }
        }

        return SpellSlotTable.combinedCasterLevel(fullLevels, halfLevels, thirdLevels);
    }

    /** The resolved 1st–9th maximum slot array for this player's current combined caster level —
     *  all-zero if {@link #computeCombinedCasterLevel} is 0. */
    public static int[] computeMaxSlots(ServerPlayer player) {
        int combinedLevel = computeCombinedCasterLevel(player);
        return combinedLevel > 0
                ? SpellSlotTable.forFullCaster(combinedLevel)
                : new int[SpellSlotTable.STANDARD_SLOT_LEVELS];
    }

    private SpellSlotRecalculator() {}
}
