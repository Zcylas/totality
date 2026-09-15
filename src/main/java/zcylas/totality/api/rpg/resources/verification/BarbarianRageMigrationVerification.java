package zcylas.totality.api.rpg.resources.verification;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.Totality;
import zcylas.totality.api.ability.impl.barbarian.BarbarianRageAbility;
import zcylas.totality.api.core.util.ServerScheduler;
import zcylas.totality.api.core.util.VerificationReporter;
import zcylas.totality.api.rpg.classes.ChargeComponents;
import zcylas.totality.api.rpg.classes.ClassComponents;
import zcylas.totality.api.rpg.classes.TotalityClasses;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.PlayerResourceService;
import zcylas.totality.api.rpg.resources.ResourceContext;
import zcylas.totality.api.rpg.resources.ResourceCost;
import zcylas.totality.api.rpg.resources.ResourceCause;
import zcylas.totality.api.rpg.resources.ResourceQueryResult;
import zcylas.totality.api.rpg.resources.PlayerResourceStateComponent;
import zcylas.totality.api.rpg.resources.ResourceStateComponents;
import zcylas.totality.api.rpg.rest.RestType;
import zcylas.totality.networking.resource.BaselineResourceLifecycleEvents;
import zcylas.totality.server.TotalityFakePlayer;
import zcylas.totality.api.ability.AbilityComponents;
import zcylas.totality.api.core.component.ComponentProvider;

/**
 * Dev-environment-gated self-test for the Phase 5 Rage migration (2026-09-15), matching {@link
 * BaselineResourceMigrationVerification}'s established pattern — exercises the REAL production
 * {@code PlayerResourceRegistry.INSTANCE}/{@code totality:rage} registration, {@link
 * zcylas.totality.api.rpg.resources.integration.BarbarianRageResources}'s grant/reconciliation, the
 * real {@link BarbarianRageAbility#registerChargePool}/{@link BarbarianRageAbility#onActivate}
 * spend path, {@link zcylas.totality.api.ability.impl.barbarian.RageMaximumResolver}, the Rest
 * integration, and the legacy NBT migration-import step.
 *
 * <p>Registration is gated on {@link VerificationReporter#isDevEnvironment()} — a complete no-op in
 * a production build.
 */
public final class BarbarianRageMigrationVerification {

    private static final int SUITE_DELAY_TICKS = 5;

    private BarbarianRageMigrationVerification() {}

    public static void register() {
        if (!VerificationReporter.isDevEnvironment()) return;
        ServerLifecycleEvents.SERVER_STARTED.register(BarbarianRageMigrationVerification::scheduleDelayed);
    }

    private static void scheduleDelayed(MinecraftServer server) {
        ServerScheduler.getInstance().queue(BarbarianRageMigrationVerification::runSelfTest, SUITE_DELAY_TICKS);
    }

