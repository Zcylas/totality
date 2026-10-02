package zcylas.totality.client.vfx.screen;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The Shared Screen FX merge rules (VFX Experiment 3, phase B1). */
class ScreenFxMixerTest {

    private static final Vec3 CAM = Vec3.ZERO;
    private static final Vec3 LOOK = new Vec3(0, 0, 1);
    private static final UUID ME = UUID.randomUUID();
    private static final ScreenFxEnvelope HOLD = new ScreenFxEnvelope(0, 1.0, 0);
    private static final ScreenFxFalloff FALLOFF = new ScreenFxFalloff(6, 40);

    private static ScreenFxRequest shake(Vec3 at, float i) {
        return ScreenFxRequest.at(ScreenFxChannel.SHAKE, at, i, HOLD, FALLOFF, 0, null);
    }

    private static ScreenFxRequest flash(Vec3 at, float i, ScreenFxEnvelope env) {
        return ScreenFxRequest.at(ScreenFxChannel.FLASH, at, i, env, new ScreenFxFalloff(8, 48), 0xFFFFFF, null);
    }

    private static ScreenFxFrame at(ScreenFxMixer m, double now) {
        return m.resolve(now, CAM, LOOK, ME, 1, 1);
    }

    @Test
    void envelopeAndFalloffAreTimeAndDistanceBased() {
        ScreenFxEnvelope e = new ScreenFxEnvelope(0.1, 0.2, 0.3);
        assertEquals(0.5, e.value(0.05), 1e-9);
        assertEquals(1.0, e.value(0.2), 1e-9);
        assertEquals(0.0, e.value(0.61), 1e-9);
        assertEquals(0.6, e.duration(), 1e-9);
        assertEquals(1.0, FALLOFF.factor(3), 1e-9);
        assertEquals(0.0, FALLOFF.factor(40), 1e-9);
        assertTrue(FALLOFF.factor(20) > 0 && FALLOFF.factor(20) < 1);
        assertEquals(1.0, ScreenFxFalloff.NONE.factor(1e6), 1e-9);
    }

    @Test
    void oneRequestPassesThrough() {
        ScreenFxMixer m = new ScreenFxMixer();
        m.add(shake(new Vec3(0, 0, 4), 0.45f), 0);
        assertEquals(0.45, at(m, 0.5).shake(), 1e-6);
    }

    @Test
    void twentySimultaneousNeverExceed135PercentOfTheStrongest() {
        ScreenFxMixer m = new ScreenFxMixer();
        for (int i = 0; i < 20; i++) m.add(shake(new Vec3(0, 0, 4), 0.45f), 0);
        ScreenFxFrame f = at(m, 0.5);
        assertEquals(0.45 * ScreenFxMixer.MAX_MERGE_FACTOR, f.shake(), 1e-6);
        assertEquals(0.45, f.strongestShake(), 1e-6);
        assertEquals(20, f.activeShake());
        // spread: still bounded by 1.35 x the strongest
        ScreenFxMixer spread = new ScreenFxMixer();
        for (int i = 0; i < 20; i++) spread.add(shake(new Vec3(0, 0, 4 + i * 1.9), 0.45f), 0);
        ScreenFxFrame g = at(spread, 0.5);
        assertTrue(g.shake() <= ScreenFxMixer.MAX_MERGE_FACTOR * g.strongestShake() + 1e-9);
    }

    @Test
    void theHardCapLimitsStrongRequests() {
        ScreenFxMixer m = new ScreenFxMixer();
        for (int i = 0; i < 5; i++) m.add(shake(new Vec3(0, 0, 1), 1.0f), 0);
        assertEquals(ScreenFxMixer.CHANNEL_CAP, at(m, 0.5).shake(), 1e-9);
    }

    @Test
    void higherPrioritySuppressesLowerUntilCancelled() {
        ScreenFxMixer m = new ScreenFxMixer();
        Object owner = new Object();
        m.add(shake(new Vec3(0, 0, 3), 0.5f), 0);
        m.add(ScreenFxRequest.at(ScreenFxChannel.SHAKE, new Vec3(0, 0, 3), 0.2f, HOLD, FALLOFF, 0, owner)
                .withPriority(ScreenFxPriority.CINEMATIC), 0);
        assertEquals(0.2, at(m, 0.1).shake(), 1e-6);
        assertEquals(1, m.cancelOwner(owner));
        assertEquals(0.5, at(m, 0.2).shake(), 1e-6);
    }

    @Test
    void distanceAttenuatesAndFarRequestsAreSilent() {
        ScreenFxMixer m = new ScreenFxMixer();
        m.add(shake(new Vec3(0, 0, 50), 0.45f), 0);
        assertEquals(0.0, at(m, 0.5).shake(), 1e-9);
        ScreenFxMixer n = new ScreenFxMixer();
        n.add(shake(new Vec3(0, 0, 25), 0.45f), 0);
        double v = at(n, 0.5).shake();
        assertTrue(v > 0 && v < 0.45);
    }

