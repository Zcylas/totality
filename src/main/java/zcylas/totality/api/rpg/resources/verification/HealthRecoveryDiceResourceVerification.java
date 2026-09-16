package zcylas.totality.api.rpg.resources.verification;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.Totality;
import zcylas.totality.api.core.util.ServerScheduler;
import zcylas.totality.api.core.util.VerificationReporter;
import zcylas.totality.api.rpg.classes.ClassChangeReconciler;
import zcylas.totality.api.rpg.classes.ClassComponents;
import zcylas.totality.api.rpg.classes.TotalityClasses;
import zcylas.totality.api.rpg.resources.PartitionSelectionPolicy;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.PlayerResourceRegistry;
import zcylas.totality.api.rpg.resources.PlayerResourceService;
import zcylas.totality.api.rpg.resources.ResourceCause;
import zcylas.totality.api.rpg.resources.ResourceContext;
import zcylas.totality.api.rpg.resources.ResourceCost;
import zcylas.totality.api.rpg.resources.ResourceQueryResult;
import zcylas.totality.api.rpg.resources.ResourceStateComponents;
import zcylas.totality.api.rpg.resources.integration.HealthRecoveryDiceResources;
import zcylas.totality.api.rpg.resources.sync.ResourcePartitionedWireSnapshot;
import zcylas.totality.api.rpg.rest.RestEventBus;
import zcylas.totality.api.rpg.rest.RestType;
import zcylas.totality.server.TotalityFakePlayer;

/**
 * Dev-environment-gated self-test for the Phase 7A Health Recovery Dice resource (2026-09-16;
 * renamed from the working name {@code totality:hit_dice}/{@code HitDiceResourceVerification}
 * before commit — this Generic Resource is a spendable/restorable Health recovery pool, NOT the
 * future Hit Die API, a separate, unimplemented Character Creation/Progression system — see the
 * implementation report) — exercises the REAL production {@code PlayerResourceRegistry.INSTANCE}/
 * {@code totality:health_recovery_dice} registration, {@link HealthRecoveryDiceResources}' grant/
 * reconciliation/Long Rest, {@code HealthRecoveryDiceMaximumResolver}, and the real class levels of
 * the four currently-implemented classes (Wizard d6, Monk d8, Warlock d8, Barbarian d12 — confirmed
 * by direct read of each class's {@code ClassData}), including a genuine same-die-size multiclass
 * aggregation case (Monk + Warlock, both d8). Mirrors {@code
 * StandardSpellSlotMigrationVerification}'s established pattern exactly (real {@code ServerPlayer}
 * via {@code TotalityFakePlayer}, real {@code ClassChangeReconciler}/{@code PlayerResourceService}
 * calls).
 *
 * <p>Deliberately does NOT exercise any healing/Short-Rest-spend flow — canonical §25.10 assigns
 * "Player choice of an available die partition," "Server-side die roll," "CON modifier and other
 * healing modifiers," and "Applying healing to authoritative Health" to a Rest/Health integration
 * layer this task does not build (the exact healing formula is unresolved in current Totality
 * canon/code — see the implementation report). What IS in this task's scope — the generic spend
 * primitive itself (one selected partition decreases by exactly one unit, wrong/empty partitions
 * cannot be spent) — is proven directly through {@link PlayerResourceService#trySpend}, the same
 * primitive a future Rest/Health integration would call.
 *
 * <p>Registration is gated on {@link VerificationReporter#isDevEnvironment()} — a complete no-op in
 * a production build.
 */
public final class HealthRecoveryDiceResourceVerification {

    private static final int SUITE_DELAY_TICKS = 5;

    private HealthRecoveryDiceResourceVerification() {}

    public static void register() {
        if (!VerificationReporter.isDevEnvironment()) return;
        ServerLifecycleEvents.SERVER_STARTED.register(HealthRecoveryDiceResourceVerification::scheduleDelayed);
    }