    private static void runSelfTest(MinecraftServer server) {
        VerificationReporter r = new VerificationReporter(Totality.LOGGER, "BarbarianRageMigrationVerification");
        ServerLevel level = server.overworld();

        ServerPlayer freshBarbarian = TotalityFakePlayer.create(level, "[BarbarianRageMigrationVerification-fresh]");
        try {
            ClassComponents.get(freshBarbarian).selectClass(TotalityClasses.BARBARIAN_ID, 1);

            safe(r, "registerChargePool (class selection) instantiates totality:rage at the resolved "
                    + "level-1 maximum through the real grant reconciliation path", () -> {
                BarbarianRageAbility.registerChargePool(freshBarbarian);
                ResourceQueryResult query = PlayerResourceService.INSTANCE.query(freshBarbarian, PlayerResourceIds.RAGE);
                boolean pass = query instanceof ResourceQueryResult.Success success
                        && success.snapshot().currentUnits() == 2 && success.snapshot().maximumUnits() == 2;
                return result(pass, "query=" + query);
            });

            safe(r, "totality:rage reports a spendable charge at full, then trySpend consumes exactly "
                    + "one charge — the same query canActivate now consults", () -> {
                ResourceQueryResult before = PlayerResourceService.INSTANCE.query(freshBarbarian, PlayerResourceIds.RAGE);
                boolean hadCharge = before instanceof ResourceQueryResult.Success s && s.snapshot().currentUnits() > 0;
                ResourceQueryResult afterSpend = spendOneRageCharge(freshBarbarian);
                boolean pass = hadCharge && afterSpend instanceof ResourceQueryResult.Success success
                        && success.snapshot().currentUnits() == 1;
                return result(pass, "before=" + before + ", afterSpend=" + afterSpend);
            });

            safe(r, "External-review wording correction (2026-09-15): the authoritative trySpend path "
                    + "(called directly here, not through BarbarianRageAbility.onActivate — see the "
                    + "separate onActivate end-to-end check below) fails with no mutation once current "
                    + "is already 0, matching legacy consume()'s exact all-or-nothing contract", () -> {
                spendOneRageCharge(freshBarbarian); // 1 -> 0
                var spendResult = PlayerResourceService.INSTANCE.trySpend(freshBarbarian,
                        new ResourceCost.Scalar(PlayerResourceIds.RAGE, 1),
                        ResourceContext.of(ResourceCause.of(ResourceContext.CauseTypes.ABILITY_COST)));
                boolean stillZero = PlayerResourceService.INSTANCE.query(freshBarbarian, PlayerResourceIds.RAGE)
                        instanceof ResourceQueryResult.Success s && s.snapshot().currentUnits() == 0;
                return result(!spendResult.isSuccess() && stillZero, "spendResult=" + spendResult);
            });

            safe(r, "onShortRest restores exactly 1 charge, clamped at maximum", () -> {
                BarbarianRageAbility.onShortRest(freshBarbarian); // 0 -> 1
                ResourceQueryResult query = PlayerResourceService.INSTANCE.query(freshBarbarian, PlayerResourceIds.RAGE);
                return result(query instanceof ResourceQueryResult.Success s && s.snapshot().currentUnits() == 1, "query=" + query);
            });

            safe(r, "onLongRest fully restores Rage to its current maximum", () -> {
                spendOneRageCharge(freshBarbarian); // 1 -> 0
                BarbarianRageAbility.onLongRest(freshBarbarian);
                ResourceQueryResult query = PlayerResourceService.INSTANCE.query(freshBarbarian, PlayerResourceIds.RAGE);
                return result(query instanceof ResourceQueryResult.Success s && s.snapshot().currentUnits() == 2, "query=" + query);
            });
        } finally {
            freshBarbarian.discard();
        }

        ServerPlayer legacyBarbarian = TotalityFakePlayer.create(level, "[BarbarianRageMigrationVerification-legacy]");
        try {
            safe(r, "Legacy NBT migration imports the exact partially-depleted legacy Rage value, not a full refill", () -> {
                ClassComponents.get(legacyBarbarian).selectClass(TotalityClasses.BARBARIAN_ID, 3);
                ChargeComponents.get(legacyBarbarian).registerPool(BarbarianRageAbility.CHARGE_ID, 3, RestType.SHORT, 1);
                ChargeComponents.get(legacyBarbarian).consume(BarbarianRageAbility.CHARGE_ID); // 3/3 -> 2/3 legacy-side
                BaselineResourceLifecycleEvents.migrateLegacyIfAbsent(legacyBarbarian);
                ResourceQueryResult query = PlayerResourceService.INSTANCE.query(legacyBarbarian, PlayerResourceIds.RAGE);
                return result(query instanceof ResourceQueryResult.Success s && s.snapshot().currentUnits() == 2, "query=" + query);
            });

            safe(r, "Re-running the legacy migration import is idempotent — already-migrated Generic "
                    + "state is never overwritten by stale legacy data", () -> {
                spendOneRageCharge(legacyBarbarian); // 2 -> 1
                BaselineResourceLifecycleEvents.migrateLegacyIfAbsent(legacyBarbarian);
                ResourceQueryResult query = PlayerResourceService.INSTANCE.query(legacyBarbarian, PlayerResourceIds.RAGE);
                return result(query instanceof ResourceQueryResult.Success s && s.snapshot().currentUnits() == 1,
                        "expected the post-spend value 1 to survive a repeated migration call, got " + query);
            });

            safe(r, "The durable migration marker is set, matching Mana/Stamina's own marker contract", () -> {
                PlayerResourceStateComponent state = ResourceStateComponents.get(legacyBarbarian);
                return result(state.isLegacyMigrated(PlayerResourceIds.RAGE), "isLegacyMigrated=" + state.isLegacyMigrated(PlayerResourceIds.RAGE));
            });
        } finally {
            legacyBarbarian.discard();
        }

        ServerPlayer onActivateBarbarian = TotalityFakePlayer.create(level, "[BarbarianRageMigrationVerification-onActivate]");
        try {
            ClassComponents.get(onActivateBarbarian).selectClass(TotalityClasses.BARBARIAN_ID, 1);
            BarbarianRageAbility.registerChargePool(onActivateBarbarian);

            safe(r, "External-review coverage addition (2026-09-15): the real BarbarianRageAbility."
                    + "onActivate end-to-end path (not a direct trySpend call) consumes exactly one "
                    + "charge and activates the Rage toggle", () -> {
                var abilities = AbilityComponents.ABILITIES.get((ComponentProvider) onActivateBarbarian);
                boolean toggleBefore = abilities.isToggleActive(BarbarianRageAbility.ID);
                zcylas.totality.api.ability.AbilityRegistry.BARBARIAN_RAGE.onActivate(onActivateBarbarian, null);
                ResourceQueryResult query = PlayerResourceService.INSTANCE.query(onActivateBarbarian, PlayerResourceIds.RAGE);
                boolean toggleAfter = abilities.isToggleActive(BarbarianRageAbility.ID);
                boolean pass = !toggleBefore && toggleAfter
                        && query instanceof ResourceQueryResult.Success s && s.snapshot().currentUnits() == 1;
                return result(pass, "toggleBefore=" + toggleBefore + ", toggleAfter=" + toggleAfter + ", query=" + query);
            });
        } finally {
            onActivateBarbarian.discard();
        }

        ServerPlayer levelUpBarbarian = TotalityFakePlayer.create(level, "[BarbarianRageMigrationVerification-levelup]");
        try {
            // External-review correction (2026-09-15, finding 1): begin immediately below a
            // RAGE_CHARGES threshold (class level 2 -> max 2), spend a charge, level across the
            // threshold (class level 3 -> max 3), and prove current is preserved while the resolved
            // maximum increases. Query-based proof only — see BarbarianRageAbilityMaximumSyncSourceRegressionTest
            // for the source-level pin that updateChargePool actually marks totality:rage dirty for
            // the Generic client sync path; a real connected client isn't available in this
            // dev-server-only environment, so this check does not by itself prove client delivery.
            ClassComponents.get(levelUpBarbarian).selectClass(TotalityClasses.BARBARIAN_ID, 2);
            BarbarianRageAbility.registerChargePool(levelUpBarbarian);
            spendOneRageCharge(levelUpBarbarian); // 2/2 -> 1/2

            safe(r, "External-review addition (2026-09-15, finding 1): leveling across a RAGE_CHARGES "
                    + "threshold preserves current and only raises the resolved maximum — 1/2 -> 1/3, "
                    + "never 2/2 or a stray refill to 3/3", () -> {
                ClassComponents.get(levelUpBarbarian).setClassLevel(TotalityClasses.BARBARIAN_ID, 3);
                BarbarianRageAbility.updateChargePool(levelUpBarbarian);
                ResourceQueryResult query = PlayerResourceService.INSTANCE.query(levelUpBarbarian, PlayerResourceIds.RAGE);
                boolean pass = query instanceof ResourceQueryResult.Success s
                        && s.snapshot().currentUnits() == 1 && s.snapshot().maximumUnits() == 3;
                return result(pass, "query=" + query);
            });
        } finally {
            levelUpBarbarian.discard();
        }

        ServerPlayer nonBarbarian = TotalityFakePlayer.create(level, "[BarbarianRageMigrationVerification-nonbarbarian]");
        try {
            safe(r, "External-review addition (2026-09-15, finding 5): a non-Barbarian never gains "
                    + "totality:rage state, even after an explicit reconciliation pass", () -> {
                zcylas.totality.api.rpg.resources.integration.BarbarianRageResources.reconcile(nonBarbarian);
                boolean hasState = ResourceStateComponents.get(nonBarbarian).hasState(PlayerResourceIds.RAGE);
                return result(!hasState, "hasState=" + hasState);
            });
        } finally {
            nonBarbarian.discard();
        }

        ServerPlayer reconcileBarbarian = TotalityFakePlayer.create(level, "[BarbarianRageMigrationVerification-reconcile]");
        try {
            ClassComponents.get(reconcileBarbarian).selectClass(TotalityClasses.BARBARIAN_ID, 1);
            BarbarianRageAbility.registerChargePool(reconcileBarbarian);

            safe(r, "External-review addition (2026-09-15, finding 5): a Barbarian has exactly one "
                    + "totality:rage state entry after reconciliation", () -> {
                boolean hasState = ResourceStateComponents.get(reconcileBarbarian).hasState(PlayerResourceIds.RAGE);
                return result(hasState, "hasState=" + hasState);
            });

            safe(r, "External-review addition (2026-09-15, finding 5): repeated reconciliation does "
                    + "not refill a partially depleted Rage pool", () -> {
                spendOneRageCharge(reconcileBarbarian); // 2/2 -> 1/2
                zcylas.totality.api.rpg.resources.integration.BarbarianRageResources.reconcile(reconcileBarbarian);
                ResourceQueryResult query = PlayerResourceService.INSTANCE.query(reconcileBarbarian, PlayerResourceIds.RAGE);
                return result(query instanceof ResourceQueryResult.Success s && s.snapshot().currentUnits() == 1,
                        "expected the post-spend value 1 to survive re-reconciliation, got " + query);
            });
        } finally {
            reconcileBarbarian.discard();
        }

        r.summarize();
    }

    private static ResourceQueryResult spendOneRageCharge(ServerPlayer player) {
        PlayerResourceService.INSTANCE.trySpend(player, new ResourceCost.Scalar(PlayerResourceIds.RAGE, 1),
                ResourceContext.of(ResourceCause.of(ResourceContext.CauseTypes.ABILITY_COST)));
        return PlayerResourceService.INSTANCE.query(player, PlayerResourceIds.RAGE);
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
