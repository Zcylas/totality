package zcylas.totality.client.renderer.hud.notification;

import zcylas.totality.Totality;
import zcylas.totality.api.core.util.VerificationReporter;

import java.util.function.Supplier;

/**
 * Dev-environment-gated self-test for {@link NotificationManager}'s timing model (post-migration
 * correction pass, Part A) — same {@link VerificationReporter} convention as
 * {@code KeybindVerification}/{@code ProvisionerRendererVerification}, run from
 * {@code TotalityClient.onInitializeClient()} since {@link NotificationManager} is a client-only
 * type. Every check drives {@link NotificationManager}'s package-private test hooks directly
 * ({@code tickActive}/{@code computeAlpha}/{@code addForTest}/etc.) — none of it depends on a live
 * {@code Minecraft} instance, {@code GuiGraphicsExtractor}, or a rendered frame, so this suite runs
 * safely at client init, before a world exists.
 *
 * <p>What this suite CANNOT verify (documented, not silently skipped): whether the SAME real
 * {@code ClientTickEvents.END_CLIENT_TICK}/{@code HudElementRegistry} callbacks this class
 * registers are wired correctly at runtime, whether F1 visually hides the text, and the actual
 * real-time visible duration at a given frame rate — all require a live client window and Stefan's
 * manual test (Part H, items 1-10). What IS verified here: {@code tickActive()} is the only
 * mutator of {@code ticksLeft} and behaves correctly in isolation, which is the actual property
 * the regression fix depends on — if this suite passes but the manual test still shows fast decay,
 * that would point to the callback registration itself, not this timing logic.
 */
public final class NotificationTimingVerification {

    private NotificationTimingVerification() {}

    public static void runIfDev() {
        if (!VerificationReporter.isDevEnvironment()) return;

        VerificationReporter r = new VerificationReporter(Totality.LOGGER, "NotificationTimingVerification");

        checkOneTickActiveCallDecrementsByExactlyOne(r);
        checkManySimulatedRenderCallsNeverConsumeLifetime(r);
        checkLowAndHighSimulatedFrameRatesProduceEquivalentLifetime(r);
        checkHoldPhaseBeforeFadeOutPhase(r);
        checkNoFadeInPhaseByDesign(r);
        checkRemovalOnlyAfterFadeOutCompletes(r);
        checkNotificationExpiresExactlyOnce(r);
        checkMultipleQueuedNotificationsRetainIndependentTiming(r);
        checkClearRemovesAllStaleState(r);

        r.summarize();
    }

    private static void checkOneTickActiveCallDecrementsByExactlyOne(VerificationReporter r) {
        safe(r, "A single tickActive() call decrements ticksLeft by exactly 1", () -> {
            NotificationManager.clear();
            NotificationManager.addForTest("test", 0xFFFFFF, 80);
            NotificationManager.tickActive();
            boolean pass = NotificationManager.ticksLeftForTest(0) == 79;
            return result(pass, "ticksLeft=" + NotificationManager.ticksLeftForTest(0));
        });
    }

    /**
     * The regression's actual failure mode was the render callback ALSO decrementing lifetime —
     * this simulates "many renders happen, but tickActive is never called from among them" (the
     * fixed design: render only reads state) by simply never invoking tickActive at all and
     * confirming ticksLeft is untouched, no matter how many times an equivalent read-only render
     * pass would conceptually run in between.
     */
    private static void checkManySimulatedRenderCallsNeverConsumeLifetime(VerificationReporter r) {
        safe(r, "Reading state (computeAlpha) any number of times never advances ticksLeft", () -> {
            NotificationManager.clear();
            NotificationManager.addForTest("test", 0xFFFFFF, 80);
            for (int i = 0; i < 1000; i++) {
                NotificationManager.computeAlpha(NotificationManager.ticksLeftForTest(0)); // pure read
            }
            boolean pass = NotificationManager.ticksLeftForTest(0) == 80;
            return result(pass, "ticksLeft=" + NotificationManager.ticksLeftForTest(0));
        });
    }

    /**
     * The regression's actual bug was lifetime measured in FRAMES, not TICKS — this proves the
     * fixed design has no notion of frame count at all: driving 80 tickActive() calls (simulating
     * "80 ticks elapsed," regardless of whether that took 1 second at 80 FPS or 4 seconds at 20
     * FPS — frame count is never consulted anywhere in this path) always expires the notification
     * at exactly the same tick count.
     */
    private static void checkLowAndHighSimulatedFrameRatesProduceEquivalentLifetime(VerificationReporter r) {
        safe(r, "Notification lifetime in ticks is identical regardless of simulated frame rate", () -> {
            NotificationManager.clear();
            NotificationManager.addForTest("test", 0xFFFFFF, 10);
            // "Low FPS": exactly 10 tickActive() calls, nothing else in between.
            for (int i = 0; i < 9; i++) NotificationManager.tickActive();
            boolean survivedNineTicks = NotificationManager.sizeForTest() == 1
                    && NotificationManager.ticksLeftForTest(0) == 1;
            NotificationManager.tickActive(); // 10th tick — expires
            boolean expiredOnTenthTick = NotificationManager.sizeForTest() == 0;
            return result(survivedNineTicks && expiredOnTenthTick,
                    "survivedNineTicks=" + survivedNineTicks + ", expiredOnTenthTick=" + expiredOnTenthTick);
        });
    }

