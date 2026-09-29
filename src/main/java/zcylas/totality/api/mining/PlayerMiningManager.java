package zcylas.totality.api.mining;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.networking.mining.MiningIntentPayload;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Server-owned mining swings for survival players. Only sessions of players who are actually
 * mining exist, so this costs nothing while nobody mines.
 *
 * <pre>
 *  IDLE --(holding, block under crosshair)--> WINDUP --contact frame--> RECOVERY --> IDLE (repeat while held)
 * </pre>
 * At the contact frame the server raycasts from the player's CURRENT eye/rotation/reach; the
 * target is never locked at click time and the client never says what it hit.
 *
 * <p>RECOVERY completing and IDLE re-scheduling the next WINDUP happen in the SAME server tick
 * while the player is still legitimately mining ({@link #tryStartSwing} is called directly from
 * RECOVERY's own completion, not left for a following IDLE tick) — a nominal N-tick cycle must
 * contact every N ticks, not N+1.
 */
public final class PlayerMiningManager {

    private enum Phase { IDLE, WINDUP, RECOVERY }

    private static final class Session {
        boolean holding;
        boolean startRequested;          // a click always yields at least one swing attempt
        float queuedForce = -1f;         // >= 0: a power swing is queued (force derived by the SERVER)
        long powerStartTick = -1;        // server game time of POWER_START; -1 = no power hold in progress
        Phase phase = Phase.IDLE;
        int ticksLeft;
        float swingForce;                // force of the swing in flight (0 = normal)
        boolean swingIsPower;
        ItemStack swingSource = ItemStack.EMPTY;   // the held source at swing start; the target block is NOT snapshotted
        int recoveryTicks = MiningTuning.RECOVERY_TICKS;   // cadence-scaled at swing start, corrected at contact (normal swings only)
        float speedCarryTicks;   // profiled tools only: fractional cycle-length remainder so authored Mining
                                 // Speed (e.g. 2.25/s) averages correctly over many cycles instead of rounding every one
        // What the contact-frame correction needs to re-derive the swing from its ACTUAL target (normal swings only):
        float carryBeforeSwing;  // speedCarryTicks before this swing's cycle was taken from it
        int swingWindUpTicks;    // wind-up already spent when contact happens
        int swingDuration;       // held item's swing animation duration at swing start
        boolean swingProfiled;
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private PlayerMiningManager() {}

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(MiningIntentPayload.TYPE, (payload, context) ->
                context.server().execute(() -> onIntent(context.player(), payload)));
        ServerTickEvents.END_SERVER_TICK.register(PlayerMiningManager::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SESSIONS.remove(handler.getPlayer().getUUID()));
        // Persisted cracks must reach a player the moment they (re)appear, not at the next sweep.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> BlockDamageStorage.syncTo(handler.getPlayer()));
        ServerPlayerEvents.AFTER_RESPAWN.register((old, player, alive) -> BlockDamageStorage.syncTo(player));
        // Old-save reconciliation that needs no position (records only, never the world); the rest is lazy.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            for (ServerLevel level : server.getAllLevels()) {
                int removed = BlockDamageStorage.get(level).reconcileRecords();
                if (removed > 0) zcylas.totality.Totality.LOGGER.info("[BlockDamage] {}: dropped {} Integrity record(s) no longer valid for their block",
                        level.dimension().identifier(), removed);
            }
        });
    }

    /** Test hook: the force the server has queued for this player's next power swing, or -1. */
    static float queuedForce(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        return s == null ? -1f : s.queuedForce;
    }

    /** Test seam: sees every body-strain amount the moment the server applies it (test fake players are invulnerable). */
    static java.util.function.BiConsumer<ServerPlayer, Float> bodyStrainObserver;

    /** Test seam: fires once per real contact FRAME (WINDUP completing and {@link #contact} running,
     *  regardless of strike outcome), AFTER the strike — lets tests drive the actual tick-by-tick
     *  state machine ({@link #debugTick}) and measure real contact-to-contact tick spacing, rather
     *  than re-deriving it from the pure {@code MiningTuning}/{@code MiningSpeedCalculator} formulas. */
    static Runnable contactFrameObserver;

    /**
     * Test seam: runs exactly one tick of the real scheduling state machine ({@link #tickSession})
     * directly for {@code player} — creating its {@link Session} on first use exactly like a real
     * intent would. A fake test player is never registered in the server's real
     * {@code PlayerList}, so the production {@link #tick} (which looks players up there) can never
     * drive one; this calls the exact same per-player transition logic {@link #tick} calls,
     * skipping only the PlayerList existence/liveness lookup and the unrelated block-damage sweep.
     */
    static void debugTickPlayer(ServerPlayer player) {
        Session s = SESSIONS.computeIfAbsent(player.getUUID(), id -> new Session());
        tickSession(player, s);
    }

    static void forget(ServerPlayer player) { SESSIONS.remove(player.getUUID()); }

    /** Whether Totality mining owns this block for this player (vanilla START/STOP must be refused). */
    public static boolean ownsMining(ServerPlayer player, ServerLevel level, BlockPos pos) {
        return MiningOwnership.owns(player.gameMode.getGameModeForPlayer(), player.getMainHandItem(), level, pos, level.getBlockState(pos));
    }

    static void onIntent(ServerPlayer player, MiningIntentPayload payload) {
        if (!player.gameMode.isSurvival() || !player.isAlive()) return;
        switch (payload.action()) {
            case HOLD_START -> {
                Session s = SESSIONS.computeIfAbsent(player.getUUID(), id -> new Session());
                s.holding = true;
                s.startRequested = true;
            }
            case HOLD_STOP -> {
                Session s = SESSIONS.get(player.getUUID());
                if (s != null) s.holding = false;
            }
            case POWER_START -> {
                if (MiningTier.excludedFromPowerMining(player.getMainHandItem())) return;   // Swords/Shears: no Power Mining
                Session s = SESSIONS.computeIfAbsent(player.getUUID(), id -> new Session());
                s.powerStartTick = player.level().getGameTime();
            }
            case POWER_RELEASE -> {
                Session s = SESSIONS.get(player.getUUID());
                // A power swing can only ever come from a hold the server saw start.
                if (s == null || s.powerStartTick < 0 || s.queuedForce >= 0f) return;
                long held = player.level().getGameTime() - s.powerStartTick;
                s.powerStartTick = -1;
                if (held > MiningTuning.MAX_POWER_HOLD_TICKS) return;
                if (MiningTier.excludedFromPowerMining(player.getMainHandItem())) return;   // swapped to a Sword/Shears mid-hold
                s.queuedForce = MiningTuning.meterValue(held);
            }
            case POWER_CANCEL -> {
                Session s = SESSIONS.get(player.getUUID());
                if (s != null) s.powerStartTick = -1;
            }
        }
    }

    private static void tick(net.minecraft.server.MinecraftServer server) {
        if (!SESSIONS.isEmpty()) {
            Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator();
            while (it.hasNext()) {
                var entry = it.next();
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                Session s = entry.getValue();
                if (player == null || !player.isAlive() || !player.gameMode.isSurvival()
                        || player.isSpectator() || player.isUsingItem()) {
                    it.remove();
                    continue;
                }
                tickSession(player, s);
                if (s.phase == Phase.IDLE && !s.holding && !s.startRequested && s.queuedForce < 0f && s.powerStartTick < 0) it.remove();
            }
        }
        if (server.getTickCount() % MiningTuning.SWEEP_INTERVAL_TICKS == 0) {
            for (ServerLevel level : server.getAllLevels()) {
                BlockDamageStorage storage = BlockDamageStorage.get(level);
                if (!storage.isEmpty()) storage.sweep();
            }
        }
    }

    private static void tickSession(ServerPlayer player, Session s) {
        switch (s.phase) {
            case IDLE -> tryStartSwing(player, s);
            case WINDUP -> {
                if (--s.ticksLeft > 0) return;
                contact(player, s);
                s.phase = Phase.RECOVERY;
                s.ticksLeft = s.recoveryTicks;
            }
            case RECOVERY -> {
                if (--s.ticksLeft <= 0) {
                    s.phase = Phase.IDLE;
                    // Start the next swing in THIS SAME tick, not the next server tick: a fresh
                    // IDLE tick that does nothing but transition to WINDUP would otherwise add one
                    // dead tick per cycle (a nominal N-tick cycle contacting every N+1 ticks),
                    // silently breaking the authored Mining Speed contact-to-contact cadence.
                    tryStartSwing(player, s);
                }
            }
        }
    }

    /**
     * Attempts to begin a new swing right now — called from a fresh IDLE tick, or immediately
     * after RECOVERY completes in the same tick (see {@link #tickSession}'s RECOVERY case). Server
     * authority, the fresh re-raycast, the source snapshot, and the normal-swing animation payload
     * are unchanged from before this was extracted; only WHEN this logic runs changed.
     */
    private static void tryStartSwing(ServerPlayer player, Session s) {
        boolean power = s.queuedForce >= 0f;
        if (!power && !s.holding && !s.startRequested) return;
        BlockHitResult target = pickBlock(player);
        s.startRequested = false;
        if (target == null || !ownsMining(player, (ServerLevel) player.level(), target.getBlockPos())) {
            if (power) {                             // nothing to swing at: drop the request
                s.queuedForce = -1f;
                reportPowerStrike(player, null);
            }
            return;
        }
        ItemStack heldTool = player.getMainHandItem();
        if (power && MiningTier.excludedFromPowerMining(heldTool)) {
            // A Power swing queued with another source can never be released through a Sword/Shears: drop it.
            s.queuedForce = -1f;
            return;
        }
        s.swingIsPower = power;
        s.swingForce = power ? s.queuedForce : 0f;
        s.queuedForce = -1f;
        int duration = heldTool.getSwingAnimation().duration();
        if (power) {
            // A deliberate power swing is never sped up or slowed down by cadence.
            s.ticksLeft = MiningTuning.windUpTicks(duration, true, 1f);
            s.recoveryTicks = MiningTuning.RECOVERY_TICKS;
        } else {
            BlockState targetState = ((ServerLevel) player.level()).getBlockState(target.getBlockPos());
            var profile = MiningSourceProfile.resolve(heldTool);
            s.swingProfiled = profile.isPresent();
            s.swingDuration = duration;
            s.carryBeforeSwing = s.speedCarryTicks;
            if (profile.isPresent()) {
                scheduleProfiledCycle(player, s, heldTool, targetState, duration);
            } else {
                // Legacy cadence (bare hands, swords, shears, any other non-profiled tool) — UNCHANGED from V1.
                float cadence = PlayerMiningPower.cadenceRatio(player, targetState);
                s.ticksLeft = MiningTuning.windUpTicks(duration, false, cadence);
                s.recoveryTicks = MiningTuning.recoveryTicks(cadence);
            }
            s.swingWindUpTicks = s.ticksLeft;
        }
        s.swingSource = heldTool.copy();
        if (!power) ServerPlayNetworking.send(player,
                new zcylas.totality.networking.mining.MiningSwingPayload(s.ticksLeft, s.recoveryTicks));
        s.phase = Phase.WINDUP;
        player.swing(InteractionHand.MAIN_HAND, true);
    }

    /**
     * Normal-swing timing for a PROFILED tool: total cycle ticks come from its authored/Efficiency/Haste/
     * environment Mining Speed (§6-8) instead of the legacy {@code cadenceRatio}. A small carry-over
     * accumulator on the session keeps fractional authored speeds (2.25/s, 2.4/s...) averaging correctly
     * over many cycles rather than silently rounding every single one. The windUp/recovery split within
     * that total preserves the existing swing-duration-based proportion (cadence-aware animation, §13).
     */
    private static void scheduleProfiledCycle(ServerPlayer player, Session s, ItemStack tool, BlockState state, int duration) {
        MiningCadence.Cycle cycle = profiledCycle(player, s.speedCarryTicks, tool, state, duration);
        s.speedCarryTicks = cycle.carry();
        s.ticksLeft = cycle.windUpTicks();
        s.recoveryTicks = cycle.recoveryTicks();
    }

    private static MiningCadence.Cycle profiledCycle(ServerPlayer player, float carry, ItemStack tool, BlockState state, int duration) {
        ResolvedMiningSource source = ResolvedMiningSource.of(tool, state);
        float speed = PlayerMiningPower.effectiveMiningSpeed(player, tool, state, source);
        return MiningCadence.profiled(carry, MiningCadence.idealTicks(speed), MiningTuning.windUpTicks(duration, false, 1f));
    }

    /** The contact frame: fresh raycast, then exactly one impact if (and only if) a block is really hit. */
    private static void contact(ServerPlayer player, Session s) {
        MiningResult result = contactNow(player, s.swingIsPower, s.swingForce, s.swingSource,
                s.swingIsPower ? null : actual -> correctRecovery(player, s, actual));
        if (s.swingIsPower) reportPowerStrike(player, result);
        if (contactFrameObserver != null) contactFrameObserver.run();
    }

    /**
     * Stale-target cadence correction: the swing was timed from the block under the crosshair at swing start, but
     * it struck {@code actual}. The wind-up already happened (contact moment unchanged); the remaining recovery is
     * re-derived so the whole swing lasts the cycle of the ACTUAL target, with the fractional carry rewound to
     * before this swing so long-run averages stay exact. Runs before the strike, so it sees the block (and the
     * tool) as struck. Misses, voided swings and Power swings never get here and keep their scheduled cycle.
     */
    private static void correctRecovery(ServerPlayer player, Session s, BlockState actual) {
        ItemStack tool = player.getMainHandItem();
        int cycleTicks;
        if (s.swingProfiled) {
            MiningCadence.Cycle cycle = profiledCycle(player, s.carryBeforeSwing, tool, actual, s.swingDuration);
            s.speedCarryTicks = cycle.carry();
            cycleTicks = cycle.cycleTicks();
        } else {
            cycleTicks = MiningTuning.cycleTicks(s.swingDuration, PlayerMiningPower.cadenceRatio(player, actual));
        }
        int recovery = MiningCadence.correctedRecovery(s.swingWindUpTicks, cycleTicks);
        if (recovery == s.recoveryTicks) return;
        s.recoveryTicks = recovery;
        ServerPlayNetworking.send(player, new zcylas.totality.networking.mining.MiningRecoveryPayload(recovery));
    }

    /**
     * Tells the player's client what its released Power swing did (Power Mining HUD impact feedback).
     * Presentation only; skipped for connections that cannot receive it (e.g. verification fake players).
     */
    private static void reportPowerStrike(ServerPlayer player, @Nullable MiningResult result) {
        var payload = new zcylas.totality.networking.mining.PowerStrikeResultPayload(
                zcylas.totality.networking.mining.PowerStrikeResultPayload.Outcome.of(result));
        if (ServerPlayNetworking.canSend(player, payload.type())) ServerPlayNetworking.send(player, payload);
    }

    /** Is {@code current} still the source that began the swing? See {@link MiningSourceIdentity}. */
    static boolean sameSource(ItemStack snapshot, ItemStack current) {
        return MiningSourceIdentity.same(snapshot, current);
    }

    /** @return the result, or null when the swing missed (no block hit / obstructed / not owned) */
    @Nullable
    static MiningResult contactNow(ServerPlayer player, boolean power, float force, ItemStack swingSource) {
        return contactNow(player, power, force, swingSource, null);
    }

    /** @param onActualTarget told the block really hit, just before it is struck (null: nothing to tell) */
    @Nullable
    private static MiningResult contactNow(ServerPlayer player, boolean power, float force, ItemStack swingSource,
                                           @Nullable Consumer<BlockState> onActualTarget) {
        // The swing was started by one source; if the hand now holds a different one, that swing is void:
        // no damage, wear, stress, crack, text or break. (The next swing starts with the new item's own properties.)
        if (!sameSource(swingSource, player.getMainHandItem())) return null;
        BlockHitResult hit = pickBlock(player);
        if (hit == null) return null;                              // the swing missed
        ServerLevel level = (ServerLevel) player.level();
        if (!ownsMining(player, level, hit.getBlockPos())) return null;
        if (onActualTarget != null) onActualTarget.accept(level.getBlockState(hit.getBlockPos()));
        return strike(player, hit, power, force);
    }

    /**
     * One impact by a player on the block of {@code hit}: damage/tier from {@link PlayerMiningPower}, then
     * durability accounting. A successful (DAMAGED) tool impact costs exactly 1 base durability (Totality's V1 tool policy, independent of the tool's vanilla per-block wear); the
     * terminal break is charged once by vanilla inside {@code destroyBlock} (normalised to 1, see BlockBreaking) and is NOT charged again here. Power
     * swings add Force Stress on any effective outcome. Hands never wear.
     */
    static MiningResult strike(ServerPlayer player, BlockHitResult hit, boolean power, float force) {
        ServerLevel level = (ServerLevel) player.level();
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        ItemStack tool = player.getMainHandItem();

        // The manager already knows whether this is a Power swing — never re-inferred from force>0
        // (a legitimate Power release can land at force exactly 0; see PlayerMiningPower.compute).
        PlayerMiningPower.Result r = PlayerMiningPower.compute(player, state, power, force);
        boolean toolSource = r.kind() == MiningSource.Kind.PLAYER_TOOL;
        int band = r.band();

        MiningImpact impact = new MiningImpact(pos, hit.getDirection(), hit.getLocation(), r.damage(), r.tier(),
                MiningSource.of(r.kind(), player), band);
        MiningResult result = BlockBreaking.applyImpact(level, impact);

        if (toolSource) {
            int wear = MiningTuning.baseWear(result.outcome(), tool);
            boolean effective = result.outcome() == MiningResult.Outcome.DAMAGED || result.outcome() == MiningResult.Outcome.BROKEN;
            // Profiled tools: new zone-based STR extra wear (§3). Non-profiled (swords, shears...): the old Force Stress formula, unchanged.
            int stress = power && effective
                    ? (r.profiled() ? MiningTuning.powerZoneExtraWear(band, r.strModifier()) : MiningTuning.toolStress(tool, r.forceLoad()))
                    : 0;
            if (wear + stress > 0) tool.hurtAndBreak(wear + stress, player, EquipmentSlot.MAINHAND);
        }
        if (power && !toolSource) {
            // Bare-hand Power: no tool to stress, so a successful contact strains the BODY (flat, band based).
            float strain = MiningTuning.bodyStrain(result.outcome(), band);
            if (strain > 0f) {
                if (bodyStrainObserver != null) bodyStrainObserver.accept(player, strain);
                player.hurtServer(level, level.damageSources().generic(), strain);   // the normal server damage path
            }
        }
        return result;
    }

    /** Server-side pick using the player's current position and rotation; null unless a block is hit unobstructed. */
    @Nullable
    private static BlockHitResult pickBlock(ServerPlayer player) {
        double reach = player.blockInteractionRange();
        HitResult hit = player.pick(reach, 1.0f, false);
        if (!(hit instanceof BlockHitResult block) || block.getType() != HitResult.Type.BLOCK) return null;

        Vec3 eye = player.getEyePosition();
        double blockDistSq = eye.distanceToSqr(block.getLocation());
        Vec3 end = eye.add(player.getViewVector(1.0f).scale(reach));
        AABB box = player.getBoundingBox().expandTowards(player.getViewVector(1.0f).scale(reach)).inflate(1.0);
        for (Entity e : player.level().getEntities(player, box, x -> !x.isSpectator() && x.isPickable())) {
            var clip = e.getBoundingBox().inflate(e.getPickRadius()).clip(eye, end);
            if (clip.isPresent() && eye.distanceToSqr(clip.get()) < blockDistSq) return null;   // entity in the way
        }
        return block;
    }
}
