package zcylas.totality.api.rpg.resources.verification;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.Totality;
import zcylas.totality.api.core.util.ServerScheduler;
import zcylas.totality.api.core.util.VerificationReporter;
import zcylas.totality.api.rpg.mana.PlayerManaManager;
import zcylas.totality.api.rpg.resources.PlayerResourceComponent;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.PlayerResourceStateComponent;
import zcylas.totality.api.rpg.resources.ResourceComponents;
import zcylas.totality.api.rpg.resources.ResourceStateComponents;
import zcylas.totality.api.rpg.stamina.PlayerStaminaManager;
import zcylas.totality.networking.resource.BaselineResourceLifecycleEvents;
import zcylas.totality.server.TotalityFakePlayer;

/**
 * Dev-environment-gated self-test for the Phase 4 Mana/Stamina migration (2026-09-15), matching
 * {@code ResourceFoundationVerification}'s established pattern — but exercising the REAL production
 * {@code PlayerResourceRegistry.INSTANCE}/{@code totality:mana}/{@code totality:stamina}
 * registrations (unlike that suite's deliberately isolated registry), since what this migration
 * needs proven is that the real wiring actually works against a real, component-attached {@code
 * ServerPlayer}: {@link PlayerManaManager}/{@link PlayerStaminaManager}'s facades, {@link
 * zcylas.totality.api.rpg.resources.integration.PlayerBaselineResources}'s grant/reconciliation, the
 * real {@link zcylas.totality.api.rpg.mana.ManaMaximumResolver}/{@link
 * zcylas.totality.api.rpg.stamina.StaminaMaximumResolver}, and the legacy NBT migration-import step.
 *
 * <p>Registration is gated on {@link VerificationReporter#isDevEnvironment()} — a complete no-op in
 * a production build.
 */
public final class BaselineResourceMigrationVerification {

    private static final int SUITE_DELAY_TICKS = 5;

    private BaselineResourceMigrationVerification() {}

    public static void register() {
        if (!VerificationReporter.isDevEnvironment()) return;
        ServerLifecycleEvents.SERVER_STARTED.register(BaselineResourceMigrationVerification::scheduleDelayed);
    }

    private static void scheduleDelayed(MinecraftServer server) {
        ServerScheduler.getInstance().queue(BaselineResourceMigrationVerification::runSelfTest, SUITE_DELAY_TICKS);
    }