    private static void scheduleDelayed(MinecraftServer server) {
        ServerScheduler.getInstance().queue(HealthRecoveryDiceResourceVerification::runSelfTest, SUITE_DELAY_TICKS);
    }

    private static void runSelfTest(MinecraftServer server) {
        VerificationReporter r = new VerificationReporter(Totality.LOGGER, "HealthRecoveryDiceResourceVerification");
        ServerLevel level = server.overworld();

        safe(r, "the corrected production resource id is exactly totality:health_recovery_dice, "
                + "and the obsolete working name totality:hit_dice is not registered at all", () -> {
            boolean registered = PlayerResourceRegistry.INSTANCE.isRegistered(PlayerResourceIds.HEALTH_RECOVERY_DICE);
            boolean oldIdAbsent = PlayerResourceRegistry.INSTANCE
                    .get(net.minecraft.resources.Identifier.fromNamespaceAndPath("totality", "hit_dice"))
                    .isEmpty();
            return result(registered && oldIdAbsent, "registered=" + registered + ", oldIdAbsent=" + oldIdAbsent);
        });

        ServerPlayer noClass = TotalityFakePlayer.create(level, "[HealthRecoveryDiceResourceVerification-noclass]");
        try {
            safe(r, "a player with no class owns no Health Recovery Dice state, even after an explicit "
                    + "reconciliation pass", () -> {
                HealthRecoveryDiceResources.reconcile(noClass);
                boolean hasState = ResourceStateComponents.get(noClass).hasState(PlayerResourceIds.HEALTH_RECOVERY_DICE);
                return result(!hasState, "hasState=" + hasState);
            });
        } finally {
            noClass.discard();
        }

        ServerPlayer single = TotalityFakePlayer.create(level, "[HealthRecoveryDiceResourceVerification-single]");
        try {
            ClassComponents.get(single).selectClass(TotalityClasses.WIZARD_ID, 5);
            ClassChangeReconciler.reconcile(single); // fresh grant — AtMaximum

            safe(r, "1. first qualifying class (Wizard, d6) grants Health Recovery Dice at the correct "
                    + "initial state: d6 5/5, d8 0/0, d12 0/0 — no ordinary-tier confusion with any other "
                    + "resource's partitions", () -> {
                ResourceQueryResult query = PlayerResourceService.INSTANCE.query(single, PlayerResourceIds.HEALTH_RECOVERY_DICE);
                if (!(query instanceof ResourceQueryResult.PartitionedSuccess success)) return result(false, "query=" + query);
                var snapshot = success.snapshot();
                boolean pass = snapshot.partition(6).map(p -> p.currentUnits() == 5 && p.maximumUnits() == 5).orElse(false)
                        && snapshot.partition(8).map(p -> p.currentUnits() == 0 && p.maximumUnits() == 0).orElse(false)
                        && snapshot.partition(12).map(p -> p.currentUnits() == 0 && p.maximumUnits() == 0).orElse(false);
                return result(pass, "snapshot=" + snapshot);
            });

            safe(r, "re-reconciling an already-granted single-class player does not create duplicate or "
                    + "refilled state (canonical §16.6)", () -> {
                PlayerResourceService.INSTANCE.trySpend(single,
                        new ResourceCost.Partitioned(PlayerResourceIds.HEALTH_RECOVERY_DICE, 6, 1, PartitionSelectionPolicy.EXACT_TIER),
                        ResourceContext.of(ResourceCause.of(ResourceContext.CauseTypes.SPELL_COST))); // 5 -> 4
                HealthRecoveryDiceResources.reconcile(single); // no before captured — must not refill
                ResourceQueryResult query = PlayerResourceService.INSTANCE.query(single, PlayerResourceIds.HEALTH_RECOVERY_DICE);
                boolean pass = query instanceof ResourceQueryResult.PartitionedSuccess success
                        && success.snapshot().partition(6).orElseThrow().currentUnits() == 4;
                return result(pass, "query=" + query);
            });

            safe(r, "4. PRESERVE_DEFICIT level-up: the unspent d6 pool grows by exactly the newly gained "
                    + "capacity (max 5 -> 6, current 4 -> 5) while the already-spent die remains spent", () -> {
                var before = ClassChangeReconciler.captureResolvedMaximums(single);
                ClassComponents.get(single).addClassLevel(TotalityClasses.WIZARD_ID); // Wizard 5 -> 6
                ClassChangeReconciler.reconcile(single, before);
                ResourceQueryResult query = PlayerResourceService.INSTANCE.query(single, PlayerResourceIds.HEALTH_RECOVERY_DICE);
                boolean pass = query instanceof ResourceQueryResult.PartitionedSuccess success
                        && success.snapshot().partition(6).orElseThrow().currentUnits() == 5
                        && success.snapshot().partition(6).orElseThrow().maximumUnits() == 6;
                return result(pass, "query=" + query);
            });

            safe(r, "6. spending the selected d6 partition decreases it by exactly 1 and leaves the "
                    + "unrelated d8/d12 partitions completely untouched", () -> {
                var spendResult = PlayerResourceService.INSTANCE.trySpend(single,
                        new ResourceCost.Partitioned(PlayerResourceIds.HEALTH_RECOVERY_DICE, 6, 1, PartitionSelectionPolicy.EXACT_TIER),
                        ResourceContext.of(ResourceCause.of(ResourceContext.CauseTypes.SPELL_COST))); // 5 -> 4
                ResourceQueryResult query = PlayerResourceService.INSTANCE.query(single, PlayerResourceIds.HEALTH_RECOVERY_DICE);
                boolean pass = spendResult.isSuccess() && query instanceof ResourceQueryResult.PartitionedSuccess success
                        && success.snapshot().partition(6).orElseThrow().currentUnits() == 4
                        && success.snapshot().partition(8).orElseThrow().currentUnits() == 0
                        && success.snapshot().partition(12).orElseThrow().currentUnits() == 0;
                return result(pass, "spendResult=" + spendResult + ", query=" + query);
            });

            safe(r, "6. an empty partition (d12, 0/0 for this Wizard) cannot be spent, and no cross-"
                    + "partition fallback silently draws from d6 instead", () -> {
                var spendResult = PlayerResourceService.INSTANCE.trySpend(single,
                        new ResourceCost.Partitioned(PlayerResourceIds.HEALTH_RECOVERY_DICE, 12, 1, PartitionSelectionPolicy.EXACT_TIER),
                        ResourceContext.of(ResourceCause.of(ResourceContext.CauseTypes.SPELL_COST)));
                ResourceQueryResult query = PlayerResourceService.INSTANCE.query(single, PlayerResourceIds.HEALTH_RECOVERY_DICE);
                boolean d6Unchanged = query instanceof ResourceQueryResult.PartitionedSuccess success
                        && success.snapshot().partition(6).orElseThrow().currentUnits() == 4;
                return result(!spendResult.isSuccess() && d6Unchanged, "spendResult=" + spendResult + ", query=" + query);
            });

            safe(r, "attempting to overspend a partition beyond its current amount is rejected entirely, "
                    + "not partially fulfilled from another partition", () -> {
                var spendResult = PlayerResourceService.INSTANCE.trySpend(single,
                        new ResourceCost.Partitioned(PlayerResourceIds.HEALTH_RECOVERY_DICE, 6, 999, PartitionSelectionPolicy.EXACT_TIER),
                        ResourceContext.of(ResourceCause.of(ResourceContext.CauseTypes.SPELL_COST)));
                ResourceQueryResult query = PlayerResourceService.INSTANCE.query(single, PlayerResourceIds.HEALTH_RECOVERY_DICE);
                boolean d6Unchanged = query instanceof ResourceQueryResult.PartitionedSuccess success
                        && success.snapshot().partition(6).orElseThrow().currentUnits() == 4;
                return result(!spendResult.isSuccess() && d6Unchanged, "spendResult=" + spendResult + ", query=" + query);
            });

            safe(r, "8. Short Rest does not restore, spend, or otherwise alter Health Recovery Dice — no "
                    + "listener is registered for it (they are spent during a Short Rest by player choice, "
                    + "not automatically changed by the rest event itself)", () -> {
                RestEventBus.fire(single, RestType.SHORT);
                ResourceQueryResult query = PlayerResourceService.INSTANCE.query(single, PlayerResourceIds.HEALTH_RECOVERY_DICE);
                boolean pass = query instanceof ResourceQueryResult.PartitionedSuccess success
                        && success.snapshot().partition(6).orElseThrow().currentUnits() == 4;
                return result(pass, "query=" + query);
            });

            safe(r, "9. Long Rest fully restores every Health Recovery Dice partition to its resolved "
                    + "maximum (d6 4/6 -> 6/6), and a second consecutive Long Rest does not overfill or "
                    + "error", () -> {
                HealthRecoveryDiceResources.onLongRest(single);
                ResourceQueryResult first = PlayerResourceService.INSTANCE.query(single, PlayerResourceIds.HEALTH_RECOVERY_DICE);
                boolean firstPass = first instanceof ResourceQueryResult.PartitionedSuccess success
                        && success.snapshot().partition(6).orElseThrow().currentUnits() == 6
                        && success.snapshot().partition(6).orElseThrow().maximumUnits() == 6;

                HealthRecoveryDiceResources.onLongRest(single); // already full — must be a no-op, not an error/overfill
                ResourceQueryResult second = PlayerResourceService.INSTANCE.query(single, PlayerResourceIds.HEALTH_RECOVERY_DICE);
                boolean secondPass = second instanceof ResourceQueryResult.PartitionedSuccess success
                        && success.snapshot().partition(6).orElseThrow().currentUnits() == 6;
                return result(firstPass && secondPass, "first=" + first + ", second=" + second);
            });
        } finally {
            single.discard();
        }

        ServerPlayer multiclass = TotalityFakePlayer.create(level, "[HealthRecoveryDiceResourceVerification-multiclass]");
        try {
            ClassComponents.get(multiclass).selectClass(TotalityClasses.MONK_ID, 3); // d8
            ClassChangeReconciler.reconcile(multiclass); // fresh grant — AtMaximum

            safe(r, "2. + 3. maximum resolution, single class: Monk 3 (d8) grants exactly d8 3/3, d6 0/0, "
                    + "d12 0/0", () -> {
                ResourceQueryResult query = PlayerResourceService.INSTANCE.query(multiclass, PlayerResourceIds.HEALTH_RECOVERY_DICE);
                boolean pass = query instanceof ResourceQueryResult.PartitionedSuccess success
                        && success.snapshot().partition(8).orElseThrow().currentUnits() == 3
                        && success.snapshot().partition(8).orElseThrow().maximumUnits() == 3
                        && success.snapshot().partition(6).orElseThrow().maximumUnits() == 0
                        && success.snapshot().partition(12).orElseThrow().maximumUnits() == 0;
                return result(pass, "query=" + query);
            });

            safe(r, "10. same-die-size multiclass aggregation: adding a level in Warlock (also d8) "
                    + "increases the SAME d8 partition's maximum (3 -> 4), not a second independent pool "
                    + "— and the newly gained capacity is immediately available (current 3 -> 4)", () -> {
                var before = ClassChangeReconciler.captureResolvedMaximums(multiclass);
                ClassComponents.get(multiclass).addClassLevel(TotalityClasses.WARLOCK_ID); // Monk 3 + Warlock 1, both d8
                ClassChangeReconciler.reconcile(multiclass, before);
                ResourceQueryResult query = PlayerResourceService.INSTANCE.query(multiclass, PlayerResourceIds.HEALTH_RECOVERY_DICE);
                boolean pass = query instanceof ResourceQueryResult.PartitionedSuccess success
                        && success.snapshot().partition(8).orElseThrow().currentUnits() == 4
                        && success.snapshot().partition(8).orElseThrow().maximumUnits() == 4;
                return result(pass, "query=" + query);
            });

            safe(r, "11. a third class with a different die size (Barbarian, d12) creates/increases its "
                    + "own independent partition immediately, without disturbing the existing aggregated "
                    + "d8 partition at all — proving losing/gaining one class's contribution never erases "
                    + "another die size's dice", () -> {
                var before = ClassChangeReconciler.captureResolvedMaximums(multiclass);
                ClassComponents.get(multiclass).addClassLevel(TotalityClasses.BARBARIAN_ID); // new d12 partition
                ClassChangeReconciler.reconcile(multiclass, before);
                ResourceQueryResult query = PlayerResourceService.INSTANCE.query(multiclass, PlayerResourceIds.HEALTH_RECOVERY_DICE);
                boolean pass = query instanceof ResourceQueryResult.PartitionedSuccess success
                        && success.snapshot().partition(12).orElseThrow().currentUnits() == 1
                        && success.snapshot().partition(12).orElseThrow().maximumUnits() == 1
                        && success.snapshot().partition(8).orElseThrow().currentUnits() == 4 // unchanged
                        && success.snapshot().partition(8).orElseThrow().maximumUnits() == 4;
                return result(pass, "query=" + query);
            });

            safe(r, "12. defensive class-level decrease: forcing Monk's level down directly (mirroring "
                    + "the Finding-1 JOIN-normalization shape) with a genuine captured-before clamps the "
                    + "d8 partition to its new lower maximum, never goes negative, leaves the unrelated "
                    + "d12 partition untouched, and remains wire-safe", () -> {
                var before = ClassChangeReconciler.captureResolvedMaximums(multiclass);
                ClassComponents.get(multiclass).setClassLevel(TotalityClasses.MONK_ID, 0); // Monk 3 -> 0, no reconciliation of its own
                ClassChangeReconciler.reconcile(multiclass, before);
                ResourceQueryResult query = PlayerResourceService.INSTANCE.query(multiclass, PlayerResourceIds.HEALTH_RECOVERY_DICE);
                if (!(query instanceof ResourceQueryResult.PartitionedSuccess success)) return result(false, "query=" + query);
                var snapshot = success.snapshot();
                // Warlock's own 1 level of d8 remains — Monk's 3 are gone: d8 max 4 -> 1.
                boolean d8Correct = snapshot.partition(8).orElseThrow().maximumUnits() == 1
                        && snapshot.partition(8).orElseThrow().currentUnits() <= 1
                        && snapshot.partition(8).orElseThrow().currentUnits() >= 0;
                boolean d12Unchanged = snapshot.partition(12).orElseThrow().currentUnits() == 1
                        && snapshot.partition(12).orElseThrow().maximumUnits() == 1;
                boolean wireSafe;
                try {
                    ResourcePartitionedWireSnapshot.from(snapshot);
                    wireSafe = true;
                } catch (IllegalArgumentException wireInvariantViolation) {
                    wireSafe = false;
                }
                boolean pass = d8Correct && d12Unchanged && wireSafe;
                return result(pass, "snapshot=" + snapshot + ", wireSafe=" + wireSafe);
            });
        } finally {
            multiclass.discard();
        }

        r.summarize();
    }

    @FunctionalInterface
    private interface CheckBody {
        CheckResult run() throws Exception;
    }

    private record CheckResult(boolean pass, String detail) {}

    private static CheckResult result(boolean pass, String detail) {
        return new CheckResult(pass, detail);
    }

    private static void safe(VerificationReporter r, String label, CheckBody body) {
        try {
            CheckResult outcome = body.run();
            r.check(label, outcome.pass(), outcome.detail());
        } catch (Exception e) {
            r.check(label, false, "threw " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
