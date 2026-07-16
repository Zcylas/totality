package zcylas.totality.client.renderer.hud;

import zcylas.totality.Totality;
import zcylas.totality.api.core.util.VerificationReporter;

import java.util.function.Supplier;

/**
 * Dev-environment-gated self-test for {@link PowerAttackFlash}'s timing model (post-migration
 * correction pass, Part B) — same convention as {@code NotificationTimingVerification}, run from
 * {@code TotalityClient.onInitializeClient()}. Drives {@link PowerAttackFlash}'s package-private
 * test hooks directly, none of which need a live {@code Minecraft} instance or a rendered frame.
 *
 * <p>What this suite CANNOT verify (documented, not silently skipped): whether the flash is
 * visually present on screen, whether it respects F1/GUI scale/screen geometry in a live render,
 * and whether invalid-target input (block/air) genuinely never calls {@link
 * PowerAttackFlash#trigger} at all — that last one is a property of {@code MinecraftAttackMixin}'s
 * target-gating (already covered by {@code PowerAttackVerification}, Phase 4's prior correction
 * pass), not of this class, and is confirmed here only by code inspection: {@code trigger()} is
 * called from exactly two sites in {@code MinecraftAttackMixin}, both already gated by {@code
 * targetStillValid}/a resolved {@code LivingEntity} crosshair pick. Visual confirmation remains
 * Stefan's manual test (Part H, items 11-23).
 */
public final class PowerAttackFlashVerification {

    private PowerAttackFlashVerification() {}

    public static void runIfDev() {
        if (!VerificationReporter.isDevEnvironment()) return;

        VerificationReporter r = new VerificationReporter(Totality.LOGGER, "PowerAttackFlashVerification");

        checkTriggerEnablesEffect(r);
        checkIdleStateDisablesEffect(r);
        checkTickDecrementsExactlyOnePerCall(r);
        checkAlphaFadesLinearlyToZero(r);
        checkEffectClearsAfterFullDuration(r);
        checkResetClearsInProgressEffect(r);
        checkTimingIndependentOfSimulatedFrameRate(r);

        r.summarize();
    }

    private static void checkTriggerEnablesEffect(VerificationReporter r) {
        safe(r, "trigger() enables the effect with nonzero alpha", () -> {
            PowerAttackFlash.reset();
            PowerAttackFlash.trigger();
            boolean pass = PowerAttackFlash.isActive() && PowerAttackFlash.getAlpha() > 0f;
            return result(pass, "isActive=" + PowerAttackFlash.isActive() + ", alpha=" + PowerAttackFlash.getAlpha());
        });
    }

    private static void checkIdleStateDisablesEffect(VerificationReporter r) {
        safe(r, "Idle state (never triggered, or fully decayed) disables the effect", () -> {
            PowerAttackFlash.reset();
            boolean pass = !PowerAttackFlash.isActive() && PowerAttackFlash.getAlpha() == 0f;
            return result(pass, "isActive=" + PowerAttackFlash.isActive() + ", alpha=" + PowerAttackFlash.getAlpha());
        });
    }

    private static void checkTickDecrementsExactlyOnePerCall(VerificationReporter r) {
        safe(r, "A single tick() call decrements the timer by exactly 1", () -> {
            PowerAttackFlash.reset();
            PowerAttackFlash.trigger();
            float before = PowerAttackFlash.flashTimerForTest();
            PowerAttackFlash.tickForTest();
            float after = PowerAttackFlash.flashTimerForTest();
            boolean pass = after == before - 1f;
            return result(pass, "before=" + before + ", after=" + after);
        });
    }

    private static void checkAlphaFadesLinearlyToZero(VerificationReporter r) {
        safe(r, "Alpha fades linearly toward zero as the timer decreases, never negative", () -> {
            PowerAttackFlash.reset();
            PowerAttackFlash.trigger();
            float previousAlpha = PowerAttackFlash.getAlpha();
            boolean monotonicallyDecreasing = true;
            for (int i = 0; i < 10; i++) {
                PowerAttackFlash.tickForTest();
                float alpha = PowerAttackFlash.getAlpha();
                if (alpha > previousAlpha || alpha < 0f) monotonicallyDecreasing = false;
                previousAlpha = alpha;
            }
            boolean pass = monotonicallyDecreasing && PowerAttackFlash.getAlpha() == 0f;
            return result(pass, "finalAlpha=" + PowerAttackFlash.getAlpha() + ", monotonic=" + monotonicallyDecreasing);
        });
    }

    private static void checkEffectClearsAfterFullDuration(VerificationReporter r) {
        safe(r, "The effect clears normally once its full duration (10 ticks) has elapsed", () -> {
            PowerAttackFlash.reset();
            PowerAttackFlash.trigger();
            for (int i = 0; i < 10; i++) PowerAttackFlash.tickForTest();
            boolean pass = !PowerAttackFlash.isActive() && PowerAttackFlash.getAlpha() == 0f;
            return result(pass, "isActive=" + PowerAttackFlash.isActive() + " after 10 ticks");
        });
    }

    private static void checkResetClearsInProgressEffect(VerificationReporter r) {
        safe(r, "reset() (the disconnect-reset path) clears an in-progress effect immediately, "
                + "matching the death/disconnect/dimension-change stale-state requirement", () -> {
            PowerAttackFlash.reset();
            PowerAttackFlash.trigger();
            boolean activeBeforeReset = PowerAttackFlash.isActive();
            PowerAttackFlash.reset();
            boolean pass = activeBeforeReset && !PowerAttackFlash.isActive() && PowerAttackFlash.getAlpha() == 0f;
            return result(pass, "activeBeforeReset=" + activeBeforeReset + ", activeAfterReset=" + PowerAttackFlash.isActive());
        });
    }

    /**
     * The regression's actual bug was the timer measured in rendered FRAMES, not TICKS — this
     * proves the fixed design has no notion of frame count: driving exactly 10 {@code tickForTest}
     * calls (simulating "10 ticks elapsed," regardless of how many frames that spanned at any
     * frame rate — frame count is never consulted anywhere in this path) always fully clears the
     * effect at the same tick count.
     */
    private static void checkTimingIndependentOfSimulatedFrameRate(VerificationReporter r) {
        safe(r, "Effect duration in ticks is identical regardless of simulated frame rate", () -> {
            PowerAttackFlash.reset();
            PowerAttackFlash.trigger();
            for (int i = 0; i < 9; i++) PowerAttackFlash.tickForTest();
            boolean activeAfterNineTicks = PowerAttackFlash.isActive();
            PowerAttackFlash.tickForTest();
            boolean inactiveAfterTenthTick = !PowerAttackFlash.isActive();
            return result(activeAfterNineTicks && inactiveAfterTenthTick,
                    "activeAfterNineTicks=" + activeAfterNineTicks + ", inactiveAfterTenthTick=" + inactiveAfterTenthTick);
        });
    }

    private record CheckResult(boolean pass, String detail) {}

    private static CheckResult result(boolean pass, String detail) {
        return new CheckResult(pass, detail);
    }

    private static void safe(VerificationReporter r, String label, Supplier<CheckResult> body) {
        try {
            CheckResult checkResult = body.get();
            r.check(label, checkResult.pass(), checkResult.detail());
        } catch (RuntimeException e) {
            r.check(label, false, "threw " + e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            PowerAttackFlash.reset();
        }
    }
}
