package zcylas.totality.api.rpg.resources.verification;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.Totality;
import zcylas.totality.api.core.util.ServerScheduler;
import zcylas.totality.api.core.util.VerificationReporter;
import zcylas.totality.api.rpg.resources.*;
import zcylas.totality.api.rpg.resources.external.ExternalPlayerResourceAdapterRegistry;
import zcylas.totality.api.rpg.resources.integration.*;
import zcylas.totality.server.TotalityFakePlayer;

import java.util.List;

/**
 * Dev-environment-gated self-test for the 2026-09-15 pre-Phase-4 mutation/grant/maximum-resolver
 * foundation — the same "exercise production code, not a reimplementation" discipline every other
 * {@code *Verification} suite in this codebase follows (e.g. {@code OffhandAttackVerification}),
 * but using a deliberately <b>isolated</b> {@link PlayerResourceRegistry}/{@link PlayerResourceService}/
 * {@link ResourceGrantReconciler} triple rather than the production {@code .INSTANCE} singletons.
 *
 * <p>Two things this buys, together:
 * <ul>
 *     <li>A real, component-attached {@link ServerPlayer} ({@link TotalityFakePlayer}) proves the
 *         mutation/grant machinery genuinely works against Minecraft's real
 *         {@code PlayerResourceStateComponent} attachment and a real running server — something no
 *         plain-JUnit test in this codebase can do (constructing a real {@code ServerPlayer} is not
 *         possible outside a running game), and something the extensive plain-JUnit coverage in
 *         {@code PlayerResourceServiceMutationTest}/{@code PlayerResourceServiceTransactionTest}/
 *         {@code PlayerResourceServiceMaximumResolutionTest}/{@code ResourceGrantReconcilerTest}
 *         (all against {@code new PlayerResourceStateComponent(null)}) does not cover.</li>
 *     <li>An isolated registry/service/reconciler triple means the dev-only test resource this
 *         suite defines <b>never touches {@link PlayerResourceRegistry#INSTANCE}</b> — it does not
 *         exist for real players, in dev or in production, satisfying "do not expose test content
 *         in production builds merely for convenience" as strictly as possible (not merely
 *         dev-gated, but structurally absent from the production registry entirely).</li>
 * </ul>
 *
 * <p>Registration is itself gated on {@link VerificationReporter#isDevEnvironment()} — in a
 * production build, {@link #register()} is a complete no-op; no event listener is even added.
 */
public final class ResourceFoundationVerification {

    private static final Identifier TEST_RESOURCE_ID = Identifier.fromNamespaceAndPath("totality", "devtest_resource_foundation");
    private static final Identifier TEST_SOURCE_ID = Identifier.fromNamespaceAndPath("totality", "devtest_resource_foundation_source");
    private static final int SUITE_DELAY_TICKS = 5;

    private ResourceFoundationVerification() {}

    public static void register() {
        if (!VerificationReporter.liveWorldVerificationEnabled()) return; // opt-in: runs against the live world
        ServerLifecycleEvents.SERVER_STARTED.register(ResourceFoundationVerification::scheduleDelayed);
    }

    private static void scheduleDelayed(MinecraftServer server) {
        ServerScheduler.getInstance().queue(ResourceFoundationVerification::runSelfTest, SUITE_DELAY_TICKS);
    }

