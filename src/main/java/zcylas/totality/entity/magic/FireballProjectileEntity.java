package zcylas.totality.entity.magic;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.api.combat.damage.DamageFlags;
import zcylas.totality.api.combat.damage.DamageTypes;
import zcylas.totality.api.combat.damage.TotalityDamage;
import zcylas.totality.api.dice.Dice;
import zcylas.totality.api.dice.RollOutcome;
import zcylas.totality.api.dice.RollType;
import zcylas.totality.api.rpg.combat.SavingThrow;
import zcylas.totality.api.rpg.stats.AbilityScore;
import zcylas.totality.init.ModEntities;

import java.util.List;

/**
 * Fireball projectile — travels slowly, explodes in a 6-block radius on impact.
 *
 * On explosion:
 *  - Each entity in 6-block radius makes a DEX saving throw vs the caster's spell save DC.
 *  - Fail: full damage (8d6 Fire). Save: half damage (4d6).
 */
public class FireballProjectileEntity extends Projectile {

    private static final EntityDataAccessor<Float> SPELL_SAVE_DC =
            SynchedEntityData.defineId(FireballProjectileEntity.class, EntityDataSerializers.FLOAT);

    private static final float BOLT_SPEED  = 1.2f; // slower than spell bolts
    private static final float BLAST_RADIUS = 6.0f;
    private static final int   DICE_COUNT  = 8;
    private static final Dice  DAMAGE_DIE  = Dice.D6;
    private static final int   MAX_LIFETIME = 100; // 5 seconds

    private int ticksAlive = 0;
    private int spellSaveDc = 14; // computed from caster in create()

    public FireballProjectileEntity(EntityType<? extends FireballProjectileEntity> type, Level level) {
        super(type, level);
    }

    private FireballProjectileEntity(Level level, LivingEntity caster) {
        super(ModEntities.FIREBALL_PROJECTILE, level);
        this.setOwner(caster);
        this.setPos(caster.getX(), caster.getEyeY() - 0.1, caster.getZ());
    }

    /** DC is computed by the spell via {@code getSpellSaveDc(player)}. */
    public static FireballProjectileEntity create(Level level, LivingEntity caster, int spellSaveDc) {
        FireballProjectileEntity proj = new FireballProjectileEntity(level, caster);
        proj.spellSaveDc = spellSaveDc;
        proj.entityData.set(SPELL_SAVE_DC, (float) spellSaveDc);
        Vec3 look = caster.getLookAngle();
        proj.setDeltaMovement(look.scale(BOLT_SPEED));
        proj.updateRotation();
        return proj;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SPELL_SAVE_DC, 14f);
    }

    @Override
    public void tick() {
        super.tick();
        ticksAlive++;
        if (ticksAlive >= MAX_LIFETIME) { this.discard(); return; }

        if (level().isClientSide()) {
            // Orange-red fireball trail
            level().addParticle(ParticleTypes.FLAME,
                    getX(), getY(), getZ(), 0, 0, 0);
            level().addParticle(ParticleTypes.LARGE_SMOKE,
                    getX(), getY(), getZ(),
                    (Math.random()-0.5)*0.1, 0, (Math.random()-0.5)*0.1);
        }

        Vec3 start    = this.position();
        Vec3 velocity = this.getDeltaMovement();
        Vec3 end      = start.add(velocity);

        var blockHit = level().clip(new net.minecraft.world.level.ClipContext(
                start, end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, this));

        HitResult hit = blockHit.getType() != HitResult.Type.MISS ? blockHit
                : net.minecraft.world.entity.projectile.ProjectileUtil.getHitResultOnMoveVector(
                this, this::canHitEntity);

        if (hit.getType() != HitResult.Type.MISS) {
            onHit(hit);
            return;
        }

        this.setPos(end.x, end.y, end.z);
        this.updateRotation();
    }

    @Override
    protected void onHitEntity(EntityHitResult hit)  { explode(); }
    @Override
    protected void onHitBlock(BlockHitResult hit)     { explode(); }

    private void explode() {
        if (!(level() instanceof ServerLevel serverLevel)) { this.discard(); return; }
        if (!(getOwner() instanceof LivingEntity caster))  { this.discard(); return; }

        double x = getX(), y = getY(), z = getZ();

        // ── Explosion visual ─────────────────────────────────────────────────
        // level.explode() gives us the full TNT-style ring + sound for free.
        // ExplosionInteraction.NONE = no block destruction (Fireball ignites, not destroys).
        serverLevel.explode(
                null, x, y, z,
                (float)(BLAST_RADIUS * 0.8),
                Level.ExplosionInteraction.NONE);

        // ── Block ignition ─────────────────────────────────────────────────────
        int iRad = (int) BLAST_RADIUS;
        for (int bx = -iRad; bx <= iRad; bx++) {
            for (int by = -iRad; by <= iRad; by++) {
                for (int bz = -iRad; bz <= iRad; bz++) {
                    if (bx*bx + by*by + bz*bz > BLAST_RADIUS * BLAST_RADIUS) continue;
                    net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(
                            x + bx, y + by, z + bz);
                    net.minecraft.core.BlockPos above = pos.above();
                    if (net.minecraft.world.level.block.BaseFireBlock.canBePlacedAt(
                            serverLevel, above, net.minecraft.core.Direction.UP)
                            && serverLevel.getRandom().nextInt(3) == 0) {
                        serverLevel.setBlock(above,
                                net.minecraft.world.level.block.BaseFireBlock.getState(serverLevel, above), 3);
                    }
                }
            }
        }

        // ── Damage entities ────────────────────────────────────────────────────
        AABB box = new AABB(x-BLAST_RADIUS, y-BLAST_RADIUS, z-BLAST_RADIUS,
                x+BLAST_RADIUS, y+BLAST_RADIUS, z+BLAST_RADIUS);
        List<LivingEntity> targets = serverLevel.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != caster && e.distanceTo(this) <= BLAST_RADIUS);

        float dc = this.entityData.get(SPELL_SAVE_DC);

        for (LivingEntity target : targets) {
            int raw = 0;
            for (int i = 0; i < DICE_COUNT; i++) raw += DAMAGE_DIE.roll(target.getRandom());

            RollOutcome outcome = SavingThrow.roll(target, AbilityScore.DEX, (int) dc, RollType.NORMAL);
            float damage = outcome.isSuccess() ? raw / 2f : raw;

            TotalityDamage.hurt(target, caster, DamageTypes.FIRE, damage,
                    DamageFlags.IS_AOE, DamageFlags.NO_CONDITIONS);
        }

        this.discard();
    }

    @Override protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput o) {}
    @Override protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput i) {}
}