    @Test
    void flashBehindTheCameraIsReduced() {
        ScreenFxMixer m = new ScreenFxMixer();
        m.add(flash(new Vec3(0, 0, -5), 0.5f, HOLD), 0);
        assertEquals(0.5 * ScreenFxMixer.BEHIND_CAMERA_FLASH, at(m, 0.0).strongestFlash(), 1e-6);
    }

    @Test
    void audienceSubjectOnlyReachesOnlyTheSubject() {
        ScreenFxMixer m = new ScreenFxMixer();
        m.add(new ScreenFxRequest(ScreenFxChannel.SHAKE, null, 0.5f, HOLD, ScreenFxFalloff.NONE, ScreenFxPriority.NORMAL,
                ScreenFxAudience.SUBJECT_ONLY, UUID.randomUUID(), 0, null), 0);
        assertEquals(0.0, at(m, 0.5).shake(), 1e-9);
        ScreenFxMixer n = new ScreenFxMixer();
        n.add(new ScreenFxRequest(ScreenFxChannel.SHAKE, null, 0.5f, HOLD, ScreenFxFalloff.NONE, ScreenFxPriority.NORMAL,
                ScreenFxAudience.SUBJECT_ONLY, ME, 0, null), 0);
        assertEquals(0.5, at(n, 0.5).shake(), 1e-6);
    }

    @Test
    void strobingFlashesAreLimitedInEnergyAndOnsets() {
        ScreenFxMixer m = new ScreenFxMixer();
        ScreenFxEnvelope env = new ScreenFxEnvelope(0.0, 0.03, 0.15);
        double energy = 0, dt = 1.0 / 60;
        int onsets0 = m.flashOnsets();
        for (int frame = 0; frame <= 600; frame++) {          // 10 s at 60 fps, a 0.8 flash every 0.1 s
            double now = frame * dt;
            if (frame % 6 == 0) m.add(flash(new Vec3(0, 0, 4), 0.8f, env), now);
            double shown = at(m, now).flash();
            if (frame > 0) energy += shown * dt;      // the mixer charges each frame for the time since the last one
        }
        int onsets = m.flashOnsets() - onsets0;
        assertTrue(onsets <= 31, "at most 3 onsets per second, got " + onsets);
        double budget = ScreenFxMixer.FLASH_BUDGET_SECONDS + ScreenFxMixer.FLASH_REFILL_PER_SECOND * 10.0;
        assertTrue(energy <= budget + 1e-6, "energy " + energy + " <= " + budget);
    }

    @Test
    void oneFlashIsNotLimited() {
        ScreenFxMixer m = new ScreenFxMixer();
        m.add(flash(new Vec3(0, 0, 4), 0.5f, new ScreenFxEnvelope(0.0, 0.03, 0.15)), 0);
        at(m, 0.0);
        assertEquals(0.5, at(m, 0.016).flash(), 1e-6);
    }

    @Test
    void accessibilityScalesAndDisables() {
        ScreenFxMixer m = new ScreenFxMixer();
        m.add(shake(new Vec3(0, 0, 4), 0.4f), 0);
        m.add(flash(new Vec3(0, 0, 4), 0.4f, HOLD), 0);
        ScreenFxFrame half = m.resolve(0.1, CAM, LOOK, ME, 0.5, 0.5);
        assertEquals(0.2, half.shake(), 1e-6);
        assertEquals(0.2, half.flashDemand(), 1e-6);
        ScreenFxFrame off = m.resolve(0.2, CAM, LOOK, ME, 0, 0);
        assertEquals(0, off.shake(), 1e-9);
        assertEquals(0, off.flash(), 1e-9);
        ScreenFxSettings s = new ScreenFxSettings(null);
        assertEquals(0.0, s.shakeScale(0.0), 1e-9, "Distortion Effects 0 disables shake");
        assertEquals(0.0, s.flashScale(true), 1e-9, "Hide Lightning Flashes disables flashes");
        assertFalse(s.impactFrames(), "impact frames off by default");
    }

    @Test
    void requestsExpireAndCanBeCancelled() {
        ScreenFxMixer m = new ScreenFxMixer();
        long h = m.add(shake(new Vec3(0, 0, 4), 0.4f), 0);
        assertTrue(m.cancel(h));
        assertEquals(0, m.liveRequests());
        m.add(ScreenFxRequest.at(ScreenFxChannel.SHAKE, new Vec3(0, 0, 4), 0.4f, new ScreenFxEnvelope(0, 0.1, 0.1), FALLOFF, 0, null), 0);
        at(m, 0.5);
        assertEquals(0, m.liveRequests());
        assertEquals(0, m.last().shake(), 1e-9);
    }

    @Test
    void resolvingTwiceAtTheSameTimeIsIdempotent() {
        ScreenFxMixer m = new ScreenFxMixer();
        m.add(flash(new Vec3(0, 0, 4), 0.8f, HOLD), 0);
        at(m, 0.0);
        ScreenFxFrame a = at(m, 0.1);
        double tokens = m.flashTokens();
        assertSame(a, at(m, 0.1));
        assertEquals(tokens, m.flashTokens(), 1e-12);
    }
}
