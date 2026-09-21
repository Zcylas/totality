package zcylas.totality.api.rpg.classes;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.rpg.resources.PlayerResourceDefinition;
import zcylas.totality.api.rpg.resources.ResourceMaximum;
import zcylas.totality.api.rpg.resources.ResourceMaximumResolver;
import zcylas.totality.api.rpg.resources.ResourceResolutionContext;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolves {@code totality:health_recovery_dice}'s partitioned maximum (Phase 7A, 2026-09-16;
 * renamed from the working name {@code totality:hit_dice} before commit — see the implementation
 * report's "why the original name was corrected" section; this Generic Resource is NOT the future
 * Hit Die API, a separate, unimplemented Character Creation/Progression system) — canonical §25.10:
 * "Every allocated class level contributes one maximum [Health Recovery Die] to the partition
 * matching that class's HP Hit Die size." Partitions are keyed by the actual {@link
 * zcylas.totality.api.dice.Dice#getSides()} value of every registered class's {@link
 * ClassData#hpDie()} — the same class-authored HP Hit Die metadata the future Hit Die API will
 * also eventually read, shared by design, not duplicated — not a hardcoded d6/d8/d10/d12 set, so a
 * future class with a different Hit Die size is picked up automatically with no change here.
 *
 * <p>Deliberately keys the returned map off {@link ClassRegistry#getAll()} — every registered
 * class, not merely the ones this particular player owns — so the partition SET is fixed and
 * identical across every call for every player, exactly mirroring {@code
 * StandardSpellSlotMaximumResolver}'s own fixed 1-9 partition set. This is what keeps {@code
 * ClassChangeReconciler#reconcilePartitioned}'s per-partition "previous maximum" lookup from ever
 * needing its {@code previousMax == null} fallback for a newly-relevant partition: a die size a
 * player has never owned before still appears in the "before" snapshot too, at maximum 0, so
 * gaining a first level in that die size resolves as an ordinary 0→N maximum change like any
 * other, never a genuinely absent partition key.
 */
public final class HealthRecoveryDiceMaximumResolver implements ResourceMaximumResolver {

    public static final HealthRecoveryDiceMaximumResolver INSTANCE = new HealthRecoveryDiceMaximumResolver();

    private HealthRecoveryDiceMaximumResolver() {}

    @Override
    public ResourceMaximum resolve(ServerPlayer player, PlayerResourceDefinition definition, ResourceResolutionContext context) {
        Map<Integer, Long> byPartition = new LinkedHashMap<>();
        for (ClassData classData : ClassRegistry.getAll()) {
            byPartition.putIfAbsent(classData.hpDie().getSides(), 0L);
        }
        for (Map.Entry<Identifier, Integer> entry : ClassComponents.get(player).getAllClassLevels().entrySet()) {
            ClassRegistry.get(entry.getKey()).ifPresent(classData -> {
                int dieSize = classData.hpDie().getSides();
                byPartition.merge(dieSize, (long) entry.getValue(), Long::sum);
            });
        }
        return new ResourceMaximum.Partitioned(byPartition, byPartition);
    }
}
