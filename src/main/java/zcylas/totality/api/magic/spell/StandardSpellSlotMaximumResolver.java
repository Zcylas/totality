package zcylas.totality.api.magic.spell;

import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.rpg.resources.PlayerResourceDefinition;
import zcylas.totality.api.rpg.resources.ResourceMaximum;
import zcylas.totality.api.rpg.resources.ResourceMaximumResolver;
import zcylas.totality.api.rpg.resources.ResourceResolutionContext;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolves {@code totality:spell_slots}'s partitioned maximum (Phase 6 migration, 2026-09-16) by
 * delegating to {@link SpellSlotRecalculator#computeMaxSlots} — the same combined-multiclass-level
 * formula the grant provider ({@code StandardSpellSlotResources}) uses to decide entitlement, kept in
 * exactly one place. Partitions 1–9 only; there is no partition 10 (see {@link SpellSlotTable}'s
 * class Javadoc for the Phase 6 canon this reflects).
 */
public final class StandardSpellSlotMaximumResolver implements ResourceMaximumResolver {

    public static final StandardSpellSlotMaximumResolver INSTANCE = new StandardSpellSlotMaximumResolver();

    private StandardSpellSlotMaximumResolver() {}

    @Override
    public ResourceMaximum resolve(ServerPlayer player, PlayerResourceDefinition definition, ResourceResolutionContext context) {
        int[] maxSlots = SpellSlotRecalculator.computeMaxSlots(player);
        Map<Integer, Long> byPartition = new LinkedHashMap<>();
        for (int level = 1; level <= SpellSlotTable.STANDARD_SLOT_LEVELS; level++) {
            byPartition.put(level, (long) maxSlots[level - 1]);
        }
        return new ResourceMaximum.Partitioned(byPartition, byPartition);
    }
}
