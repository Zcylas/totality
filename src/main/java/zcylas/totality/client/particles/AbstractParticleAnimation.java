package zcylas.totality.client.particles;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.api.client.particles.TotalityParticles;

/**
 * Base class for server-side, tick-based particle animations.
 * <p>
 * Unlike {@link TotalityParticles} (instant, one-shot), animations register a
 * server-tick listener and evolve each frame. On {@link #run()}, {@link #onRun()}
 * fires once immediately, then every tick for {@link #iterations} ticks, then
 * the stop callback fires.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * new VortexAnimation(serverLevel, ParticleTypes.SOUL_FIRE_FLAME, player.position())
 *     .setYaw(player.getYaw())
 *     .setPitch(player.getPitch())
 *     .setHelixes(3)
 *     .setForced(true)
 *     .onStop(() -> spawnImpactBurst(player))
 *     .runFor(2.0);
 * }</pre>
 *
 * <h3>Entity tracking</h3>
 * Call {@link #setEntityOrigin(Entity)} to attach the animation to a moving entity.
 * {@link #updatePositions} is enabled automatically. The origin is re-evaluated
 * every tick before {@link #onRun()} is called.
 *
 * <h3>Chaining</h3>
 * {@link #onStop(Runnable)} fires when the animation finishes (or {@link #stop()}
 * is called externally), allowing effects to be sequenced:
 * <pre>{@code
 * chargeAnimation.onStop(() -> releaseAnimation.runFor(0.5)).runFor(1.5);
 * }</pre>
 *
 * <b>Server-side only.</b> Never construct from a client-only path.
 *
 * @param <SELF> Concrete subclass — enables fluent chaining across the
 *               base and subclass setters without casts.
 */
@SuppressWarnings("unchecked")
public abstract class AbstractParticleAnimation<SELF extends AbstractParticleAnimation<SELF>> {

    // ── Core state ────────────────────────────────────────────────────────────

    protected ServerLevel level;
    protected ParticleOptions particle;
    protected Vec3 origin;

    /** Total number of tick-listener calls before the animation stops. */
    protected int iterations = 60;
    /** Current tick count within the tick listener. Starts at 0. */
    protected int ticks = 0;
    /** True once the animation has finished or been stopped externally. */
    protected boolean done = false;

    // ── Entity tracking ───────────────────────────────────────────────────────

    protected Entity entityOrigin;
    protected Vec3 originOffset = Vec3.ZERO;
    protected boolean updatePositions = false;
    protected boolean useEyePos = false;

    // ── Particle output ───────────────────────────────────────────────────────

    /** Constant velocity applied to every spawned particle. Default zero. */
    protected Vec3 velocity = Vec3.ZERO;
    /**
     * If true, particles are sent per-player with the force flag, making them
     * visible beyond the normal 32-block particle render distance.
     * Useful for large AOE abilities.
     */
    protected boolean forced = false;
    protected int particleLimit = 5000;
    protected boolean limitParticles = true;
    private int currentParticleCount = 0;

    // ── Throttle ──────────────────────────────────────────────────────────────

    /**
     * If true, {@link #onRun()} is skipped on ticks where
     * {@code ticks % spawnInterval != 0}. Halves (or more) particle traffic
     * on long-running effects without changing the visual duration.
     */
    protected boolean spawnEveryNTicks = false;
    protected int spawnInterval = 2;

    // ── Chaining ──────────────────────────────────────────────────────────────

    protected Runnable onStopCallback;

    // ── Constructor ───────────────────────────────────────────────────────────

    protected AbstractParticleAnimation(ServerLevel level, ParticleOptions particle, Vec3 origin) {
        this.level  = level;
        this.particle = particle;
        this.origin = origin;
    }

    // ── Abstract hook ─────────────────────────────────────────────────────────

