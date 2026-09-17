package zcylas.totality.api.soulgem.verification;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.Totality;
import zcylas.totality.api.core.util.ServerScheduler;
import zcylas.totality.api.core.util.VerificationReporter;
import zcylas.totality.api.mob.stats.MobRank;
import zcylas.totality.api.soulgem.CapturedSoul;
import zcylas.totality.api.soulgem.CapturedSoulComponent;
import zcylas.totality.api.soulgem.SoulCaptureResult;
import zcylas.totality.api.soulgem.SoulCaptureService;
import zcylas.totality.api.soulgem.SoulCategory;
import zcylas.totality.init.items.FoodItems;
import zcylas.totality.init.items.MagicItems;

import java.util.UUID;

/**
 * Dev-environment-gated self-test for the Soul Gem foundation — proves everything that genuinely
 * needs a real, bootstrapped {@code ItemStack}/{@code DataComponentType}/item-registry lifecycle
 * (stacking compatibility via {@code ItemStack.isSameItemSameComponents}, real component
 * set/read-back, real {@link SoulGemItem} instances resolved through {@code BuiltInRegistries.ITEM}).
 * Pure logic that needs no live registries (MobRank order/comparison, {@link
 * zcylas.totality.api.soulgem.SoulGemAcceptanceRule} behavior, {@link CapturedSoul} record equality,
 * codec round-trips against plain {@code JsonOps}) is covered by plain JUnit instead — mirrors
 * {@code FoodSystemVerification}/{@code FoodResourceDefinitionTest}'s established split in this
 * repo; see {@code HealingPotionItemContractTest}'s Javadoc for why plain JUnit cannot construct a
 * real {@code Item}/{@code ItemStack} in this codebase (the item registry is not bootstrapped under
 * plain JUnit). See
 * {@code TOTALITY_SOUL_GEM_FOUNDATION_IMPLEMENTATION_REPORT_2026-09-17.md} §17-18.
 *
 * <p>No live Entity/Soul Trap capture pipeline exists yet — every {@link CapturedSoul} here is
 * constructed directly, exactly as a future capture system's own tests would still need to for unit
 * coverage of the vessel/acceptance logic itself.
 *
 * <p>Registration is gated on {@link VerificationReporter#isDevEnvironment()} — a complete no-op in
 * a production build.
 */
public final class SoulGemSystemVerification {

    private static final int SUITE_DELAY_TICKS = 5;
    private static final Identifier ZOMBIE = Identifier.fromNamespaceAndPath("minecraft", "zombie");

    private SoulGemSystemVerification() {}

    public static void register() {
        if (!VerificationReporter.isDevEnvironment()) return;
        ServerLifecycleEvents.SERVER_STARTED.register(SoulGemSystemVerification::scheduleDelayed);
    }

    private static void scheduleDelayed(MinecraftServer server) {
        ServerScheduler.getInstance().queue(SoulGemSystemVerification::runSelfTest, SUITE_DELAY_TICKS);
    }

    private static CapturedSoul soul(MobRank rank) {
        return new CapturedSoul(UUID.randomUUID(), rank, SoulCategory.ORDINARY, ZOMBIE);
    }

