package zcylas.totality.entity.magic;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.scores.PlayerTeam;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.init.ModEntities;

import java.util.UUID;

public class SummonSkeletonEntity extends Skeleton {

    private static final EntityDataAccessor<String> OWNER_UUID =
            SynchedEntityData.defineId(SummonSkeletonEntity.class, EntityDataSerializers.STRING);

    private final RangedBowAttackGoal<SummonSkeletonEntity> bowGoal =
            new RangedBowAttackGoal<>(this, 1.0D, 20, 15.0F);

    private final MeleeAttackGoal meleeGoal = new MeleeAttackGoal(this, 1.5D, true) {
        @Override public void stop() {
            super.stop();
            SummonSkeletonEntity.this.setAggressive(false);
        }
        @Override public void start() {
            super.start();
            SummonSkeletonEntity.this.setAggressive(true);
        }
    };

    private LivingEntity owner;
    private int limitedLifeTicks = 0;

    public SummonSkeletonEntity(EntityType<? extends Skeleton> type, Level level) {
        super(type, level);
    }

    public SummonSkeletonEntity(Level level, LivingEntity owner, ItemStack weapon) {
        super(ModEntities.SUMMON_SKELETON, level);
        this.owner = owner;
        this.setOwnerUUID(owner.getUUID());
        this.setWeapon(weapon);
        this.limitedLifeTicks = 20 * 60; // 60 seconds; // default, will be overwritten by setLimitedLife()
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 3.0F, 1.0F));
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Mob.class, 8.0F));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(4, new FollowOwnerGoal());

        // Don't retaliate against other summon skeletons
        this.targetSelector.addGoal(2, new HurtByTargetGoal(this, SummonSkeletonEntity.class));

        // Attack mobs targeting the owner
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Mob.class, 10,
                false, true,
                (LivingEntity entity, @Nullable ServerLevel level) ->
                        entity instanceof Mob mob
                                && mob.getTarget() != null
                                && mob.getTarget().getUUID().equals(getOwnerUUID())));
    }

    // Simple follow goal
    private class FollowOwnerGoal extends Goal {
        @Override
        public boolean canUse() {
            LivingEntity o = getActualOwner();
            return o != null && distanceToSqr(o) > 6 * 6;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity o = getActualOwner();
            return o != null && distanceToSqr(o) > 4 * 4;
        }

        @Override
        public void tick() {
            LivingEntity o = getActualOwner();
            if (o != null) {
                getNavigation().moveTo(o, 1.2);
                getLookControl().setLookAt(o, 10F, (float) getMaxHeadXRot());
            }
        }
    }

    public void setWeapon(ItemStack item) {
        this.setItemSlot(EquipmentSlot.MAINHAND, item);
        this.reassessWeaponGoal();
    }

    @Override
    public void reassessWeaponGoal() {
        if (!(this.level() instanceof ServerLevel)) return;
        if (meleeGoal == null || bowGoal == null) return; // guard against constructor call
        this.goalSelector.removeGoal(meleeGoal);
        this.goalSelector.removeGoal(bowGoal);
        ItemStack held = this.getItemInHand(
                ProjectileUtil.getWeaponHoldingHand(this, Items.BOW));
        if (held.is(Items.BOW)) {
            bowGoal.setMinAttackInterval(20);
            this.goalSelector.addGoal(4, bowGoal);
        } else {
            this.goalSelector.addGoal(4, meleeGoal);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;
        if (limitedLifeTicks > 0) {
            --limitedLifeTicks;
            if (limitedLifeTicks == 0) {
                SummonSkeletonEntity.this.hurtServer(
                        (ServerLevel) level(),
                        level().damageSources().starve(),
                        20.0F);
            }
        }
    }

    public void setLimitedLife(int ticks) {
        this.limitedLifeTicks = ticks;
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        EntitySpawnReason spawnType, @Nullable SpawnGroupData spawnData) {
        // Don't call super to avoid random equipment
        return spawnData;
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        // No default equipment
    }

    @Override
    protected boolean shouldDropLoot(ServerLevel level) { return false; }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {}

    @Override
    protected void dropEquipment(ServerLevel level) {}

    @Override
    protected int getBaseExperienceReward(ServerLevel level) { return 0; }



    @Override
    public PlayerTeam getTeam() {
        LivingEntity o = getActualOwner();
        if (o != null) return o.getTeam();
        return super.getTeam();
    }

    public LivingEntity getActualOwner() { return owner; }

    @Nullable
    public UUID getOwnerUUID() {
        String str = this.entityData.get(OWNER_UUID);
        if (str.isEmpty()) return null;
        try {
            return UUID.fromString(str);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void setOwnerUUID(@Nullable UUID uuid) {
        this.entityData.set(OWNER_UUID, uuid != null ? uuid.toString() : "");
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER_UUID, "");
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        UUID uuid = getOwnerUUID();
        if (uuid != null) output.putString("OwnerUUID", uuid.toString());
        output.putInt("LifeTicks", limitedLifeTicks);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        input.getString("OwnerUUID").ifPresent(s -> {
            try { setOwnerUUID(UUID.fromString(s)); } catch (IllegalArgumentException ignored) {}
        });
        limitedLifeTicks = input.getIntOr("LifeTicks", 0);
    }
}