    /**
     * Override in subclasses. Called once immediately on {@link #run()}, then
     * once per qualifying tick until {@link #iterations} is reached.
     * {@link #origin} is already updated from the entity if tracking is enabled.
     */
    protected abstract void onRun();

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Starts the animation. Fires {@link #onRun()} immediately (tick 0), then
     * registers a {@link ServerTickEvents#END_SERVER_TICK} listener that fires
     * it every tick for {@link #iterations} ticks.
     * <p>
     * The registered lambda stays in the event list after the animation finishes
     * (Fabric API has no unregister API), but becomes a no-op once
     * {@link #done} is true. Avoid calling {@code run()} more than once on the
     * same instance.
     */
    public void run() {
        currentParticleCount = 0;
        onRun();

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (done) return;

            currentParticleCount = 0;

            if (updatePositions) updatePos();

            ticks++;

            if (spawnEveryNTicks && ticks % spawnInterval != 0) return;

            onRun();

            if (ticks >= iterations) {
                done = true;
                ticks = 0;
                onFinish();
            }
        });
    }

    /**
     * Sets {@link #iterations} from seconds (20 ticks = 1 s) and calls {@link #run()}.
     */
    public void runFor(double seconds) {
        this.iterations = Math.max(1, (int) (seconds * 20));
        run();
    }

    /**
     * Registers a stop callback, then calls {@link #runFor(double)}.
     */
    public void runFor(double seconds, Runnable onStop) {
        this.onStopCallback = onStop;
        runFor(seconds);
    }

    /**
     * Immediately marks the animation as done and fires the stop callback.
     * Safe to call at any time — idempotent if already done.
     */
    public void stop() {
        if (done) return;
        done = true;
        onFinish();
    }

    /** Called when the animation ends naturally or via {@link #stop()}. */
    protected void onFinish() {
        if (onStopCallback != null) onStopCallback.run();
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /** Refreshes {@link #origin} from the tracked entity. */
    protected void updatePos() {
        if (entityOrigin == null) return;
        Vec3 base = useEyePos ? entityOrigin.getEyePosition() : entityOrigin.position();
        origin = (originOffset == Vec3.ZERO) ? base : base.add(originOffset);
    }

    /**
     * Spawns one particle at {@code pos} using the configured {@link #velocity}.
     * Respects {@link #particleLimit} and {@link #forced}.
     */
    protected void spawnParticle(Vec3 pos) {
        spawnParticle(pos, velocity);
    }

    /**
     * Spawns one particle at {@code pos} with an explicit velocity.
     */
    protected void spawnParticle(Vec3 pos, Vec3 vel) {
        if (limitParticles) {
            currentParticleCount++;
            if (currentParticleCount > particleLimit) return;
        }
        if (forced) {
            level.sendParticles(particle, true, true, pos.x, pos.y, pos.z, 1, vel.x, vel.y, vel.z, 0.0);
        } else {
            level.sendParticles(particle, pos.x, pos.y, pos.z, 1, vel.x, vel.y, vel.z, 0.0);
        }
    }

    // ── Fluent setters ────────────────────────────────────────────────────────
    //
    // Return type is SELF so subclass-specific and base-class setters can be
    // chained without casts:
    //   new VortexAnimation(...).setRadius(2f).setForced(true).runFor(3.0);

    private SELF self() { return (SELF) this; }

    /** Total tick-listener calls before stopping. Prefer {@link #runFor(double)}. */
    public SELF setIterations(int iterations) {
        this.iterations = iterations;
        return self();
    }

    /**
     * Attaches a moving entity as the animation origin.
     * Enables {@link #updatePositions} automatically.
     */
    public SELF setEntityOrigin(Entity entity) {
        this.entityOrigin = entity;
        this.updatePositions = true;
        return self();
    }

    /**
     * Additional offset added to the entity position each tick.
     * Only meaningful when entity tracking is active.
     * Example: {@code new Vec3(0, 1.8, 0)} to track above a player's head.
     */
    public SELF setOriginOffset(Vec3 offset) {
        this.originOffset = offset;
        return self();
    }

    /** If true, the entity's eye position is used instead of feet. Default false. */
    public SELF setUseEyePos(boolean useEyePos) {
        this.useEyePos = useEyePos;
        return self();
    }

    /**
     * If true, particles are sent per-player with the force flag, bypassing
     * render-distance culling. Use for large AOE or ritual effects.
     */
    public SELF setForced(boolean forced) {
        this.forced = forced;
        return self();
    }

    /** Constant velocity for every spawned particle. Default {@link Vec3#ZERO}. */
    public SELF setVelocity(Vec3 velocity) {
        this.velocity = velocity;
        return self();
    }

    /** Maximum particles spawned per tick before the rest are silently dropped. Default 5000. */
    public SELF setParticleLimit(int limit) {
        this.particleLimit = limit;
        return self();
    }

    /**
     * Spawns particles only every {@code n} ticks instead of every tick.
     * Reduces packet traffic on long effects without changing their visual duration.
     * Pass {@code n <= 1} to disable throttling.
     */
    public SELF setSpawnInterval(int n) {
        this.spawnEveryNTicks = n > 1;
        this.spawnInterval    = Math.max(1, n);
        return self();
    }

    /**
     * Callback fired when the animation ends naturally or via {@link #stop()}.
     * Use to chain effects:
     * <pre>{@code .onStop(() -> nextEffect.runFor(0.5)) }</pre>
     */
    public SELF onStop(Runnable callback) {
        this.onStopCallback = callback;
        return self();
    }
}