    private static void runSelfTest(MinecraftServer server) {
        VerificationReporter r = new VerificationReporter(Totality.LOGGER, "BaselineResourceMigrationVerification");
        ServerLevel level = server.overworld();

        ServerPlayer freshPlayer = TotalityFakePlayer.create(level, "[BaselineResourceMigrationVerification-fresh]");
        try {
            safe(r, "A freshly created player self-heals Mana to a full 100/100 through the facade "
                    + "(never having gone through ServerPlayConnectionEvents.JOIN)", () -> {
                int mana = PlayerManaManager.getMana(freshPlayer);
                int maxMana = PlayerManaManager.getMaxMana(freshPlayer);
                return result(mana == 100 && maxMana == 100, "mana=" + mana + "/" + maxMana);
            });

            safe(r, "A freshly created player self-heals Stamina to a full 100/100 through the facade", () -> {
                int stamina = PlayerStaminaManager.getStamina(freshPlayer);
                int maxStamina = PlayerStaminaManager.getMaxStamina(freshPlayer);
                return result(stamina == 100 && maxStamina == 100, "stamina=" + stamina + "/" + maxStamina);
            });

            safe(r, "removeMana clamps at the floor rather than rejecting, matching legacy drain semantics", () -> {
                PlayerManaManager.removeMana(freshPlayer, 30);
                int after = PlayerManaManager.getMana(freshPlayer);
                PlayerManaManager.removeMana(freshPlayer, 999);
                int floored = PlayerManaManager.getMana(freshPlayer);
                return result(after == 70 && floored == 0, "after=" + after + ", floored=" + floored);
            });

            safe(r, "addMana restores and clamps at the resolved maximum", () -> {
                PlayerManaManager.addMana(freshPlayer, 25);
                int midway = PlayerManaManager.getMana(freshPlayer);
                PlayerManaManager.addMana(freshPlayer, 999);
                int capped = PlayerManaManager.getMana(freshPlayer);
                return result(midway == 25 && capped == 100, "midway=" + midway + ", capped=" + capped);
            });

            safe(r, "removeStamina/addStamina round-trip identically to Mana's", () -> {
                PlayerStaminaManager.removeStamina(freshPlayer, 40);
                int drained = PlayerStaminaManager.getStamina(freshPlayer);
                PlayerStaminaManager.addStamina(freshPlayer, 15);
                int restored = PlayerStaminaManager.getStamina(freshPlayer);
                return result(drained == 60 && restored == 75, "drained=" + drained + ", restored=" + restored);
            });

            safe(r, "setMana/setStamina (the privileged absolute setter used by PlayerResourceRecalculator "
                    + "and admin commands) sets exactly the requested value", () -> {
                PlayerManaManager.setMana(freshPlayer, 42);
                PlayerStaminaManager.setStamina(freshPlayer, 17);
                int mana = PlayerManaManager.getMana(freshPlayer);
                int stamina = PlayerStaminaManager.getStamina(freshPlayer);
                return result(mana == 42 && stamina == 17, "mana=" + mana + ", stamina=" + stamina);
            });

            safe(r, "PlayerStaminaManager.onLongRest fully restores Stamina to its current maximum", () -> {
                PlayerStaminaManager.onLongRest(freshPlayer);
                int stamina = PlayerStaminaManager.getStamina(freshPlayer);
                return result(stamina == 100, "stamina=" + stamina);
            });

            safe(r, "PlayerResourceService resolves Mana's maximum through the real ManaMaximumResolver, "
                    + "not a short-circuited authored base", () -> {
                var query = zcylas.totality.api.rpg.resources.PlayerResourceService.INSTANCE
                        .query(freshPlayer, PlayerResourceIds.MANA);
                boolean pass = query instanceof zcylas.totality.api.rpg.resources.ResourceQueryResult.Success success
                        && success.snapshot().maximumUnits() == PlayerManaManager.getMaxMana(freshPlayer);
                return result(pass, "query=" + query);
            });
        } finally {
            freshPlayer.discard();
        }

        ServerPlayer legacyPlayer = TotalityFakePlayer.create(level, "[BaselineResourceMigrationVerification-legacy]");
        try {
            safe(r, "Legacy NBT migration imports the exact partially-depleted legacy value, not a full refill", () -> {
                PlayerResourceComponent legacy = ResourceComponents.get(legacyPlayer);
                legacy.setMana(63);
                legacy.setStamina(41);
                BaselineResourceLifecycleEvents.migrateLegacyIfAbsent(legacyPlayer);
                int mana = PlayerManaManager.getMana(legacyPlayer);
                int stamina = PlayerStaminaManager.getStamina(legacyPlayer);
                return result(mana == 63 && stamina == 41, "mana=" + mana + ", stamina=" + stamina);
            });

            safe(r, "Re-running the legacy migration import is idempotent — already-migrated Generic "
                    + "state is never overwritten by stale legacy data", () -> {
                PlayerManaManager.removeMana(legacyPlayer, 20); // simulate gameplay spend after migration: 63 -> 43
                BaselineResourceLifecycleEvents.migrateLegacyIfAbsent(legacyPlayer);
                int mana = PlayerManaManager.getMana(legacyPlayer);
                return result(mana == 43, "expected the post-spend value 43 to survive a repeated migration call, got " + mana);
            });

            safe(r, "Final external-review correction: losing/quarantining Generic state after a "
                    + "successful migration does NOT resurrect the stale legacy value — the durable "
                    + "marker (not mere state presence) is what the import guard consults", () -> {
                PlayerResourceStateComponent state = ResourceStateComponents.get(legacyPlayer);
                boolean migratedBefore = state.isLegacyMigrated(PlayerResourceIds.MANA);
                // Simulate Generic state becoming absent for a reason other than a real grant loss
                // (e.g. a persisted-data quarantine) — current Mana is 43 at this point.
                state.removeState(PlayerResourceIds.MANA);
                BaselineResourceLifecycleEvents.migrateLegacyIfAbsent(legacyPlayer);
                int mana = PlayerManaManager.getMana(legacyPlayer);
                // The stale legacy component still holds 63 (never dual-written) — if the presence-only
                // guard were still in charge, this would incorrectly resurrect 63. Instead, the marker
                // blocks re-import entirely, and the subsequent self-heal (ensureInstantiated ->
                // reconcile -> AtMaximum, exactly like ordinary respawn) fills to the resolved max.
                boolean pass = migratedBefore && mana != 63 && mana == PlayerManaManager.getMaxMana(legacyPlayer);
                return result(pass, "migratedBefore=" + migratedBefore + ", mana=" + mana
                        + " (must not be the stale legacy value 63)");
            });
        } finally {
            legacyPlayer.discard();
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
