package zcylas.totality.client.vfx.eldritch;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Eldritch Blast V2 beam timeline: anticipation, release, travelling lance, collapse, residue, cleanup. */
class EldritchBeamTrackTest {

    private static EldritchBeamTrack track(boolean castSeen, double headDistance) {
        EldritchBeamTrack t = new EldritchBeamTrack();
        t.castSeen = castSeen;
        t.castAt = 0.0;
        t.origin = Vec3.ZERO;
        t.head = new Vec3(0, 0, headDistance);
        return t;
    }

    @Test
    void aSeenCastShowsOnlyTheFlareDuringTheAnticipation() {
        EldritchBeamTrack t = track(true, 3.0);
        assertTrue(t.evaluate(0.03));
        assertEquals(0.0f, t.beamAlpha, "no beam before the release (the sound's in-drawn swell)");
        assertEquals(0.0f, t.headAlpha);
        assertTrue(t.flare > 0.0f && t.flare < 1.0f, "the flare coils in at the hand");
        assertFalse(t.released(0.03));
        assertTrue(t.released(EldritchBeamTrack.RELEASE_DELAY));
    }

    @Test
    void theReleaseIsWideAndSettles() {
        EldritchBeamTrack t = track(true, 5.0);
        t.evaluate(EldritchBeamTrack.RELEASE_DELAY);
        assertEquals(1.0f, t.beamAlpha);
        assertEquals(EldritchBeamTrack.RELEASE_WIDTH, t.width, 1e-9);
        t.evaluate(EldritchBeamTrack.RELEASE_DELAY + EldritchBeamTrack.RELEASE);
        assertEquals(1.0, t.width, 1e-9);
        t.evaluate(1.0);
        assertEquals(-1.0f, t.flare, "the flare is over");
    }

    @Test
    void theReleaseFlashFallsOffAsFastAsTheBeamContracts() {
        EldritchBeamTrack t = track(true, 5.0);
        t.evaluate(EldritchBeamTrack.RELEASE_DELAY);
        assertEquals(1.0f, t.releaseFlash, 1e-6f);
        assertEquals(0.0f, t.beamParam(), 1e-6f, "full flash");
        t.evaluate(EldritchBeamTrack.RELEASE_DELAY + EldritchBeamTrack.RELEASE / 2);
        assertTrue(t.releaseFlash < 0.3f && t.width < 1.0 + (EldritchBeamTrack.RELEASE_WIDTH - 1.0) * 0.15,
                "most of the width and light are gone half way through the release");
        t.evaluate(EldritchBeamTrack.RELEASE_DELAY + EldritchBeamTrack.RELEASE);
        assertEquals(0.0f, t.releaseFlash, 1e-6f);
        assertEquals(0.5f, t.beamParam(), 1e-6f, "the thin travelling beam: no flash, no collapse");
        t.removedAt = 1.0;
        t.evaluate(1.0 + EldritchBeamTrack.COLLAPSE / 2);
        assertTrue(t.beamParam() > 0.5f, "collapse is encoded above 0.5");
        EldritchBeamTrack unseen = track(false, 5.0);
        unseen.evaluate(0.0);
        assertEquals(0.0f, unseen.releaseFlash, "no release flash for a beam first seen mid-flight");
    }

    @Test
    void theTailFollowsTheHeadAfterTheHold() {
        EldritchBeamTrack t = track(true, 30.0);
        t.evaluate(EldritchBeamTrack.RELEASE_DELAY + EldritchBeamTrack.HOLD);
        assertEquals(0.0, t.tail.distanceTo(t.origin), 1e-9, "the whole beam is lit from the hand during the hold");
        t.evaluate(EldritchBeamTrack.RELEASE_DELAY + EldritchBeamTrack.HOLD + 0.2);
        assertEquals(0.2 * EldritchBeamTrack.TAIL_SPEED, t.tail.distanceTo(t.origin), 1e-6, "then it travels as a lance");
        t.evaluate(10.0);
        assertTrue(t.tail.distanceTo(t.origin) <= 30.0 + 1e-9, "the tail never passes the head");
    }

