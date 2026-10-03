package zcylas.totality.client.vfx.eldritch;

import net.minecraft.world.phys.Vec3;
import zcylas.totality.entity.magic.SpellBoltEntity;

/**
 * One Eldritch Blast beam as drawn in a frame, from its release until its residue has faded (pure state and timing of
 * {@link EldritchBlastVfx}, unit-tested). The beam is the unchanged bolt's path drawn as light: it reaches from the
 * caster's hand to the bolt (the head), so it extends at the bolt's speed of 50 blocks a second.
 *
 * <ol>
 *   <li>Anticipation, {@link #RELEASE_DELAY}: only the cast flare coils in at the hand (the sound's in-drawn swell).</li>
 *   <li>Release: the beam appears at its full current length as a very bright discharge {@link #RELEASE_WIDTH} times
 *       wider, contracting within {@link #RELEASE} into a thin streak (BG3 reference: ~7:1 within one or two video frames),
 *       its flash ({@link #releaseFlash}) falling off as fast.</li>
 *   <li>Travel: after {@link #HOLD} the beam's tail follows the head at the bolt's speed, so a long shot travels as a
 *       short streak (about 6 blocks) instead of a continuous ray.</li>
 *   <li>Hit or expiry: the beam thins (halo first) and fades over {@link #COLLAPSE}, while a dark residue appears along
 *       the path and fades ({@link #RESIDUE_IN}, {@link #RESIDUE_HOLD}, {@link #RESIDUE_OUT}).</li>
 * </ol>
 * A beam first seen mid-flight (this client did not see the cast) has no flare, release or anticipation.
 */
final class EldritchBeamTrack {

    static final double RELEASE_DELAY = 0.075;
    static final double RELEASE = 0.09;
    static final double RELEASE_WIDTH = 6.0;
    static final double HOLD = 0.12;
    static final double TAIL_SPEED = 50.0;
    static final double COLLAPSE = 0.12;
    static final double RESIDUE_IN = 0.08;
    static final double RESIDUE_HOLD = 0.35;
    static final double RESIDUE_OUT = 0.75;
    static final double FLARE = 0.30;
    /** A bolt lives 3 s; a track still unresolved after this is treated as gone. */
    static final double MAX_AGE = 3.6;

    SpellBoltEntity entity;
    Vec3 origin = Vec3.ZERO;
    Vec3 head = Vec3.ZERO;
    Vec3 dir = new Vec3(0, 0, 1);
    boolean castSeen;
    double castAt;
    double removedAt = Double.NaN;
    /** The server's impact event reached this beam (its end is then the exact hit point). */
    boolean impactMatched;
    boolean fizzled;
    float seed;
    long frame;
    boolean releaseFired;
    // evaluated per frame
    Vec3 tail = Vec3.ZERO;
    double width;
    /** Release flash 1 -> 0 over {@link #RELEASE} (brighter, whiter core and glow). */
    float releaseFlash;
    float beamAlpha;
    float collapse;
    float headAlpha;
    float residueAlpha;
    /** Cast-flare progress 0..1, or -1 when no flare is drawn. */
    float flare;

    boolean removed() {
        return !Double.isNaN(removedAt);
    }

    /** True once the release moment has passed (the beam is drawn and the cast feedback fires). */
    boolean released(double now) {
        return !castSeen || now - castAt >= RELEASE_DELAY;
    }

    /** Everything drawn this frame; false when the track is finished (nothing left to draw, ever). */
    boolean evaluate(double now) {
        double age = now - castAt;
        if (!removed() && age > MAX_AGE) removedAt = now;
        boolean removed = removed();
        flare = castSeen && age < FLARE ? (float) Math.max(0.0, age / FLARE) : -1.0f;
        double lit = (removed ? Math.min(now, removedAt) : now) - castAt;
        Vec3 path = head.subtract(origin);
        double length = path.length();
        double tailDist = castSeen ? Math.clamp((lit - RELEASE_DELAY - HOLD) * TAIL_SPEED, 0.0, length) : 0.0;
        tail = length < 1.0e-6 ? origin : origin.add(path.scale(tailDist / length));
        double release = castSeen ? Math.clamp((age - RELEASE_DELAY) / RELEASE, 0.0, 1.0) : 1.0;
        double fall = (1.0 - release) * (1.0 - release);
        width = 1.0 + (RELEASE_WIDTH - 1.0) * fall * (1.0 - release);
        releaseFlash = castSeen && released(now) ? (float) fall : 0.0f;
        boolean shown = released(now);
        if (!removed) {
            collapse = 0.0f;
            beamAlpha = shown ? 1.0f : 0.0f;
            headAlpha = beamAlpha;
            residueAlpha = 0.0f;
            return true;
        }
        double since = now - removedAt;
        double k = Math.clamp(since / COLLAPSE, 0.0, 1.0);
        collapse = (float) k;
        beamAlpha = shown ? (float) (1.0 - k * k) : 0.0f;
        headAlpha = 0.0f;
        double in = Math.clamp(since / RESIDUE_IN, 0.0, 1.0);
        double out = Math.clamp((since - RESIDUE_IN - RESIDUE_HOLD) / RESIDUE_OUT, 0.0, 1.0);
        residueAlpha = shown ? (float) (in * in * (3.0 - 2.0 * in) * (1.0 - out * out * (3.0 - 2.0 * out)) * (fizzled ? 0.5 : 1.0)) : 0.0f;
        return since < RESIDUE_IN + RESIDUE_HOLD + RESIDUE_OUT;
    }

    /**
     * The beam's shader parameter (one byte): below 0.5 the release flash (0 = full flash, 0.5 = none), from 0.5 the
     * collapse (0.5 = none, 1 = gone). The two never overlap: a collapsing beam has no flash.
     */
    float beamParam() {
        return collapse > 0.0f ? 0.5f + 0.5f * collapse : 0.5f * (1.0f - releaseFlash);
    }

    // ── Pure helpers (unit-tested) ────────────────────────────────────────────

    /** Distance from {@code p} to the segment {@code a}..{@code b}. */
    static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double len2 = ab.lengthSqr();
        double k = len2 < 1.0e-9 ? 0.0 : Math.clamp(p.subtract(a).dot(ab) / len2, 0.0, 1.0);
        return p.distanceTo(a.add(ab.scale(k)));
    }

    /** The caster's hand for a beam fired along {@code dir}: {@code side} 1 = right hand, -1 = left. */
    static Vec3 hand(Vec3 eye, Vec3 dir, double[] offset, int side) {
        Vec3 right = dir.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1.0e-8 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(dir).normalize();
        return eye.add(dir.scale(offset[0])).add(right.scale(offset[1] * side)).subtract(up.scale(offset[2]));
    }

    /** Vertex colour blue channel: the shader's element mode and palette. */
    static float code(int mode, int pal) {
        return (mode * 2 + pal + 0.5f) / 16.0f;
    }
}
