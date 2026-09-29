package zcylas.totality.entity.animal;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.entity.animal.ForestBoarEntity.Behavior;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static zcylas.totality.entity.animal.ForestBoarRules.*;

/**
 * The Forest Boar's whole server-side behaviour as one explicit state machine (no competing goals):
 * <pre>
 *   WANDER ⇄ GRAZE / SNIFF          peaceful: strolls, pauses and looks around, grazes, sniffs
 *     │ player detected (or a landed hit)
 *   ALERT                           freezes and faces the player, then picks one reaction:
 *     ├─ FLEE                         runs away at once, or
 *     └─ CHARGE_WINDUP → CHARGE → FLEE  a single defensive rush, then runs away (hit or miss)
 *   FLEE → silently removed once it has fled 5 s, every player is 48+ blocks away and none can see it
 * </pre>
 * A boar charges at most once, never pursues, and never returns from fleeing to attacking.
 */
final class ForestBoarBrain {

    private final ForestBoarEntity boar;
    private int ticksInState;
    private int timer;
    private float chargeChance;
    private boolean hasCharged;
    @Nullable private LivingEntity threat;
    private Vec3 threatPos = Vec3.ZERO;
    // charge
    private Vec3 chargeStart = Vec3.ZERO;
    private Vec3 chargeDir = Vec3.ZERO;
    private double chargePlanned;
    private int ticksWithoutProgress;
    private Vec3 lastPos = Vec3.ZERO;
    private final Set<UUID> hitThisCharge = new HashSet<>();
    // flee
    private int ticksSinceNearPlayer;
    // development/test hooks and results (read by the verification and capture code)
    @Nullable private Boolean forcedCharge;
    @Nullable String lastChargeResult;
    @Nullable private String contactNote;
    boolean escaped;
    /** The escape decision's inputs at the moment it was taken (for the verification logs). */
    @Nullable String escapeRecord;
    /** Development capture only: the brain is paused and the behaviour is set from outside (animation footage). */
    boolean scripted;

    ForestBoarBrain(ForestBoarEntity boar) {
        this.boar = boar;
        this.timer = PAUSE_MIN_TICKS;
    }

    int ticksInState() { return ticksInState; }
    boolean hasCharged() { return hasCharged; }
    void forceNextReaction(@Nullable Boolean charge) { this.forcedCharge = charge; }

    void script(@Nullable Behavior behavior) {
        scripted = behavior != null;
        enter(behavior != null ? behavior : Behavior.WANDER);
    }

    private void enter(Behavior next) {
        boar.setBehavior(next);
        ticksInState = 0;
    }

    void tick(ServerLevel level) {
        ticksInState++;
        if (scripted) return;
        Behavior b = boar.getBehavior();
        if (b.peaceful() && boar.tickCount % DETECTION_INTERVAL_TICKS == 0) {
            Player seen = detect(level);
            if (seen != null) {
                startle(seen, ALERT_TICKS, CHARGE_CHANCE);
                return;
            }
        }
        switch (b) {
            case WANDER -> wander();
            case GRAZE, SNIFF -> {
                boar.getNavigation().stop();
                if (--timer <= 0) {
                    timer = Mth.nextInt(boar.getRandom(), PAUSE_MIN_TICKS / 2, PAUSE_MAX_TICKS / 2);
                    enter(Behavior.WANDER);
                }
            }
            case ALERT -> {
                if (threat != null && threat.isAlive()) threatPos = threat.position();
                holdFacing(threatPos, 25.0F);
                if (ticksInState >= timer) react();
            }
            case CHARGE_WINDUP -> windup();
            case CHARGE -> charge(level);
            case FLEE -> flee(level);
        }
    }

    // ── peaceful ──

    private void wander() {
        if (!boar.getNavigation().isDone()) return;           // strolling
        if (boar.getRandom().nextInt(40) == 0) {             // look around while standing
            float yaw = boar.getYRot() + (boar.getRandom().nextFloat() - 0.5F) * 160.0F;
            Vec3 look = boar.getEyePosition().add(-Mth.sin(yaw * Mth.DEG_TO_RAD) * 4, (boar.getRandom().nextFloat() - 0.6F) * 2, Mth.cos(yaw * Mth.DEG_TO_RAD) * 4);
            boar.getLookControl().setLookAt(look.x, look.y, look.z, 10.0F, 20.0F);
        }
        if (--timer > 0) return;
        int roll = boar.getRandom().nextInt(STROLL_ODDS + GRAZE_ODDS + SNIFF_ODDS);
        if (roll < STROLL_ODDS) {
            Vec3 to = LandRandomPos.getPos(boar, WANDER_RANGE, 7);
            if (to != null) boar.getNavigation().moveTo(to.x, to.y, to.z, WANDER_SPEED);
            timer = Mth.nextInt(boar.getRandom(), PAUSE_MIN_TICKS, PAUSE_MAX_TICKS);
        } else if (roll < STROLL_ODDS + GRAZE_ODDS) {
            timer = Mth.nextInt(boar.getRandom(), GRAZE_MIN_TICKS, GRAZE_MAX_TICKS);
            enter(Behavior.GRAZE);
        } else {
            timer = SNIFF_TICKS;
            enter(Behavior.SNIFF);
        }
    }

