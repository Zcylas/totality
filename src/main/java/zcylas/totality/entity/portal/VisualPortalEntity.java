package zcylas.totality.entity.portal;

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
import net.minecraft.world.phys.Vec3;

/**
 * Creative Capability Test C: a NONFUNCTIONAL, visual-only green swirling portal (after
 * Context/References/Other/Totality_ Green Portal Reference Sheet.png). It does nothing to anything that touches it: no
 * teleport, no destination, no collision; it is not the Totality gate system.
 * <p>The entity's position is the portal's centre; it faces along its rotation (yaw/pitch, any orientation) and its
 * {@link #size() size} scales the default 1.6 x 2.4 block oval. Its life: {@link Phase#OPENING} (1 s) then
 * {@link Phase#STABLE} until {@link #collapse()}, then {@link Phase#COLLAPSING} (1 s) and it removes itself. The phase is
 * synced; each client times the animation itself from the phase change (VisualPortalRenderer, {@link VisualPortalVfx}).
 * Spawn with /portaltest or /summon totality:visual_portal.
 */
public class VisualPortalEntity extends Entity {

    public enum Phase { OPENING, STABLE, COLLAPSING }

    public static final int OPEN_TICKS = 20;
    public static final int COLLAPSE_TICKS = 20;
    public static final float MIN_SIZE = 0.5F;
    public static final float MAX_SIZE = 4.0F;

    private static final EntityDataAccessor<Byte> PHASE =
            SynchedEntityData.defineId(VisualPortalEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> SIZE =
            SynchedEntityData.defineId(VisualPortalEntity.class, EntityDataSerializers.FLOAT);

    /** Ticks since the current phase began (on each side; a client restarts it when the synced phase changes). */
    private int phaseAge;

    public VisualPortalEntity(EntityType<? extends VisualPortalEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(PHASE, (byte) Phase.OPENING.ordinal());
        builder.define(SIZE, 1.0F);
    }

    public Phase phase() {
        return Phase.values()[this.entityData.get(PHASE)];
    }

    public int phaseAge() {
        return this.phaseAge;
    }

    public float size() {
        return this.entityData.get(SIZE);
    }

    public void setSize(float size) {
        this.entityData.set(SIZE, Math.clamp(size, MIN_SIZE, MAX_SIZE));
    }

    /** Starts the collapse (server side); the portal removes itself when it has finished. */
    public void collapse() {
        if (phase() != Phase.COLLAPSING) setPhase(Phase.COLLAPSING);
    }

    private void setPhase(Phase phase) {
        this.entityData.set(PHASE, (byte) phase.ordinal());
        this.phaseAge = 0;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (PHASE.equals(accessor) && level().isClientSide()) this.phaseAge = 0;
    }

    @Override
    public void tick() {
        super.tick();
        this.phaseAge++;
        if (level().isClientSide()) {
            VisualPortalVfx.clientTick(this);
            return;
        }
        if (phase() == Phase.OPENING && this.phaseAge >= OPEN_TICKS) setPhase(Phase.STABLE);
        else if (phase() == Phase.COLLAPSING && this.phaseAge >= COLLAPSE_TICKS) discard();
    }

    /**
     * The portal's axes in the world: right, up (along its height) and the facing normal. Shared by the renderer and
     * the particles so they agree.
     */
    public static Vec3[] basis(float yaw, float pitch) {
        Vec3 normal = Vec3.directionFromRotation(pitch, yaw);
        Vec3 up = Vec3.directionFromRotation(pitch - 90.0F, yaw);
        Vec3 right = up.cross(normal);
        return new Vec3[]{right, up, normal};
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
    protected void readAdditionalSaveData(ValueInput input) {
        setSize(input.getFloatOr("size", 1.0F));
        // A portal saved mid-collapse finishes collapsing when loaded; one saved opening or open comes back open.
        setPhase(input.getBooleanOr("collapsing", false) ? Phase.COLLAPSING : Phase.STABLE);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putFloat("size", size());
        output.putBoolean("collapsing", phase() == Phase.COLLAPSING);
    }
}
