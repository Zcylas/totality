package zcylas.totality.entity.magic;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.api.magic.context.FormulaContext;
import zcylas.totality.api.magic.context.FormulaResolver;
import zcylas.totality.api.magic.formula.ArcaneFormula;
import zcylas.totality.api.magic.rune.AbstractRune;
import zcylas.totality.init.ModEntities;

import java.util.List;

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
    private int ticksAlive  = 0;
    private int maxLifetime = 60 * 20; // 60 seconds default
    private boolean tracksGround = false;
    private Vec3 groundPos  = Vec3.ZERO;
    private int trackedEntityId = -1;

    public OrbitProjectileEntity(EntityType<? extends OrbitProjectileEntity> type, Level level) {
        super(type, level);
    }

    public OrbitProjectileEntity(Level level, LivingEntity owner, ArcaneFormula formula,
                                 int offset, int total, float radius, float speed,
                                 int lifetime, boolean sensitive) {
        super(ModEntities.ORBIT_PROJECTILE, level);
        this.formula     = formula;
        this.maxLifetime = lifetime;
        this.setOwner(owner);
        this.entityData.set(OFFSET,    offset);
        this.entityData.set(TOTAL,     total);
        this.entityData.set(RADIUS,    radius);
        this.entityData.set(SPEED,     speed);
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

        Vec3 orbitPos = getOrbitPosition(ticksAlive);
        this.setPos(orbitPos.x, orbitPos.y, orbitPos.z);

        if (this.level().isClientSide()) {
            // ── Subtle orbit trail — witch particles ──────────────────────────
            this.level().addParticle(ParticleTypes.WITCH,
                    getX(), getY(), getZ(), 0, 0, 0);
            // ── Occasional enchant sparkle — every 3 ticks ────────────────────
            if (ticksAlive % 3 == 0) {
                this.level().addParticle(ParticleTypes.ENCHANT,
                        getX() + (Math.random() - 0.5) * 0.15,
                        getY() + (Math.random() - 0.5) * 0.15,
                        getZ() + (Math.random() - 0.5) * 0.15,
                        0, 0, 0);
            }
            return;
        }

        // Grace period — skip hit detection for first 10 ticks
        if (ticksAlive < 10) return;

        // Entity hit detection
        net.minecraft.world.phys.AABB hitBox = net.minecraft.world.phys.AABB.ofSize(
                orbitPos, 1.0, 1.0, 1.0);
        var entities = this.level().getEntities(this, hitBox, this::canHitEntity);
        if (!entities.isEmpty()) {
            onHitEntity(new EntityHitResult(entities.get(0)));
            return;
        }

        // Block hit — only if sensitive
        if (entityData.get(SENSITIVE)) {
            net.minecraft.core.BlockPos blockPos = net.minecraft.core.BlockPos.containing(
                    orbitPos.x, orbitPos.y, orbitPos.z);
            net.minecraft.world.level.block.state.BlockState state =
                    this.level().getBlockState(blockPos);
            if (!state.isAir() && state.getDestroySpeed(this.level(), blockPos) >= 0) {
                onHitBlock(new BlockHitResult(orbitPos,
                        net.minecraft.core.Direction.UP, blockPos, false));
            }
        }
    }

    private Vec3 getOrbitPosition(int tick) {
        float radius = entityData.get(RADIUS);
        float speed  = entityData.get(SPEED);
        int offset   = entityData.get(OFFSET);
        int total    = entityData.get(TOTAL);

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
        if (trackedEntityId >= 0) {
            Entity tracked = level().getEntity(trackedEntityId);
            if (tracked != null && !tracked.isRemoved()) return tracked.position();
        }
        Entity owner = getOwner();
        if (owner == null || owner.isRemoved()) return groundPos;
        return owner.position();
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        spawnImpactParticles();
        if (formula == null) return;
        if (!(level() instanceof ServerLevel)) return;
        if (hit.getEntity().equals(getOwner()) && !tracksGround) return;
        if (trackedEntityId >= 0 && hit.getEntity().getId() == trackedEntityId) return;
        if (!(getOwner() instanceof LivingEntity caster)) return;

        int orbitIndex = findOrbitIndex();
        if (orbitIndex < 0 || orbitIndex >= formula.getRunes().size() - 1) return;

        FormulaContext parentContext = new FormulaContext(level(), formula, caster, caster.getMainHandItem());
        FormulaContext childContext  = parentContext.makeChildContext(orbitIndex + 1);
        new FormulaResolver(childContext).onResolveEffect(level(), hit);
        this.discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        spawnImpactParticles();
        if (!entityData.get(SENSITIVE)) return;
        if (formula == null) return;
        if (!(level() instanceof ServerLevel)) return;
        if (!(getOwner() instanceof LivingEntity caster)) return;

        int orbitIndex = findOrbitIndex();
        if (orbitIndex < 0 || orbitIndex >= formula.getRunes().size() - 1) return;

        FormulaContext parentContext = new FormulaContext(level(), formula, caster, caster.getMainHandItem());
        FormulaContext childContext  = parentContext.makeChildContext(orbitIndex + 1);
        new FormulaResolver(childContext).onResolveEffect(level(), hit);
        this.discard();
    }

    // ── Impact burst — server-side so all clients see it ─────────────────────
    private void spawnImpactParticles() {
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.WITCH,
                    getX(), getY(), getZ(),
                    8, 0.2, 0.2, 0.2, 0.08);
            serverLevel.sendParticles(ParticleTypes.ENCHANT,
                    getX(), getY(), getZ(),
                    12, 0.3, 0.3, 0.3, 0.3);
            serverLevel.sendParticles(ParticleTypes.POOF,
                    getX(), getY(), getZ(),
                    1, 0, 0, 0, 0);
        }
    }

    private int findOrbitIndex() {
        List<AbstractRune> runes = formula.getRunes();
        for (int i = 0; i < runes.size(); i++) {
            if (runes.get(i) instanceof zcylas.totality.item.magic.rune.effect.OrbitEffect)
                return i;
        }
        return -1;
    }

    public void setFormula(ArcaneFormula formula) { this.formula = formula; }
    public ArcaneFormula getFormula()             { return formula; }

    public void setTrackedEntity(Entity entity) {
        this.trackedEntityId = entity.getId();
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        if (!super.canHitEntity(entity)) return false;
        if (trackedEntityId >= 0 && entity.getId() == trackedEntityId) return false;
        return true;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {}

    @Override
    protected void readAdditionalSaveData(ValueInput input) {}
}