package zcylas.totality.api.mining;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
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

/**
 * Server-owned mining swings for survival players. Only sessions of players who are actually
 * mining exist, so this costs nothing while nobody mines.
 *
 * <pre>
 *  IDLE --(holding, block under crosshair)--> WINDUP --contact frame--> RECOVERY --> IDLE (repeat while held)
 * </pre>
 * At the contact frame the server raycasts from the player's CURRENT eye/rotation/reach; the
 * target is never locked at click time and the client never says what it hit.
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
        int recoveryTicks = MiningTuning.RECOVERY_TICKS;   // cadence-scaled at swing start (normal swings only)
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
    }

    /** Test hook: the force the server has queued for this player's next power swing, or -1. */
    static float queuedForce(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        return s == null ? -1f : s.queuedForce;
    }

    /** Test seam: sees every body-strain amount the moment the server applies it (test fake players are invulnerable). */
    static java.util.function.BiConsumer<ServerPlayer, Float> bodyStrainObserver;

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
            case IDLE -> {
                boolean power = s.queuedForce >= 0f;
                if (!power && !s.holding && !s.startRequested) return;
                BlockHitResult target = pickBlock(player);
                s.startRequested = false;
                if (target == null || !ownsMining(player, (ServerLevel) player.level(), target.getBlockPos())) {
                    if (power) s.queuedForce = -1f;          // nothing to swing at: drop the request
                    return;
                }
                s.swingIsPower = power;
                s.swingForce = power ? s.queuedForce : 0f;
                s.queuedForce = -1f;
                int duration = player.getMainHandItem().getSwingAnimation().duration();
                // Cadence is the only place the vanilla break-speed stack (Efficiency/Haste/Fatigue...) matters,
                // and a deliberate power swing is never sped up or slowed down by it.
                float cadence = power ? 1f
                        : PlayerMiningPower.cadenceRatio(player, ((ServerLevel) player.level()).getBlockState(target.getBlockPos()));
                s.ticksLeft = MiningTuning.windUpTicks(duration, power, cadence);
                s.recoveryTicks = power ? MiningTuning.RECOVERY_TICKS : MiningTuning.recoveryTicks(cadence);
                s.swingSource = player.getMainHandItem().copy();
                if (!power) ServerPlayNetworking.send(player,
                        new zcylas.totality.networking.mining.MiningSwingPayload(s.ticksLeft, s.recoveryTicks));
                s.phase = Phase.WINDUP;
                player.swing(InteractionHand.MAIN_HAND, true);
            }
            case WINDUP -> {
                if (--s.ticksLeft > 0) return;
                contact(player, s);
                s.phase = Phase.RECOVERY;
                s.ticksLeft = s.recoveryTicks;
            }
            case RECOVERY -> {
                if (--s.ticksLeft <= 0) s.phase = Phase.IDLE;
            }
        }
    }

    /** The contact frame: fresh raycast, then exactly one impact if (and only if) a block is really hit. */
    private static void contact(ServerPlayer player, Session s) {
        contactNow(player, s.swingIsPower, s.swingForce, s.swingSource);
    }

    /** Is {@code current} still the source that began the swing? See {@link MiningSourceIdentity}. */
    static boolean sameSource(ItemStack snapshot, ItemStack current) {
        return MiningSourceIdentity.same(snapshot, current);
    }

    /** @return the result, or null when the swing missed (no block hit / obstructed / not owned) */
    @Nullable
    static MiningResult contactNow(ServerPlayer player, boolean power, float force, ItemStack swingSource) {
        // The swing was started by one source; if the hand now holds a different one, that swing is void:
        // no damage, wear, stress, crack, text or break. (The next swing starts with the new item's own properties.)
        if (!sameSource(swingSource, player.getMainHandItem())) return null;
        BlockHitResult hit = pickBlock(player);
        if (hit == null) return null;                              // the swing missed
        ServerLevel level = (ServerLevel) player.level();
        if (!ownsMining(player, level, hit.getBlockPos())) return null;
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

        PlayerMiningPower.Result r = PlayerMiningPower.compute(player, state, force);
        boolean toolSource = r.kind() == MiningSource.Kind.PLAYER_TOOL;
        boolean overloaded = power && toolSource && r.forceLoad() > MiningTuning.forceTolerance(tool);
        int band = power ? MiningTuning.presentationBand(force, overloaded) : 0;

        MiningImpact impact = new MiningImpact(pos, hit.getDirection(), hit.getLocation(), r.damage(), r.tier(),
                MiningSource.of(r.kind(), player), band);
        MiningResult result = BlockBreaking.applyImpact(level, impact);

        if (toolSource) {
            int wear = MiningTuning.baseWear(result.outcome(), tool);
            boolean effective = result.outcome() == MiningResult.Outcome.DAMAGED || result.outcome() == MiningResult.Outcome.BROKEN;
            int stress = power && effective ? MiningTuning.toolStress(tool, r.forceLoad()) : 0;
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