    private static void runSelfTest(MinecraftServer server) {
        VerificationReporter r = new VerificationReporter(Totality.LOGGER, "ResourceFoundationVerification");

        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        PlayerResourceDefinition definition = PlayerResourceDefinition.builder(TEST_RESOURCE_ID, ResourceModel.SCALAR)
                .absoluteMinimum(0)
                .authoredBaseMaximum(100)
                .build();
        registry.register(definition);
        ExternalPlayerResourceAdapterRegistry adapters = new ExternalPlayerResourceAdapterRegistry();
        registry.freeze(adapters);
        PlayerResourceService service = new PlayerResourceService(registry, adapters);

        ResourceGrantRegistry grantRegistry = new ResourceGrantRegistry();
        grantRegistry.register(player -> List.of(new ResourceGrant(
                TEST_RESOURCE_ID, TEST_SOURCE_ID, ResourceGrantSourceType.CUSTOM, ResourceGrantMode.PERSISTENT,
                new ResourceGrantInitialization.AtMaximum(), ResourceRemovalPolicy.REMOVE_STATE, ResourceVisibilityPolicy.ALWAYS_FOR_OWNER, 0)));
        ResourceGrantReconciler reconciler = new ResourceGrantReconciler(registry, grantRegistry, new ResourceGrantPolicyRegistry(), service);

        ServerLevel level = server.overworld();
        ServerPlayer player = TotalityFakePlayer.create(level, "[ResourceFoundationVerification]");
        try {
            safe(r, "Querying an ungranted resource against a real ServerPlayer fails structurally, never fabricates a snapshot", () -> {
                ResourceQueryResult before = service.query(player, TEST_RESOURCE_ID);
                boolean pass = before instanceof ResourceQueryResult.Failure failure
                        && failure.reason() == ResourceQueryFailureReason.STATE_NOT_INSTANTIATED;
                return result(pass, "result=" + before);
            });

            safe(r, "Reconciliation grants and instantiates the resource on a real component-attached ServerPlayer", () -> {
                var reconciliation = reconciler.reconcile(player, ResourceStateComponents.get(player));
                ResourceQueryResult after = service.query(player, TEST_RESOURCE_ID);
                boolean pass = reconciliation.instantiated().contains(TEST_RESOURCE_ID)
                        && after instanceof ResourceQueryResult.Success success
                        && success.snapshot().currentUnits() == 100
                        && success.snapshot().maximumUnits() == 100;
                return result(pass, "instantiated=" + reconciliation.instantiated() + ", query=" + after);
            });

            safe(r, "Re-reconciling an unchanged grant does not refill or duplicate state", () -> {
                ResourceOperationResult spend = service.trySpend(player, new ResourceCost.Scalar(TEST_RESOURCE_ID, 40),
                        ResourceContext.of(ResourceCause.of(Identifier.fromNamespaceAndPath("totality", "devtest"))));
                if (!(spend instanceof ResourceOperationResult.Success)) {
                    return result(false, "setup spend itself failed: " + spend);
                }
                reconciler.reconcile(player, ResourceStateComponents.get(player));
                ResourceQueryResult after = service.query(player, TEST_RESOURCE_ID);
                boolean pass = after instanceof ResourceQueryResult.Success success && success.snapshot().currentUnits() == 60;
                return result(pass, "expected 60 after a 40 spend survived re-reconciliation, got " + after);
            });

            safe(r, "restore() raises current on the real player and marks the resource dirty for sync", () -> {
                ResourceOperationResult restore = service.restore(player, ResourceAmount.scalar(TEST_RESOURCE_ID, 15),
                        ResourceContext.of(ResourceCause.of(Identifier.fromNamespaceAndPath("totality", "devtest"))));
                ResourceQueryResult after = service.query(player, TEST_RESOURCE_ID);
                boolean pass = restore instanceof ResourceOperationResult.Success success
                        && success.after().currentUnits() == 75
                        && after instanceof ResourceQueryResult.Success q && q.snapshot().currentUnits() == 75;
                return result(pass, "expected 75 after restoring 15 from 60, got " + after);
            });

            safe(r, "A transaction spanning this resource commits atomically on the real player", () -> {
                ResourceTransaction transaction = ResourceTransaction.of(
                        new ResourceOperation.Spend(new ResourceCost.Scalar(TEST_RESOURCE_ID, 25)));
                ResourceTransactionResult txResult = service.transact(player, transaction,
                        ResourceContext.of(ResourceCause.of(Identifier.fromNamespaceAndPath("totality", "devtest"))));
                ResourceQueryResult after = service.query(player, TEST_RESOURCE_ID);
                boolean pass = txResult.success()
                        && after instanceof ResourceQueryResult.Success q && q.snapshot().currentUnits() == 50;
                return result(pass, "expected 50 after a 25-spend transaction from 75, got " + after);
            });

            safe(r, "Final grant removal deletes state on the real player (REMOVE_STATE, the default)", () -> {
                var reconciliation = reconciler.reconcile(player, ResourceStateComponents.get(player));
                boolean pass = reconciliation.instantiated().isEmpty(); // grant provider still active — nothing removed this round
                if (!pass) return result(false, "unexpected instantiation on a re-reconcile with no ownership change: " + reconciliation);

                ResourceGrantReconciler noProviderReconciler =
                        new ResourceGrantReconciler(registry, new ResourceGrantRegistry(), new ResourceGrantPolicyRegistry(), service);
                var removal = noProviderReconciler.reconcile(player, ResourceStateComponents.get(player));
                ResourceQueryResult after = service.query(player, TEST_RESOURCE_ID);
                pass = removal.removed().contains(TEST_RESOURCE_ID)
                        && after instanceof ResourceQueryResult.Failure failure
                        && failure.reason() == ResourceQueryFailureReason.STATE_NOT_INSTANTIATED;
                return result(pass, "removed=" + removal.removed() + ", query=" + after);
            });
        } finally {
            player.discard();
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
