package zcylas.totality.entity.base_weapon;


import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import zcylas.totality.api.dice.RollType;
import zcylas.totality.api.rpg.combat.CombatResolver;
import zcylas.totality.api.rpg.combat.weapon.TotalityWeaponItem;
import zcylas.totality.api.rpg.stats.AbilityScore;
import zcylas.totality.init.ModEntities;

public class ThrownShurikenEntity extends AbstractArrow {

    private static final EntityDataAccessor<ItemStack> DATA_ITEM =
            SynchedEntityData.defineId(ThrownShurikenEntity.class, EntityDataSerializers.ITEM_STACK);

    private float damage;

    public ThrownShurikenEntity(EntityType<? extends ThrownShurikenEntity> type, Level level) {
        super(type, level);
        this.damage = 4.0f;
    }

    public ThrownShurikenEntity(Level level, LivingEntity thrower, ItemStack stack, float damage) {
        super(ModEntities.THROWN_SHURIKEN, thrower, level, stack, null);
        this.damage = damage;
        this.entityData.set(DATA_ITEM, stack.copy());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ITEM, ItemStack.EMPTY);
    }

    public ItemStack getShurikenItem() {
        return this.entityData.get(DATA_ITEM);
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return getShurikenItem().isEmpty() ? ItemStack.EMPTY : getShurikenItem().copy();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!(result.getEntity() instanceof LivingEntity target)) return;

        net.minecraft.world.entity.Entity owner = getOwner();

        if (owner instanceof LivingEntity attacker) {
            ItemStack shurikenStack = getShurikenItem();

            if (!shurikenStack.isEmpty() && shurikenStack.getItem() instanceof TotalityWeaponItem weapon) {
                String weaponName = shurikenStack.getHoverName().getString();

                AbilityScore effective = weapon.getEffectiveAbilityScore(attacker);

                CombatResolver.resolveAttack(
                        attacker, target,
                        effective,               // finesse-aware ability score
                        weapon.isProficient(attacker),
                        weapon.modifyRollType(attacker, target, RollType.NORMAL),
                        weapon.getDiceCount(),
                        weapon.getDamageDie(),
                        weapon.getDamageType()   // 7-param — auto-adds ability mod to damage, no bonusDamage
                );
                // TODO: weapon.onHit() once CombatResolver exposes hit result
            } else {
                target.hurt(damageSources().thrown(this, owner), this.damage);
            }
        } else {
            target.hurt(damageSources().thrown(this, owner), this.damage);
        }

        ItemStack pickup = getDefaultPickupItem();
        if (!pickup.isEmpty() && getOwner() instanceof Player player) {
            pickup.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        }
        if (this.level() instanceof ServerLevel serverLevel && !pickup.isEmpty()) {
            this.spawnAtLocation(serverLevel, pickup, 0.1f);
        }
        this.discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result); // sticks in block, no durability loss, pickup via AbstractArrow
    }

    @Override
    public double getDefaultGravity() {
        return 0.01;
    }

    @Override
    protected SoundEvent getDefaultHitGroundSoundEvent() {
        return SoundEvents.ITEM_PICKUP; // placeholder, replace later
    }

    public boolean isGrounded() {
        return this.isInGround();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("shuriken_item", ItemStack.CODEC, this.entityData.get(DATA_ITEM));
        output.putFloat("damage", this.damage);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        ItemStack saved = input.read("shuriken_item", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        this.entityData.set(DATA_ITEM, saved);
        this.damage = input.getFloatOr("damage", 4.0f);
    }
}