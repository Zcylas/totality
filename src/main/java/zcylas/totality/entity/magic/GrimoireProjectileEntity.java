package zcylas.totality.entity.magic;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.api.magic.context.FormulaContext;
import zcylas.totality.api.magic.context.FormulaResolver;
import zcylas.totality.api.magic.formula.ArcaneFormula;
import zcylas.totality.init.ModEntities;

public class GrimoireProjectileEntity extends Projectile {

    private static final int MAX_LIFETIME_TICKS = 80; // 4 seconds
    private ArcaneFormula formula;
    private int ticksAlive = 0;

    public GrimoireProjectileEntity(EntityType<? extends GrimoireProjectileEntity> type, Level level) {
        super(type, level);
    }

    public GrimoireProjectileEntity(Level level, LivingEntity owner, ArcaneFormula formula) {
        super(ModEntities.GRIMOIRE_PROJECTILE, level);
        this.formula = formula;
        this.setOwner(owner);
        this.setPos(
                owner.getX(),
                owner.getEyeY() - 0.1,
                owner.getZ());
    }

    @Override
    protected void defineSynchedData(
            net.minecraft.network.syncher.SynchedEntityData.Builder builder) {}

    @Override
    public void tick() {
        super.tick();

        ticksAlive++;
        if (ticksAlive >= MAX_LIFETIME_TICKS) {
            this.discard();
            return;
        }

        // Particle trail — client side only
        if (this.level().isClientSide()) {
            this.level().addParticle(ParticleTypes.COPPER_FIRE_FLAME,
                    getX(), getY(), getZ(),
                    (Math.random() - 0.5) * 0.1,  // small random X spread
                    (Math.random() - 0.5) * 0.1,  // small random Y spread
                    (Math.random() - 0.5) * 0.1); // small random Z spread
            this.level().addParticle(ParticleTypes.HAPPY_VILLAGER,
                    getX(), getY(), getZ(), 0, 0, 0);
        }

        // Collision check
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS) {
            onHit(hit);
        }

        // Move
        Vec3 velocity = this.getDeltaMovement();
        this.setPos(
                this.getX() + velocity.x,
                this.getY() + velocity.y,
                this.getZ() + velocity.z);
        this.updateRotation();
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        if (formula == null) { this.discard(); return; }
        if (!(level() instanceof ServerLevel)) { this.discard(); return; }

        if (getOwner() instanceof LivingEntity caster) {
            FormulaContext context = new FormulaContext(
                    level(), formula, caster, caster.getMainHandItem());
            FormulaResolver resolver = new FormulaResolver(context);
            resolver.onResolveEffect(level(), hit);
        }
        this.discard();
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (formula == null) { this.discard(); return; }
        if (!(level() instanceof ServerLevel)) { this.discard(); return; }

        if (getOwner() instanceof LivingEntity caster) {
            FormulaContext context = new FormulaContext(
                    level(), formula, caster, caster.getMainHandItem());
            FormulaResolver resolver = new FormulaResolver(context);
            resolver.onResolveEffect(level(), hit);
        }
        this.discard();
    }

    public void setFormula(ArcaneFormula formula) {
        this.formula = formula;
    }

    public ArcaneFormula getFormula() {
        return formula;
    }

    @Override
    protected void addAdditionalSaveData(
            net.minecraft.world.level.storage.ValueOutput output) {}

    @Override
    protected void readAdditionalSaveData(
            net.minecraft.world.level.storage.ValueInput input) {}
}