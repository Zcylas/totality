package zcylas.totality.entity.gate;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Creative Test K: a VISUAL-ONLY Solo Leveling Normal Gate (after the solo_leveling_gate.png reference sheet) — a
 * free-standing, ~3 block tall dimensional vortex. It does nothing to anything: no teleport, no dungeon, no collision;
 * it is not a Gate API. Its position is the vortex centre; it faces along its yaw.
 *
 * <p>Synced: the {@link Phase}, the {@link GatePalette} id (the only difference between a blue and a red gate), and a
 * pulse counter — the entry/distortion reaction hook ({@link #pulse()}), purely visual. Life: {@link Phase#OPENING}
 * ({@value #OPEN_TICKS} ticks) → {@link Phase#STABLE} until {@link #close()} → {@link Phase#CLOSING}
 * ({@value #CLOSE_TICKS} ticks) → removed. Clients time the animation from the synced changes (SoloGateRenderer); all
 * its effects are drawn by the renderer, so nothing outlives the entity.
 */
public class SoloGateEntity extends Entity {

    public enum Phase { OPENING, STABLE, CLOSING }

    public static final int OPEN_TICKS = 40;
    public static final int CLOSE_TICKS = 36;
    public static final int PULSE_TICKS = 24;
    /** Radius of the vortex in blocks: the gate is about three blocks tall. */
    public static final float RADIUS = 1.5F;
    /** How high above the ground the centre of a standing gate sits (its ragged rim clears the ground). */
    public static final float CENTRE_HEIGHT = RADIUS + 0.45F;

    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(SoloGateEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<String> PALETTE = SynchedEntityData.defineId(SoloGateEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> PULSE = SynchedEntityData.defineId(SoloGateEntity.class, EntityDataSerializers.INT);

    private int phaseAge;
    private int pulseAge = PULSE_TICKS;

    public SoloGateEntity(EntityType<? extends SoloGateEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(PHASE, (byte) Phase.OPENING.ordinal());
        builder.define(PALETTE, GatePalette.BLUE.id());
        builder.define(PULSE, 0);
    }

    public Phase phase() {
        return Phase.values()[this.entityData.get(PHASE)];
    }

    public int phaseAge() {
        return this.phaseAge;
    }

    /** Ticks since the last pulse ({@value #PULSE_TICKS} or more when none is playing). */
    public int pulseAge() {
        return this.pulseAge;
    }

    public GatePalette palette() {
        return GatePalette.byId(this.entityData.get(PALETTE));
    }

    public void setPalette(GatePalette palette) {
        this.entityData.set(PALETTE, palette.id());
    }

    /** The entry/distortion reaction (server side): a visual energy pulse only. */
    public void pulse() {
        this.entityData.set(PULSE, this.entityData.get(PULSE) + 1);
        this.pulseAge = 0;
    }

    /** Starts closing (server side); the gate removes itself when it has dissipated. */
    public void close() {
        if (phase() != Phase.CLOSING) setPhase(Phase.CLOSING);
    }

    private void setPhase(Phase phase) {
        this.entityData.set(PHASE, (byte) phase.ordinal());
        this.phaseAge = 0;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (!level().isClientSide()) return;
        if (PHASE.equals(accessor)) this.phaseAge = 0;
        if (PULSE.equals(accessor) && this.entityData.get(PULSE) > 0) this.pulseAge = 0;
    }

    @Override
    public void tick() {
        super.tick();
        this.phaseAge++;
        this.pulseAge++;
        if (level().isClientSide()) return;
        if (phase() == Phase.OPENING && this.phaseAge >= OPEN_TICKS) setPhase(Phase.STABLE);
        else if (phase() == Phase.CLOSING && this.phaseAge >= CLOSE_TICKS) discard();
    }

    // Visual only: it cannot be hurt, pushed or picked, and nothing collides with it.
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        setPalette(GatePalette.byId(input.getStringOr("palette", GatePalette.BLUE.id())));
        setPhase(input.getBooleanOr("closing", false) ? Phase.CLOSING : Phase.STABLE);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putString("palette", palette().id());
        output.putBoolean("closing", phase() == Phase.CLOSING);
    }
}
