package zcylas.totality.api.rpg.rest;

import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.entity.rest.RestSeatEntity;
import zcylas.totality.networking.notification.SendNotificationPayload;
import zcylas.totality.networking.rest.RestTimeSyncPayload;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Drives the real-time wait timer behind Short/Long Rest. Actual resource
 * recovery ({@link RestManager}/{@link RestEventBus}) only fires when a
 * session genuinely completes — never on start.
 *
 * <p>Every lying-down rest (Nap, Long Rest — bed or outdoor, day or night) rides the exact same
 * real vanilla sleep state as an actual bed: {@link net.minecraft.world.entity.LivingEntity#startSleeping}
 * is public and unconditional (all the day/night/monster validity gating lives in
 * {@code Player#tick()}'s {@link BedRule} check instead, confirmed by decompiling 26.1.2 — Mojang's
 * own {@code Player#startSleepInBed} is now a trivial always-succeeds wrapper around it). Driving
 * the real thing means the lying-flat render, eye height, and the locked "look up at the bed"
 * sleep camera all come from vanilla's own code ({@code LivingEntityRenderer}, {@code Camera#setup},
 * {@code GameRenderer}, all keyed off {@code isSleeping()}) instead of an approximation — this is
 * what fixes the old fake-mount approach reading as "just sitting."
 *
 * <p>The one vanilla behavior that has to be deliberately suppressed is {@code Player#tick()}'s
 * own continuous validity check: it reads {@code isSleeping()} every tick (not just at entry) and
 * force-wakes the player the instant {@link BedRule#canSleep} goes false, e.g. the moment it's no
 * longer night — which is correct for a real spontaneous bed sleep but wrong for a Rest session
 * that's deliberately still in progress (a Nap, an outdoor Long Rest, a Long Rest at a bed during
 * the day). {@code PlayerRestSleepMixin} redirects only that one call site, so every other path to
 * {@code stopSleepInBed} — the sneak-to-get-up action, damage, disconnect — is untouched and still
 * fires {@link EntitySleepEvents#STOP_SLEEPING} normally, which is what actually interrupts the
 * session (see the listener below).
 *
 * <p>{@link RestSession#isRealVanillaSleep()} still matters, just for a narrower reason now: it
 * gates whether this session is allowed to complete early via vanilla's own automatic night-skip
 * consensus ({@link EntitySleepEvents#ALLOW_RESETTING_TIME}, always instant in singleplayer) versus
 * only ever completing via our own timer/{@link #checkLongRestConsensus}. Only a Long Rest at a
 * real bed while {@link BedRule#canSleep} is currently true qualifies — a Short Rest Nap must
 * never let a solo/singleplayer night-skip fire just because it happens to share the same real
 * {@code isSleeping()} state (see {@link zcylas.totality.networking.rest.RequestRestHandler}, which
 * computes this flag once at session start).
 *
 * <p>Seated Short Rest activities (Read/Meditate) are unrelated to any of this: they still ride an
 * invisible {@link RestSeatEntity} mount, the same trick the "Sit" mod uses for the leg-bend, and
 * never touch vanilla sleep state at all.
 */
public final class RestSessionManager {

    /** Matches vanilla's own Player#SLEEP_DURATION — how long a real sleep must hold before it's
     *  allowed to skip time, so the player actually sees themselves lie down first. */
    private static final int MIN_TICKS_BEFORE_LONG_REST_SKIP = 100;

    private static final Map<UUID, RestSession> SESSIONS = new HashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (SESSIONS.isEmpty()) return;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                RestSession session = SESSIONS.get(player.getUUID());
                if (session == null) continue;

                if (session.isInGrace()) {
                    if (server.getTickCount() >= session.getGraceDeadlineTick()) {
                        SESSIONS.remove(player.getUUID());
                        sendSync(player, null);
                    }
                    continue;
                }

                if (session.getSeatEntity() != null) {
                    // Getting up (sneaking off the mount) cancels the rest outright — no grace
                    // window, unlike a damage interrupt. Doesn't burn a Short Rest charge either
                    // way, since RestManager only counts completions. Seated activities only —
                    // lying-down sessions ride real vanilla sleep instead (see class doc), where
                    // "getting up" is vanilla's own sneak action firing STOP_SLEEPING below.
                    if (player.getVehicle() != session.getSeatEntity()) {
                        cancel(player);
                        SendNotificationPayload.send(player, "Rest cancelled — you got up.", SendNotificationPayload.YELLOW);
                        continue;
                    }
                } else if (session.getType() == RestType.LONG && !session.isRealVanillaSleep()) {
                    if (checkLongRestConsensus(player)) {
                        continue; // this session was just completed by the consensus check
                    }
                }

                session.tick();
                if (session.isComplete()) {
                    complete(player, session);
                } else if (session.getElapsedTicks() % 20 == 0) {
                    // Throttle the countdown sync to once a second.
                    sendSync(player, session);
                }
            }
        });

        // Taking damage interrupts an in-progress rest (short of the grace window).
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, killed) -> {
            if (damageTaken <= 0) return;
            if (!(entity instanceof ServerPlayer player)) return;
            interrupt(player);
        });

        // Real vanilla sleep resolution for bed-triggered Long Rest: ALLOW_RESETTING_TIME fires
        // exactly when vanilla has decided enough players are asleep to skip the night (instant
        // with a single player, i.e. singleplayer) — that IS a genuine completed Long Rest, so
        // complete immediately instead of waiting on our timer. Every OTHER session type now also
        // rides real isSleeping() (see class doc), so this has to actively veto them here — a
        // Short Rest Nap or an outdoor/daytime Long Rest must only ever complete via our own timer/
        // checkLongRestConsensus, never vanilla's automatic night-skip consensus.
        EntitySleepEvents.ALLOW_RESETTING_TIME.register(player -> {
            if (!(player instanceof ServerPlayer serverPlayer)) return true;
            RestSession session = SESSIONS.get(serverPlayer.getUUID());
            if (session == null) return true; // not one of our sessions — a normal vanilla sleeper
            if (session.isInGrace() || !session.isRealVanillaSleep()) return false;
            complete(serverPlayer, session);
            return true;
        });

        // Anything that stops real vanilla sleep outside our own control flow — the sneak-to-get-up
        // action, vanilla's own damage-triggered wake, etc. Our own complete()/cancel() always remove
        // the session (or, for interrupt(), mark the grace window) BEFORE calling stopSleepInBed, so
        // by the time this fires from one of those it's already a no-op below.
        EntitySleepEvents.STOP_SLEEPING.register((entity, pos) -> {
            if (!(entity instanceof ServerPlayer player)) return;
            RestSession session = SESSIONS.get(player.getUUID());
            if (session == null || session.isInGrace()) return;
            interrupt(player);
        });
    }

    public static void start(ServerPlayer player, RestType type, int totalTicks, @Nullable BlockPos bedPos,
                              @Nullable ShortRestActivity activity, boolean realVanillaSleep) {
        RestSession existing = SESSIONS.get(player.getUUID());
        if (existing != null && existing.isInGrace()) {
            clearGraceAndResume(player, existing);
            return;
        }

        RestSession session = new RestSession(type, totalTicks, bedPos, activity, realVanillaSleep);
        SESSIONS.put(player.getUUID(), session);
        beginVisual(player, session);
        sendSync(player, session);
    }

    /** "Keep Resting" — resumes an interrupted rest still within its grace window. Unlike
     *  {@link #start}, takes no session parameters: the existing {@link RestSession} already
     *  has everything (type/bedPos/activity), so this just clears the grace flag and re-enters. */
    public static void resume(ServerPlayer player) {
        RestSession existing = SESSIONS.get(player.getUUID());
        if (existing == null || !existing.isInGrace()) return;
        clearGraceAndResume(player, existing);
    }

    private static void clearGraceAndResume(ServerPlayer player, RestSession session) {
        session.clearGrace();
        beginVisual(player, session);
        sendSync(player, session);
    }

    private static boolean isSeatedActivity(RestSession session) {
        return session.getType() == RestType.SHORT
                && session.getActivity() != null
                && session.getActivity() != ShortRestActivity.NAP;
    }

    /** Used by {@link zcylas.totality.mixin.PlayerRestSleepMixin} to suppress vanilla's own
     *  per-tick day/night validity check from force-waking a Rest session that's deliberately
     *  still in progress — see that mixin's comment and this class's doc. */
    public static boolean isSuppressingAutoWake(UUID playerId) {
        RestSession session = SESSIONS.get(playerId);
        return session != null && !session.isInGrace() && !isSeatedActivity(session);
    }

    /** Mirrors the exact {@link BedRule} check {@code Player#tick()} runs, computed once at
     *  session start (see {@link zcylas.totality.networking.rest.RequestRestHandler}) to decide
     *  whether a Long Rest at this bed qualifies for vanilla's own automatic night-skip. */
    public static boolean isBedRuleSatisfied(ServerPlayer player) {
        BedRule rule = player.level().environmentAttributes().getValue(EnvironmentAttributes.BED_RULE, player.position());
        return rule.canSleep(player.level());
    }

    /**
     * Mirrors vanilla's own "enough players sleeping" consensus for our fake Long Rests (no
     * bed, or a bed vanilla itself refused). Real vanilla sleep already gets this for free via
     * {@link EntitySleepEvents#ALLOW_RESETTING_TIME}; this covers everything that mechanism
     * can't see, most importantly the solo/singleplayer case an outdoor Long Rest should still
     * resolve on its own, same as a real bed does — just after the same minimum visible-sleep
     * window vanilla itself requires (see {@link #MIN_TICKS_BEFORE_LONG_REST_SKIP}).
     *
     * @return true if it completed {@code trigger}'s own session.
     */
    private static boolean checkLongRestConsensus(ServerPlayer trigger) {
        List<ServerPlayer> players = trigger.level().getServer().getPlayerList().getPlayers();
        if (players.isEmpty()) return false;

        int neededPercent = trigger.level().getGameRules().get(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
        List<RestSession> resting = players.stream()
                .map(p -> SESSIONS.get(p.getUUID()))
                .filter(s -> s != null && s.getType() == RestType.LONG && !s.isInGrace() && !s.isRealVanillaSleep()
                        && s.getElapsedTicks() >= MIN_TICKS_BEFORE_LONG_REST_SKIP)
                .toList();
        if (resting.isEmpty() || resting.size() * 100L < (long) neededPercent * players.size()) return false;

        boolean completedTrigger = false;
        for (ServerPlayer p : players) {
            RestSession session = SESSIONS.get(p.getUUID());
            if (session != null && resting.contains(session)) {
                if (p == trigger) completedTrigger = true;
                complete(p, session);
            }
        }
        return completedTrigger;
    }

    public static void interrupt(ServerPlayer player) {
        RestSession session = SESSIONS.get(player.getUUID());
        if (session == null || session.isInGrace()) return;
        // Mark the grace window BEFORE tearing down the visual: stopSleepInBed() below fires
        // EntitySleepEvents.STOP_SLEEPING synchronously, which re-enters this exact method —
        // isInGrace() has to already be true at that point or it recurses forever (stack overflow).
        session.enterGrace(player.level().getServer().getTickCount() + RestDurations.GRACE_WINDOW_TICKS);
        endVisual(player, session, true);
        sendSync(player, session);
    }

    /** Hard-cancel with no grace window — used by the popup's Cancel button and auto-cancel. */
    public static void cancel(ServerPlayer player) {
        RestSession session = SESSIONS.remove(player.getUUID());
        if (session == null) return;
        endVisual(player, session, true);
        sendSync(player, null);
    }

    /**
     * Disconnect cleanup. Unlike {@link #cancel}, never sends a sync (the player's gone) — but
     * critically still has to call {@link #endVisual} first: for a seated activity, leaving a
     * session in {@code SESSIONS} just removed from the map, without ever discarding the
     * {@link RestSeatEntity} mount or calling stopRiding(), orphans the mount entity in the world —
     * vanilla's own entity save/load then reconstructs the passenger relationship from the mount's
     * saved NBT on the next login, rejoining the player still mounted. A lying-down session doesn't
     * have this problem (no mount involved), but still needs its real sleep state cleared here.
     */
    public static void clearPlayer(ServerPlayer player) {
        RestSession session = SESSIONS.remove(player.getUUID());
        if (session == null) return;
        endVisual(player, session, true);
    }

    @Nullable
    public static RestSession getSession(UUID playerId) {
        return SESSIONS.get(playerId);
    }

    private static void complete(ServerPlayer player, RestSession session) {
        SESSIONS.remove(player.getUUID());
        endVisual(player, session, false);
        if (session.getType() == RestType.SHORT) {
            RestManager.shortRest(player);
        } else {
            RestManager.longRest(player);
            if (!session.isRealVanillaSleep()) {
                // A genuine vanilla sleep jumps the clock to the next "wake up" marker itself
                // before ALLOW_RESETTING_TIME fires — but that's vanilla's own sleep-anytime-
                // still-lands-at-dawn behavior, not what a D&D-style Long Rest should do: it's a
                // fixed-duration recovery period regardless of when it started (resting at 5am
                // then again at 7am the next day should cost 8 hours each time, not ~22 the
                // second time from snapping to the same fixed marker). So for our own fake Long
                // Rests we just add the session's own duration directly instead.
                advanceTimeBySessionDuration(player, session);
            }
        }
        sendSync(player, null);
    }

    /** Adds this Long Rest's own duration to the world clock — see complete()'s comment for why. */
    private static void advanceTimeBySessionDuration(ServerPlayer player, RestSession session) {
        ServerLevel level = player.level();
        if (!level.getGameRules().get(GameRules.ADVANCE_TIME)) return;
        level.dimensionType().defaultClock().ifPresent(clock ->
                level.getServer().clockManager().addTicks(clock, session.getTotalTicks()));
        if (level.getGameRules().get(GameRules.ADVANCE_WEATHER) && level.isRaining()) {
            level.resetWeatherCycle();
        }
    }

    // ── Visuals (sleep pose / seated pose) ──────────────────────────────────────

    private static void beginVisual(ServerPlayer player, RestSession session) {
        if (isSeatedActivity(session)) {
            Vec3 seatPos = session.getBedPos() != null
                    ? Vec3.atBottomCenterOf(session.getBedPos()).add(0, 0.5, 0) // mattress height
                    : player.position();
            RestSeatEntity seat = new RestSeatEntity(player.level(), seatPos);
            player.level().addFreshEntity(seat);
            player.startRiding(seat);
            session.setSeatEntity(seat);
            return;
        }

        // Lying down (Nap, Long Rest — bed or outdoor, day or night): real vanilla sleep. No
        // manual position/pose math needed — LivingEntity#startSleeping snaps position (via its
        // own setPosToBed) and sets Pose.SLEEPING itself; a synthetic anchor at the player's own
        // feet stands in for a bed when resting outdoors.
        BlockPos anchor = session.getBedPos() != null ? session.getBedPos() : BlockPos.containing(player.position());
        if (session.getBedPos() != null) {
            Direction facing = BedBlock.getBedOrientation(player.level(), session.getBedPos());
            if (facing != null) {
                player.setYRot(facing.toYRot());
                player.setXRot(0);
            }
        }
        player.startSleeping(anchor);
    }

    private static void endVisual(ServerPlayer player, RestSession session, boolean wakeImmediately) {
        if (session.getSeatEntity() != null) {
            player.stopRiding();
            session.getSeatEntity().discard();
            session.setSeatEntity(null);
            return;
        }
        if (player.isSleeping()) {
            player.stopSleepInBed(wakeImmediately, true);
        }
    }

    // ── Client sync ──────────────────────────────────────────────────────────

    private static void sendSync(ServerPlayer player, @Nullable RestSession session) {
        RestTimeSyncPayload payload;
        if (session == null) {
            payload = RestTimeSyncPayload.cleared();
        } else if (session.isInGrace()) {
            int currentTick = player.level().getServer().getTickCount();
            int graceRemaining = Math.max(0, session.getGraceDeadlineTick() - currentTick);
            payload = new RestTimeSyncPayload(true, true, session.getType(), session.getRemainingTicks(), graceRemaining);
        } else {
            payload = new RestTimeSyncPayload(true, false, session.getType(), session.getRemainingTicks(), 0);
        }
        ServerPlayNetworking.send(player, payload);
    }

    private RestSessionManager() {}
}
