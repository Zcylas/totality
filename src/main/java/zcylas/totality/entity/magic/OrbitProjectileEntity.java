package zcylas.totality.entity.magic;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.api.magic.context.FormulaContext;
import zcylas.totality.api.magic.context.FormulaResolver;
import zcylas.totality.api.magic.formula.ArcaneFormula;
import zcylas.totality.init.ModEntities;

public class OrbitProjectileEntity extends Projectile {

    private static final EntityDataAccessor<Integer> OFFSET =
            SynchedEntityData.defineId(OrbitProjectileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TOTAL =
            SynchedEntityData.defineId(OrbitProjectileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> RADIUS =
            SynchedEntityData.defineId(OrbitProjectileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SPEED =
            SynchedEntityData.defineId(OrbitProjectileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> SENSITIVE =
            SynchedEntityData.defineId(OrbitProjectileEntity.class, EntityDataSerializers.BOOLEAN);

    private ArcaneFormula formula;
    private int ticksAlive   = 0;
    private int maxLifetime  = 60 * 20; // 60 seconds default
    private boolean tracksGround = false;
    private Vec3 groundPos   = Vec3.ZERO;

    public OrbitProjectileEntity(EntityType<? extends OrbitProjectileEntity> type, Level level) {
        super(type, level);
    }

    public OrbitProjectileEntity(Level level, LivingEntity owner, ArcaneFormula formula,
                                 int offset, int total, float radius, float speed,
                                 int lifetime, boolean sensitive) {
        super(ModEntities.ORBIT_PROJECTILE, level);
        this.formula   = formula;
        this.maxLifetime = lifetime;
        this.setOwner(owner);
        this.entityData.set(OFFSET, offset);
        this.entityData.set(TOTAL, total);
        this.entityData.set(RADIUS, radius);
        this.entityData.set(SPEED, speed);
        this.entityData.set(SENSITIVE, sensitive);
        this.setPos(owner.getX(), owner.getEyeY(), owner.getZ());
    }

    // Constructor for orbiting a ground position (block hit)
    public OrbitProjectileEntity(Level level, LivingEntity owner, ArcaneFormula formula,
                                 Vec3 groundPos, int offset, int total, float radius,
                                 float speed, int lifetime, boolean sensitive) {
        this(level, owner, formula, offset, total, radius, speed, lifetime, sensitive);
        this.tracksGround = true;
        this.groundPos    = groundPos;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OFFSET,    0);
        builder.define(TOTAL,     3);
        builder.define(RADIUS,    1.5f);
        builder.define(SPEED,     1.0f);
        builder.define(SENSITIVE, false);
    }

    @Override
    public void tick() {
        super.tick();
        ticksAlive++;

        if (ticksAlive >= maxLifetime) {
            this.discard();
            return;
        }

        // Move to orbit position
        Vec3 orbitPos = getOrbitPosition(ticksAlive);
        this.setPos(orbitPos.x, orbitPos.y, orbitPos.z);

        // Particle trail client side
        if (this.level().isClientSide()) {
            this.level().addParticle(ParticleTypes.WITCH,
                    getX(), getY(), getZ(), 0, 0, 0);
        }

        // Collision check — server side only
        if (!this.level().isClientSide()) {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) {
                onHit(hit);
            }
        }
    }

    private Vec3 getOrbitPosition(int tick) {
        float radius = entityData.get(RADIUS);
        float speed  = entityData.get(SPEED);
        int offset   = entityData.get(OFFSET);
        int total    = entityData.get(TOTAL);

        // Spread projectiles evenly around the orbit
        double angleOffset = (2 * Math.PI / total) * offset;
        double angle       = tick * speed * 0.1 + angleOffset;

        Vec3 center = getCenter();
        return new Vec3(
                center.x + radius * Math.cos(angle),
                center.y + 1.0,
                center.z + radius * Math.sin(angle));
    }

    private Vec3 getCenter() {
        if (tracksGround) return groundPos;
        Entity owner = getOwner();
        if (owner == null || owner.isRemoved()) return groundPos;
        return owner.position();
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        if (!entityData.get(SENSITIVE)) return;
        if (formula == null) return;
        if (!(level() instanceof ServerLevel)) return;
        if (getOwner() instanceof LivingEntity caster) {
            FormulaContext context = new FormulaContext(
                    level(), formula, caster, caster.getMainHandItem());
            new FormulaResolver(context).onResolveEffect(level(), hit);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        if (formula == null) return;
        if (!(level() instanceof ServerLevel)) return;
        // Don't hit the owner
        if (hit.getEntity().equals(getOwner()) && !tracksGround) return;
        if (getOwner() instanceof LivingEntity caster) {
            FormulaContext context = new FormulaContext(
                    level(), formula, caster, caster.getMainHandItem());
            new FormulaResolver(context).onResolveEffect(level(), hit);
        }
    }

    public void setFormula(ArcaneFormula formula) { this.formula = formula; }
    public ArcaneFormula getFormula()             { return formula; }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {}

    @Override
    protected void readAdditionalSaveData(ValueInput input) {}
}