    @Test
    void aHitCollapsesTheBeamThenTheResidueFadesAndTheTrackEnds() {
        EldritchBeamTrack t = track(true, 14.0);
        t.removedAt = 0.3;
        assertTrue(t.evaluate(0.3));
        assertEquals(1.0f, t.beamAlpha);
        assertEquals(0.0f, t.headAlpha, "the impact replaces the head");
        assertTrue(t.evaluate(0.3 + EldritchBeamTrack.COLLAPSE / 2));
        assertTrue(t.collapse > 0.4f && t.beamAlpha < 1.0f);
        assertTrue(t.evaluate(0.3 + EldritchBeamTrack.COLLAPSE));
        assertEquals(0.0f, t.beamAlpha, 1e-6f);
        assertTrue(t.evaluate(0.3 + EldritchBeamTrack.RESIDUE_IN + 0.1));
        assertEquals(1.0f, t.residueAlpha, 1e-6f, "the dark residue holds along the path");
        double end = 0.3 + EldritchBeamTrack.RESIDUE_IN + EldritchBeamTrack.RESIDUE_HOLD + EldritchBeamTrack.RESIDUE_OUT;
        assertFalse(t.evaluate(end), "everything is gone after the residue");
    }

    @Test
    void anExpiryLeavesAFainterResidue() {
        EldritchBeamTrack t = track(true, 14.0);
        t.removedAt = 3.0;
        t.fizzled = true;
        t.evaluate(3.0 + EldritchBeamTrack.RESIDUE_IN + 0.1);
        assertEquals(0.5f, t.residueAlpha, 1e-6f);
    }

    @Test
    void aBeamFirstSeenMidFlightIsDrawnAtOnceWithoutFlare() {
        EldritchBeamTrack t = track(false, 10.0);
        assertTrue(t.evaluate(0.0));
        assertEquals(1.0f, t.beamAlpha);
        assertEquals(1.0, t.width, 1e-9);
        assertEquals(-1.0f, t.flare);
    }

    @Test
    void anUnresolvedTrackIsDroppedAfterTheBoltsLifetime() {
        EldritchBeamTrack t = track(true, 10.0);
        t.evaluate(EldritchBeamTrack.MAX_AGE + 0.1);
        assertTrue(t.removed());
        assertFalse(t.evaluate(EldritchBeamTrack.MAX_AGE + 5.0));
    }

    @Test
    void theHandSitsBelowAndBesideTheEyesAndAlternates() {
        Vec3 eye = new Vec3(0, 10, 0);
        Vec3 dir = new Vec3(0, 0, 1); // looking south: right hand is west (-x)
        Vec3 right = EldritchBeamTrack.hand(eye, dir, new double[]{0.5, 0.3, 0.3}, 1);
        Vec3 left = EldritchBeamTrack.hand(eye, dir, new double[]{0.5, 0.3, 0.3}, -1);
        assertEquals(-0.3, right.x, 1e-9);
        assertEquals(0.3, left.x, 1e-9);
        assertEquals(9.7, right.y, 1e-9);
        assertEquals(0.5, right.z, 1e-9);
        Vec3 up = EldritchBeamTrack.hand(eye, new Vec3(0, 1, 0), new double[]{0.5, 0.3, 0.3}, 1);
        assertTrue(Double.isFinite(up.x) && Double.isFinite(up.y), "looking straight up has a defined hand");
    }

    @Test
    void segmentDistance() {
        Vec3 a = Vec3.ZERO, b = new Vec3(0, 0, 10);
        assertEquals(2.0, EldritchBeamTrack.distanceToSegment(new Vec3(2, 0, 5), a, b), 1e-9);
        assertEquals(3.0, EldritchBeamTrack.distanceToSegment(new Vec3(0, 0, 13), a, b), 1e-9);
        assertEquals(1.0, EldritchBeamTrack.distanceToSegment(new Vec3(0, 1, 0), a, a), 1e-9);
    }

    @Test
    void shaderCodesSurviveEightBitColourAndDecodeLikeTheShader() {
        for (int mode = 0; mode <= 5; mode++) {
            for (int pal = 0; pal <= 1; pal++) {
                float b = Math.round(EldritchBeamTrack.code(mode, pal) * 255.0f) / 255.0f; // vertex colours are bytes
                int code = (int) Math.floor(b * 16.0f);
                assertEquals(mode, code / 2);
                assertEquals(pal, code - code / 2 * 2);
            }
        }
        assertNotEquals(EldritchBeamTrack.code(0, 0), EldritchBeamTrack.code(0, 1));
    }
}