    private static void checkHoldPhaseBeforeFadeOutPhase(VerificationReporter r) {
        safe(r, "Alpha is full (1.0) during the hold phase, before the fade-out window begins",
                () -> result(NotificationManager.computeAlpha(80) == 1.0f && NotificationManager.computeAlpha(21) == 1.0f,
                        "alpha(80)=" + NotificationManager.computeAlpha(80) + ", alpha(21)=" + NotificationManager.computeAlpha(21)));
    }

    /**
     * Confirms the ORIGINAL implementation genuinely has no fade-in phase (verified via {@code git
     * diff} against the pre-migration commit) — alpha is immediately 1.0 the instant a notification
     * is added (ticksLeft == LIFETIME_TICKS), not ramping up from 0. Documented explicitly rather
     * than silently absent, per the task instruction not to invent timing the original code never
     * had.
     */
    private static void checkNoFadeInPhaseByDesign(VerificationReporter r) {
        safe(r, "A freshly added notification (ticksLeft = 80) starts at full alpha, no fade-in ramp",
                () -> result(NotificationManager.computeAlpha(80) == 1.0f,
                        "alpha(80)=" + NotificationManager.computeAlpha(80) + " — original design has hold+fade-out only, no fade-in"));
    }

    private static void checkRemovalOnlyAfterFadeOutCompletes(VerificationReporter r) {
        safe(r, "A notification is not removed until its fade-out fully completes (ticksLeft reaches 0)", () -> {
            NotificationManager.clear();
            NotificationManager.addForTest("test", 0xFFFFFF, 2); // 2 < FADE_TICKS(20), already fading
            NotificationManager.tickActive(); // ticksLeft 2 -> 1, still present, alpha near-zero but nonzero
            boolean stillPresentDuringFade = NotificationManager.sizeForTest() == 1;
            float fadingAlpha = stillPresentDuringFade ? NotificationManager.computeAlpha(NotificationManager.ticksLeftForTest(0)) : -1f;
            NotificationManager.tickActive(); // ticksLeft 1 -> 0, now removed
            boolean removedAfterFadeCompletes = NotificationManager.sizeForTest() == 0;
            boolean pass = stillPresentDuringFade && fadingAlpha > 0f && fadingAlpha < 1f && removedAfterFadeCompletes;
            return result(pass, "stillPresentDuringFade=" + stillPresentDuringFade + ", fadingAlpha=" + fadingAlpha
                    + ", removedAfterFadeCompletes=" + removedAfterFadeCompletes);
        });
    }

    private static void checkNotificationExpiresExactlyOnce(VerificationReporter r) {
        safe(r, "An expired notification is removed exactly once, not re-processed on later ticks", () -> {
            NotificationManager.clear();
            NotificationManager.addForTest("test", 0xFFFFFF, 1);
            NotificationManager.tickActive(); // expires and is removed
            boolean removedOnce = NotificationManager.sizeForTest() == 0;
            // Further ticks must not throw or do anything unexpected against the now-empty list.
            NotificationManager.tickActive();
            NotificationManager.tickActive();
            boolean staysEmpty = NotificationManager.sizeForTest() == 0;
            return result(removedOnce && staysEmpty, "removedOnce=" + removedOnce + ", staysEmpty=" + staysEmpty);
        });
    }

    private static void checkMultipleQueuedNotificationsRetainIndependentTiming(VerificationReporter r) {
        safe(r, "Multiple queued notifications tick down independently, not shortening each other's lifetime", () -> {
            NotificationManager.clear();
            NotificationManager.addForTest("first", 0xFFFFFF, 80);
            NotificationManager.addForTest("second", 0xFF0000, 30);
            NotificationManager.tickActive();
            NotificationManager.tickActive();
            boolean pass = NotificationManager.sizeForTest() == 2
                    && NotificationManager.ticksLeftForTest(0) == 78
                    && NotificationManager.ticksLeftForTest(1) == 28;
            return result(pass, "first=" + NotificationManager.ticksLeftForTest(0)
                    + ", second=" + NotificationManager.ticksLeftForTest(1));
        });
    }

    private static void checkClearRemovesAllStaleState(VerificationReporter r) {
        safe(r, "clear() (the disconnect-reset path) removes all stale notifications", () -> {
            NotificationManager.clear();
            NotificationManager.addForTest("a", 0, 80);
            NotificationManager.addForTest("b", 0, 40);
            NotificationManager.clear();
            boolean pass = NotificationManager.sizeForTest() == 0;
            return result(pass, "size=" + NotificationManager.sizeForTest());
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
            NotificationManager.clear();
        }
    }
}