    private static void runSelfTest(MinecraftServer server) {
        VerificationReporter r = new VerificationReporter(Totality.LOGGER, "SoulGemSystemVerification");

        safe(r, "REGISTRATION: Petty and Common Soul Gems both resolve as real SoulGemItem instances", () -> {
            boolean pass = MagicItems.PETTY_SOUL_GEM != null && MagicItems.COMMON_SOUL_GEM != null;
            return result(pass, "petty=" + MagicItems.PETTY_SOUL_GEM + ", common=" + MagicItems.COMMON_SOUL_GEM);
        });

        safe(r, "STACKING: two identical empty Petty Soul Gem stacks are stack-compatible", () -> {
            ItemStack a = new ItemStack(MagicItems.PETTY_SOUL_GEM);
            ItemStack b = new ItemStack(MagicItems.PETTY_SOUL_GEM);
            boolean pass = ItemStack.isSameItemSameComponents(a, b);
            return result(pass, "isSameItemSameComponents=" + pass);
        });

        safe(r, "STACKING: two identical empty Common Soul Gem stacks are stack-compatible", () -> {
            ItemStack a = new ItemStack(MagicItems.COMMON_SOUL_GEM);
            ItemStack b = new ItemStack(MagicItems.COMMON_SOUL_GEM);
            boolean pass = ItemStack.isSameItemSameComponents(a, b);
            return result(pass, "isSameItemSameComponents=" + pass);
        });

        safe(r, "FILL: capturing a Rank F ORDINARY soul into an empty Petty gem succeeds and writes "
                + "exactly one CapturedSoul component", () -> {
            ItemStack stack = new ItemStack(MagicItems.PETTY_SOUL_GEM);
            boolean hadComponentBefore = stack.has(CapturedSoulComponent.CAPTURED_SOUL);
            CapturedSoul offered = soul(MobRank.F);
            SoulCaptureResult outcome = SoulCaptureService.attemptCapture(stack, offered);
            boolean hasComponentAfter = stack.has(CapturedSoulComponent.CAPTURED_SOUL);
            CapturedSoul stored = stack.get(CapturedSoulComponent.CAPTURED_SOUL);
            boolean pass = outcome == SoulCaptureResult.SUCCESS && !hadComponentBefore
                    && hasComponentAfter && offered.equals(stored);
            return result(pass, "outcome=" + outcome + ", stored=" + stored);
        });

        safe(r, "STATE: an already-filled Petty gem rejects a second capture attempt and keeps the "
                + "original soul unchanged", () -> {
            ItemStack stack = new ItemStack(MagicItems.PETTY_SOUL_GEM);
            CapturedSoul first = soul(MobRank.F);
            SoulCaptureService.attemptCapture(stack, first);
            CapturedSoul second = soul(MobRank.F);
            SoulCaptureResult outcome = SoulCaptureService.attemptCapture(stack, second);
            CapturedSoul stored = stack.get(CapturedSoulComponent.CAPTURED_SOUL);
            boolean pass = outcome == SoulCaptureResult.ALREADY_FILLED && stored.equals(first) && !stored.equals(second);
            return result(pass, "outcome=" + outcome + ", stored=" + stored + " (expected unchanged, ==first)");
        });

        safe(r, "PETTY: a Rank E soul is too strong for Petty (F only) and leaves the gem unchanged", () -> {
            ItemStack stack = new ItemStack(MagicItems.PETTY_SOUL_GEM);
            SoulCaptureResult outcome = SoulCaptureService.attemptCapture(stack, soul(MobRank.E));
            boolean pass = outcome == SoulCaptureResult.RANK_TOO_STRONG && !stack.has(CapturedSoulComponent.CAPTURED_SOUL);
            return result(pass, "outcome=" + outcome + ", hasComponent=" + stack.has(CapturedSoulComponent.CAPTURED_SOUL));
        });

        safe(r, "COMMON: Rank F, E, and D souls are all accepted by Common (three separate fresh gems)", () -> {
            boolean allOk = true;
            StringBuilder detail = new StringBuilder();
            for (MobRank rank : new MobRank[]{MobRank.F, MobRank.E, MobRank.D}) {
                ItemStack stack = new ItemStack(MagicItems.COMMON_SOUL_GEM);
                SoulCaptureResult outcome = SoulCaptureService.attemptCapture(stack, soul(rank));
                boolean ok = outcome == SoulCaptureResult.SUCCESS;
                allOk &= ok;
                detail.append(rank).append("=").append(outcome).append(" ");
            }
            return result(allOk, detail.toString());
        });

        safe(r, "COMMON: a Rank C soul is too strong for Common (F/E/D ceiling) and leaves the gem unchanged", () -> {
            ItemStack stack = new ItemStack(MagicItems.COMMON_SOUL_GEM);
            SoulCaptureResult outcome = SoulCaptureService.attemptCapture(stack, soul(MobRank.C));
            boolean pass = outcome == SoulCaptureResult.RANK_TOO_STRONG && !stack.has(CapturedSoulComponent.CAPTURED_SOUL);
            return result(pass, "outcome=" + outcome);
        });

        safe(r, "RANK 0: a Rank ZERO (Family Rank) soul is rejected by Common via the global "
                + "eligibility gate, before any rank-ceiling comparison, and leaves the gem unchanged", () -> {
            ItemStack stack = new ItemStack(MagicItems.COMMON_SOUL_GEM);
            SoulCaptureResult outcome = SoulCaptureService.attemptCapture(stack, soul(MobRank.ZERO));
            boolean pass = outcome == SoulCaptureResult.RANK_ZERO_INELIGIBLE && !stack.has(CapturedSoulComponent.CAPTURED_SOUL);
            return result(pass, "outcome=" + outcome);
        });

        safe(r, "NOT A SOUL GEM: attempting capture into an ordinary non-SoulGemItem stack fails cleanly", () -> {
            ItemStack stack = new ItemStack(FoodItems.PIZZA_MARGHERITA);
            SoulCaptureResult outcome = SoulCaptureService.attemptCapture(stack, soul(MobRank.F));
            boolean pass = outcome == SoulCaptureResult.NOT_A_SOUL_GEM;
            return result(pass, "outcome=" + outcome);
        });

        safe(r, "STACKING: two filled Petty gems with distinct soulInstanceId are NOT stack-compatible "
                + "(CapturedSoul is part of ItemStack component equality)", () -> {
            ItemStack a = new ItemStack(MagicItems.PETTY_SOUL_GEM);
            ItemStack b = new ItemStack(MagicItems.PETTY_SOUL_GEM);
            SoulCaptureService.attemptCapture(a, soul(MobRank.F));
            SoulCaptureService.attemptCapture(b, soul(MobRank.F));
            boolean same = ItemStack.isSameItemSameComponents(a, b);
            return result(!same, "isSameItemSameComponents=" + same + " (expected false, distinct soulInstanceId)");
        });

        safe(r, "STACKING: two filled Petty gems holding the exact SAME CapturedSoul value ARE "
                + "stack-compatible (equality is by value, not by object identity)", () -> {
            ItemStack a = new ItemStack(MagicItems.PETTY_SOUL_GEM);
            ItemStack b = new ItemStack(MagicItems.PETTY_SOUL_GEM);
            CapturedSoul shared = soul(MobRank.F);
            SoulCaptureService.attemptCapture(a, shared);
            SoulCaptureService.attemptCapture(b, shared);
            boolean same = ItemStack.isSameItemSameComponents(a, b);
            return result(same, "isSameItemSameComponents=" + same);
        });

        // ── STACKED-VESSEL CORRECTION (2026-09-17 review-correction pass) ──────────────────────
        // A capture attempt against a stack.getCount() > 1 ItemStack must fail via
        // STACKED_VESSEL_REQUIRES_SPLIT rather than writing one CapturedSoul onto every physical gem
        // the stack represents. See SoulCaptureService's own Javadoc for the exact check order.

        safe(r, "ONE-COUNT STILL WORKS: an explicit count=1 empty Petty gem still captures "
                + "successfully (the stacked-vessel fix must not have broken the ordinary path)", () -> {
            ItemStack stack = new ItemStack(MagicItems.PETTY_SOUL_GEM, 1);
            SoulCaptureResult outcome = SoulCaptureService.attemptCapture(stack, soul(MobRank.F));
            boolean pass = outcome == SoulCaptureResult.SUCCESS && stack.getCount() == 1
                    && stack.has(CapturedSoulComponent.CAPTURED_SOUL);
            return result(pass, "outcome=" + outcome + ", count=" + stack.getCount());
        });

        safe(r, "STACKED VESSEL (Petty): a stack of 2 empty Petty gems rejects capture as "
                + "STACKED_VESSEL_REQUIRES_SPLIT and is left completely unchanged (count, no "
                + "component)", () -> {
            ItemStack stack = new ItemStack(MagicItems.PETTY_SOUL_GEM, 2);
            SoulCaptureResult outcome = SoulCaptureService.attemptCapture(stack, soul(MobRank.F));
            boolean pass = outcome == SoulCaptureResult.STACKED_VESSEL_REQUIRES_SPLIT
                    && stack.getCount() == 2
                    && !stack.has(CapturedSoulComponent.CAPTURED_SOUL);
            return result(pass, "outcome=" + outcome + ", count=" + stack.getCount()
                    + ", hasComponent=" + stack.has(CapturedSoulComponent.CAPTURED_SOUL));
        });

        safe(r, "STACKED VESSEL (Common): a stack of 3 empty Common gems rejects capture as "
                + "STACKED_VESSEL_REQUIRES_SPLIT and is left completely unchanged", () -> {
            ItemStack stack = new ItemStack(MagicItems.COMMON_SOUL_GEM, 3);
            SoulCaptureResult outcome = SoulCaptureService.attemptCapture(stack, soul(MobRank.D));
            boolean pass = outcome == SoulCaptureResult.STACKED_VESSEL_REQUIRES_SPLIT
                    && stack.getCount() == 3
                    && !stack.has(CapturedSoulComponent.CAPTURED_SOUL);
            return result(pass, "outcome=" + outcome + ", count=" + stack.getCount());
        });

        safe(r, "CHECK ORDER: a stacked (count=2) ALREADY-FILLED gem still reports ALREADY_FILLED, "
                + "not STACKED_VESSEL_REQUIRES_SPLIT — this pass deliberately preserved the original "
                + "ALREADY_FILLED-first ordering (see SoulCaptureService's Javadoc for why), and the "
                + "original soul must remain exactly unchanged", () -> {
            ItemStack stack = new ItemStack(MagicItems.PETTY_SOUL_GEM, 1);
            CapturedSoul original = soul(MobRank.F);
            SoulCaptureService.attemptCapture(stack, original);
            stack.grow(1); // now count=2, already filled — a state only reachable by direct
                            // construction/admin action in this foundation (no real merge path exists
                            // yet since filled gems only stack when their CapturedSoul values are
                            // identical, which attemptCapture itself never produces from two distinct
                            // captures), but still a state this service must handle correctly.
            SoulCaptureResult outcome = SoulCaptureService.attemptCapture(stack, soul(MobRank.F));
            CapturedSoul stored = stack.get(CapturedSoulComponent.CAPTURED_SOUL);
            boolean pass = outcome == SoulCaptureResult.ALREADY_FILLED
                    && stack.getCount() == 2 && original.equals(stored);
            return result(pass, "outcome=" + outcome + ", count=" + stack.getCount() + ", stored=" + stored);
        });

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
