package zcylas.totality.entity.animal;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Forest Boar (visual design: Astra's Forest_Boar_Astra.bbmodel). In the wild it wanders and grazes; when it senses
 * or spots a player it startles and scatters, either running away or making one defensive charge before running
 * away, and it slips away unseen once it is far enough from every player. See {@link ForestBoarBrain} (the state
 * machine) and {@link ForestBoarRules} (the tunable numbers). No breeding, taming or riding. Drops 1-2 Raw Meat
 * on death only ({@code totality:entities/forest_boar}); an escape is a silent removal, never a kill.
 */
public class ForestBoarEntity extends PathfinderMob {

    /** The server's behaviour state, synced to clients so they pick the matching animations. */
    public enum Behavior {
        WANDER, GRAZE, SNIFF, ALERT, CHARGE_WINDUP, CHARGE, FLEE;

        private static final Behavior[] VALUES = values();

        public boolean peaceful() {
            return this == WANDER || this == GRAZE || this == SNIFF;
        }

        /** Moves with the running gait (the flee and the rush); every other state walks. */
        public boolean running() {
            return this == CHARGE || this == FLEE;
        }

        public static Behavior byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : WANDER;
        }
    }

    private static final EntityDataAccessor<Byte> DATA_BEHAVIOR =
            SynchedEntityData.defineId(ForestBoarEntity.class, EntityDataSerializers.BYTE);

    private final ForestBoarBrain brain = new ForestBoarBrain(this);

    public ForestBoarEntity(EntityType<? extends ForestBoarEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 16.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.2)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_BEHAVIOR, (byte) Behavior.WANDER.ordinal());
    }

    public Behavior getBehavior() {
        return Behavior.byId(this.entityData.get(DATA_BEHAVIOR));
    }

    void setBehavior(Behavior behavior) {
        this.entityData.set(DATA_BEHAVIOR, (byte) behavior.ordinal());
    }

    @Override
    protected void registerGoals() {
        // Swimming only; everything else is the single state machine below.
        this.goalSelector.addGoal(0, new FloatGoal(this));
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        this.brain.tick(level);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && this.isAlive()) this.brain.onHurt(source.getEntity());
        return hurt;
    }

    /**
     * The escape: removed without dying — no death animation, sound, loot or experience, and not a kill. Only the
     * brain calls this, once {@link ForestBoarRules#mayEscape} holds.
     */
    void escape() {
        this.discard();
    }

    /** Named, leashed or otherwise persistent boars still flee but are never removed. */
    boolean keepsThroughEscape() {
        return this.hasCustomName() || this.isLeashed() || this.isPersistenceRequired() || this.isPassenger() || this.isVehicle();
    }

    /** Like vanilla animals, it is never despawned for being far away: the escape is its only disappearance. */
    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    // ── Development and verification hooks (the capture run and the dedicated-server suite) ──

    /** Forces the next reaction (true: charge, false: flee, null: roll as usual). */
    public void forceNextReaction(@Nullable Boolean charge) {
        this.brain.forceNextReaction(charge);
    }

    /** "hit", "missed", "blocked", "timed out" or "abandoned (...)" for the last rush, or null. */
    @Nullable
    public String lastChargeResult() {
        return this.brain.lastChargeResult;
    }

    public boolean hasCharged() {
        return this.brain.hasCharged();
    }

    /** True once this boar was removed by escaping. */
    public boolean hasEscaped() {
        return this.brain.escaped;
    }

    /** The escape decision's inputs when it was taken, or null. */
    @Nullable
    public String escapeRecord() {
        return this.brain.escapeRecord;
    }

    public int ticksInBehavior() {
        return this.brain.ticksInState();
    }

    /** Capture footage only: pauses the brain and shows {@code behavior} (null resumes the normal brain, peaceful). */
    public void scriptForCapture(@Nullable Behavior behavior) {
        this.brain.script(behavior);
    }

    /** Vanilla's animal spawn rule (grass-type ground, enough light), for this non-breeding mob. */
    public static boolean checkForestBoarSpawnRules(EntityType<ForestBoarEntity> type, LevelAccessor level,
                                                    EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        boolean bright = EntitySpawnReason.ignoresLightRequirements(reason) || level.getRawBrightness(pos, 0) > 8;
        return level.getBlockState(pos.below()).is(BlockTags.ANIMALS_SPAWNABLE_ON) && bright;
    }

    // Vanilla's own boar voice (the hoglin), quieter and a touch higher for this smaller forest animal.
    @Override
    protected SoundEvent getAmbientSound() { return SoundEvents.HOGLIN_AMBIENT; }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.HOGLIN_HURT; }

    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.HOGLIN_DEATH; }

    @Override
    protected void playStepSound(BlockPos pos, BlockState block) {
        this.playSound(SoundEvents.HOGLIN_STEP, 0.15F, 1.0F);
    }

    @Override
    protected float getSoundVolume() {
        return 0.6F;
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 1.15F;
    }
}
