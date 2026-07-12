package zcylas.totality.api.magic.spell;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.rpg.classes.ClassComponents;
import zcylas.totality.api.rpg.classes.PlayerClassComponent;

/**
 * Recomputes {@link SpellSlotComponent}'s max slots from the player's current class levels.
 * Call whenever class levels change (class selection, class level-up) and once on join.
 *
 * Only FULL/HALF/THIRD caster progressions pool into the shared bank via
 * {@link SpellSlotTable#combinedCasterLevel} — WARLOCK is deliberately excluded (see
 * {@link CasterProgression}) and isn't recovered here; its separate Pact Magic pool is
 * a deferred follow-up.
 */
public final class SpellSlotRecalculator {

    public static void recalculate(ServerPlayer player) {
        PlayerClassComponent classComp = ClassComponents.get(player);
        if (classComp == null) return;

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

        int combinedLevel = SpellSlotTable.combinedCasterLevel(fullLevels, halfLevels, thirdLevels);
        int[] maxSlots = combinedLevel > 0
                ? SpellSlotTable.forFullCaster(combinedLevel)
                : new int[SpellSlotComponent.MAX_SPELL_LEVEL];

        SpellSlotComponents.get(player).recalculate(maxSlots);
    }

    private SpellSlotRecalculator() {}
}
