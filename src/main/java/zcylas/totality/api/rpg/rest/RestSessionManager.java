package zcylas.totality.api.rpg.rest;

import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.entity.Pose;
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
 * Long Rest at a bed also rides real vanilla sleep ({@link EntitySleepEvents})
 * so that a natural night-skip (always instant in singleplayer) completes the
 * rest immediately instead of forcing the full 8-MC-hour wait; the tick timer
 * remains the fallback for when that doesn't happen.
 *
 * Everything else (Long Rest with no valid bed, Long Rest at a bed during the
 * day/near monsters, Short Rest) is entirely our own system, not vanilla's:
 * the player rides an invisible {@link RestSeatEntity} mount, the same trick
 * the "Sit" mod uses for Read/Meditate's leg-bend. For Nap/Long Rest we also
 * force {@code Pose.SLEEPING} on top for the lying-flat look — the client's
 * lying-flat render is driven purely by that pose (see
 * {@code LivingEntityRenderer}), not by {@code isSleeping()}, so it looks
 * identical to real sleep without ever touching {@code isSleeping()} itself.
 * That distinction matters: measured on 2026-07-09, vanilla runs a continuous
 * "is it currently valid to be asleep" check tied to the same day/night bed
 * rule that blocks {@code startSleepInBed} — it reads {@code isSleeping()}
 * every tick, not just at entry, and silently forces the player awake again
 * within the same tick during the day no matter how genuine the bed backing
 * it is. Riding the mount is also what makes this reliable rather than a bare
 * pose override: it fully owns the player's position (gravity doesn't apply,
 * nothing can accumulate drift), so "did they get up" is a simple, exact
 * check — did they stop riding — rather than a movement-distance heuristic.
 * The client also has to be told to force third-person view whenever we're
 * lying down (see {@link RestTimeSyncPayload#lyingDown()}): vanilla only
 * does that automatically for genuine {@code isSleeping()} sleep, and
 * Pose.SLEEPING's much shorter eye height looks broken in first person.
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
                    // way, since RestManager only counts completions.
                    if (player.getVehicle() != session.getSeatEntity()) {
                        cancel(player);
                        SendNotificationPayload.send(player, "Rest cancelled — you got up.", SendNotificationPayload.YELLOW);
                        continue;
                    }
                    if (!isSeatedActivity(session)) {
                        // Self-heal every tick in case anything else reverts the pose — riding
                        // itself doesn't touch Pose, so nothing normally fights this, but it's
                        // free insurance since we're already iterating active sessions.
                        if (player.getPose() != Pose.SLEEPING) {
                            player.setPose(Pose.SLEEPING);
                        }
                        if (session.getType() == RestType.LONG && checkLongRestConsensus(player)) {
                            continue; // this session was just completed by the consensus check
                        }
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

        // Real vanilla sleep resolution for bed-triggered Long Rest: ALLOW_RESETTING_TIME
        // fires exactly when vanilla has decided enough players are asleep to skip the
        // night (instant with a single player, i.e. singleplayer) — that IS a genuine
        // completed Long Rest, so complete immediately instead of waiting on our timer.
        EntitySleepEvents.ALLOW_RESETTING_TIME.register(player -> {
            if (player instanceof ServerPlayer serverPlayer) {
                RestSession session = SESSIONS.get(serverPlayer.getUUID());
                if (session != null && session.isRealVanillaSleep() && !session.isInGrace()) {
                    complete(serverPlayer, session);
                }
            }
            return true;
        });

        // Anything else that stops the player sleeping while a real vanilla-sleep session is
        // still present (manual exit, monster nearby, etc.) — completion above already removes
        // the session, so seeing one here means it wasn't a real night-skip.
        EntitySleepEvents.STOP_SLEEPING.register((entity, pos) -> {
            if (!(entity instanceof ServerPlayer player)) return;
            RestSession session = SESSIONS.get(player.getUUID());
            if (session == null || !session.isRealVanillaSleep()) return;
            interrupt(player);
        });
    }

    public static void start(ServerPlayer player, RestType type, int totalTicks, @Nullable BlockPos bedPos,
                              @Nullable ShortRestActivity activity, boolean realVanillaSleep) {
        RestSession existing = SESSIONS.get(player.getUUID());
        if (existing != null && existing.isInGrace()) {
            existing.clearGrace();
            beginVisual(player, existing);
            sendSync(player, existing);
            return;
        }

        RestSession session = new RestSession(type, totalTicks, bedPos, activity, realVanillaSleep);
        SESSIONS.put(player.getUUID(), session);
        beginVisual(player, session);
        sendSync(player, session);
    }

    private static boolean isSeatedActivity(RestSession session) {
        return session.getType() == RestType.SHORT
                && session.getActivity() != null
                && session.getActivity() != ShortRestActivity.NAP;
    }

    /** Used by {@link zcylas.totality.mixin.PlayerRestPoseMixin} to stop vanilla's own per-tick
     *  pose recalculation from fighting our forced Pose.SLEEPING — see that mixin's comment. */
    public static boolean isForcingLyingPose(UUID playerId) {
        RestSession session = SESSIONS.get(playerId);
        return session != null && !session.isInGrace() && !session.isRealVanillaSleep()
                && session.getSeatEntity() != null && !isSeatedActivity(session);
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
     * critically still has to call {@link #endVisual} first: leaving a session in {@code SESSIONS}
     * just removed from the map, without ever discarding the {@link RestSeatEntity} mount or
     * calling stopRiding(), orphans the mount entity in the world. Vanilla's own entity save/load
     * then reconstructs the passenger relationship from the mount's saved NBT on the next login —
     * the player rejoins still mounted (and, if it was a lying-down session, missing the Pose
     * override, since Pose itself isn't persisted — reported as "sitting with no camera problems").
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
        if (session.isRealVanillaSleep()) return; // vanilla already placed them via startSleepInBed

        boolean seated = isSeatedActivity(session);
        Vec3 seatPos = session.getBedPos() != null
                ? Vec3.atBottomCenterOf(session.getBedPos()).add(0, seated ? 0.5 : 0.6875, 0) // mattress vs lying height
                // Bedless lying-down still needs some clearance off exact ground level — at Y+0,
                // Pose.SLEEPING's tiny eye height sits right at/inside the terrain, and the forced
                // third-person camera's clip-avoidance collapses to near-zero distance trying to
                // avoid it, producing a broken almost-first-person view from underground.
                : (seated ? player.position() : player.position().add(0, 0.6875, 0));
        RestSeatEntity seat = new RestSeatEntity(player.level(), seatPos);
        player.level().addFreshEntity(seat);
        player.startRiding(seat);
        session.setSeatEntity(seat);

        if (!seated) {
            if (session.getBedPos() != null) {
                Direction facing = BedBlock.getBedOrientation(player.level(), session.getBedPos());
                if (facing != null) {
                    player.setYRot(facing.toYRot());
                    player.setXRot(0);
                }
            }
            player.setPose(Pose.SLEEPING);
        }
    }

    private static void endVisual(ServerPlayer player, RestSession session, boolean wakeImmediately) {
        if (session.getSeatEntity() != null) {
            player.stopRiding();
            session.getSeatEntity().discard();
            session.setSeatEntity(null);
            if (player.getPose() == Pose.SLEEPING) {
                player.setPose(Pose.STANDING);
            }
            return;
        }
        if (session.isRealVanillaSleep() && player.isSleeping()) {
            player.stopSleepInBed(wakeImmediately, true);
        }
    }

    // ── Client sync ──────────────────────────────────────────────────────────

    private static void sendSync(ServerPlayer player, @Nullable RestSession session) {
        RestTimeSyncPayload payload;
        if (session == null) {
            payload = RestTimeSyncPayload.cleared();
        } else if (session.isInGrace()) {
            // The visual (including the forced pose) is already torn down by the time grace
            // starts (see interrupt()), so the client should already have its camera back.
            int currentTick = player.level().getServer().getTickCount();
            int graceRemaining = Math.max(0, session.getGraceDeadlineTick() - currentTick);
            payload = new RestTimeSyncPayload(true, true, session.getType(), session.getRemainingTicks(), graceRemaining, false);
        } else {
            boolean lyingDown = !session.isRealVanillaSleep() && !isSeatedActivity(session);
            payload = new RestTimeSyncPayload(true, false, session.getType(), session.getRemainingTicks(), 0, lyingDown);
        }
        ServerPlayNetworking.send(player, payload);
    }

    private RestSessionManager() {}
}