    /** The nearest detectable player (survival/adventure; spectators and creative players are ignored). */
    @Nullable
    private Player detect(ServerLevel level) {
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player p : level.players()) {
            if (!validThreat(p)) continue;
            double d = boar.distanceTo(p);
            if (d > SIGHT_RADIUS || d >= bestDist) continue;
            double bearing = Mth.wrapDegrees(yawTo(p.position()) - boar.yBodyRot);
            boolean near = d <= PROXIMITY_RADIUS;
            if (ForestBoarRules.detects(d, bearing, !near && boar.hasLineOfSight(p))) {
                best = p;
                bestDist = d;
            }
        }
        return best;
    }

    private static boolean validThreat(@Nullable LivingEntity e) {
        return e instanceof Player p && p.isAlive() && !p.isSpectator() && !p.isCreative();
    }

    // ── startle and reaction ──

    /** A player was detected or a hit landed: freeze, face it, then react. */
    private void startle(@Nullable LivingEntity by, int alertTicks, float chance) {
        threat = by;
        if (by != null) threatPos = by.position();
        chargeChance = chance;
        timer = alertTicks;
        boar.getNavigation().stop();
        enter(Behavior.ALERT);
    }

    void onHurt(@Nullable Entity attacker) {
        if (scripted || !(attacker instanceof LivingEntity by)) return;    // falls, cactus etc. do not startle it
        Behavior b = boar.getBehavior();
        if (b.peaceful()) {
            startle(by, HURT_ALERT_TICKS, validThreat(by) ? HURT_CHARGE_CHANCE : 0.0F);
        } else if (b == Behavior.FLEE) {
            threat = by;                                        // keep running, now away from the attacker
            threatPos = by.position();
            timer = 0;
        }
        // ALERT reacts shortly anyway; a wind-up or rush in progress carries on to its end.
    }

    private void react() {
        boolean chargeable = validThreat(threat) && threat.level() == boar.level() && canCharge(threat);
        float roll = forcedCharge == null ? boar.getRandom().nextFloat() : (forcedCharge ? -1.0F : 2.0F);
        forcedCharge = null;
        if (ForestBoarRules.chooseCharge(hasCharged, chargeable, roll, chargeChance)) {
            enter(Behavior.CHARGE_WINDUP);
        } else {
            startFlee();
        }
    }

    private boolean canCharge(LivingEntity target) {
        double dx = target.getX() - boar.getX(), dz = target.getZ() - boar.getZ();
        return ForestBoarRules.chargeable(Math.sqrt(dx * dx + dz * dz), target.getY() - boar.getY(), boar.hasLineOfSight(target));
    }

    // ── charge ──

    private void windup() {
        boar.getNavigation().stop();
        if (!validThreat(threat) || threat.level() != boar.level()) {
            startFlee();
            return;
        }
        threatPos = threat.position();
        holdFacing(threatPos, 18.0F);
        if (ticksInState < WINDUP_TICKS) return;
        if (!canCharge(threat)) {                              // moved out of reach or behind cover during the wind-up
            lastChargeResult = "abandoned (no valid target)";
            startFlee();
            return;
        }
        Vec3 flat = new Vec3(threat.getX() - boar.getX(), 0, threat.getZ() - boar.getZ());
        chargeStart = boar.position();
        chargeDir = flat.normalize();
        chargePlanned = ForestBoarRules.chargeDistance(flat.length());
        hitThisCharge.clear();
        ticksWithoutProgress = 0;
        lastPos = boar.position();
        hasCharged = true;
        enter(Behavior.CHARGE);
    }

    private void charge(ServerLevel level) {
        Vec3 end = chargeStart.add(chargeDir.scale(chargePlanned));
        boar.getMoveControl().setWantedPosition(end.x, boar.getY(), end.z, CHARGE_SPEED);
        float yaw = (float) (Mth.atan2(chargeDir.z, chargeDir.x) * Mth.RAD_TO_DEG) - 90.0F;
        boar.setYRot(yaw);
        boar.yBodyRot = yaw;
        boar.yHeadRot = yaw;

        boolean contact = false;
        // Contact: a player touching the boar's visible body (its turned torso and lowered head, ForestBoarBody),
        // with a small reach for the tusk tips.
        var body = ForestBoarBody.regions(boar);
        AABB near = boar.getBoundingBox().inflate(1.5, 0.0, 1.5);
        for (Player p : level.getEntitiesOfClass(Player.class, near,
                p -> validThreat(p) && ForestBoarBody.touches(body, p.getBoundingBox(), CHARGE_CONTACT_REACH))) {
            if (hitThisCharge.add(p.getUUID())) {              // one hit per player per rush
                float before = p.getHealth();
                boar.doHurtTarget(level, p);                    // Totality routes this (attack roll vs AC, blocking, damage text)
                contactNote = String.format(java.util.Locale.ROOT, "%s health %.1f -> %.1f", p.getName().getString(), before, p.getHealth());
                contact = true;
            }
        }
        Vec3 now = boar.position();
        double moved = now.subtract(lastPos).horizontalDistance();
        lastPos = now;
        ticksWithoutProgress = ticksInState > 4 && moved < CHARGE_MIN_PROGRESS_PER_TICK ? ticksWithoutProgress + 1 : 0;
        String end_ = ForestBoarRules.chargeEnd(contact, now.subtract(chargeStart).horizontalDistance(), chargePlanned,
                ticksInState, ticksWithoutProgress);
        if (end_ != null) {
            lastChargeResult = contact ? end_ + " (" + contactNote + ")" : end_;
            startFlee();
        }
    }

    // ── flee and escape ──

    private void startFlee() {
        if (threat != null) threatPos = threat.position();
        timer = 0;
        ticksSinceNearPlayer = 0;
        enter(Behavior.FLEE);
    }

    private void flee(ServerLevel level) {
        double nearest = Double.POSITIVE_INFINITY;
        Vec3 away = Vec3.ZERO;
        int n = 0;
        for (Player p : level.players()) {
            double d = boar.distanceTo(p);
            nearest = Math.min(nearest, d);
            if (d <= FLEE_THREAT_RADIUS && !p.isSpectator()) {
                away = away.add(p.position());
                n++;
            }
        }
        if (n > 0) threatPos = away.scale(1.0 / n);
        ticksSinceNearPlayer = nearest <= FLEE_CALM_PLAYER_DISTANCE ? 0 : ticksSinceNearPlayer + 1;

        if (ticksInState % ESCAPE_CHECK_INTERVAL_TICKS == 0 && !boar.keepsThroughEscape()
                && ForestBoarRules.mayEscape(true, ticksInState, nearest, anyPlayerCanSee(level))) {
            escaped = true;
            escapeRecord = String.format(java.util.Locale.ROOT, "fled %d ticks; nearest player %.1f blocks; seen by any player: no",
                    ticksInState, nearest);
            boar.escape();
            return;
        }
        if (ticksInState >= FLEE_CALM_TICKS && ticksSinceNearPlayer >= 200) {   // lost them without escaping: settle down
            threat = null;
            timer = PAUSE_MIN_TICKS;
            enter(Behavior.WANDER);
            return;
        }
        if (--timer <= 0 || boar.getNavigation().isDone()) {
            timer = FLEE_REPATH_TICKS;
            Vec3 to = LandRandomPos.getPosAway(boar, FLEE_PATH_DISTANCE, 7, threatPos);
            if (to == null) to = DefaultRandomPos.getPosAway(boar, FLEE_PATH_DISTANCE, 7, threatPos);
            if (to == null) to = LandRandomPos.getPos(boar, FLEE_PATH_DISTANCE, 7);
            if (to != null) boar.getNavigation().moveTo(to.x, to.y, to.z, FLEE_SPEED);
        }
    }

    /** Every player in the level is checked, not only the one that startled it (spectators and creative included). */
    private boolean anyPlayerCanSee(ServerLevel level) {
        for (Player p : level.players()) {
            if (p.hasLineOfSight(boar)) return true;
        }
        return false;
    }

    // ── helpers ──

    private float yawTo(Vec3 pos) {
        return (float) (Mth.atan2(pos.z - boar.getZ(), pos.x - boar.getX()) * Mth.RAD_TO_DEG) - 90.0F;
    }

    private void holdFacing(Vec3 pos, float maxTurn) {
        float yaw = Mth.approachDegrees(boar.getYRot(), yawTo(pos), maxTurn);
        boar.setYRot(yaw);
        boar.yBodyRot = yaw;
        boar.getLookControl().setLookAt(pos.x, pos.y + 1.2, pos.z, 30.0F, 30.0F);
    }
}
