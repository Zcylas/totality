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
    private boolean sensitive = false;

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

        if (this.level().isClientSide()) {
            // ── Main trail — purple witch particles ───────────────────────────
            this.level().addParticle(ParticleTypes.WITCH,
                    getX(), getY(), getZ(),
                    (Math.random() - 0.5) * 0.05,
                    (Math.random() - 0.5) * 0.05,
                    (Math.random() - 0.5) * 0.05);

            // ── Sparkle trail — enchant particles with slight spread ───────────
            this.level().addParticle(ParticleTypes.ENCHANT,
                    getX() + (Math.random() - 0.5) * 0.2,
                    getY() + (Math.random() - 0.5) * 0.2,
                    getZ() + (Math.random() - 0.5) * 0.2,
                    (Math.random() - 0.5) * 0.1,
                    Math.random() * 0.05,
                    (Math.random() - 0.5) * 0.1);
        }

        Vec3 start = this.position();
        Vec3 velocity = this.getDeltaMovement();
        Vec3 end = start.add(velocity);

        // Sensitive uses OUTLINE to hit non-solid blocks like grass/flowers
        net.minecraft.world.level.ClipContext clipContext = new net.minecraft.world.level.ClipContext(
                start, end,
                sensitive ? net.minecraft.world.level.ClipContext.Block.OUTLINE
                        : net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                this);
        BlockHitResult blockHit = this.level().clip(clipContext);

        HitResult hit;
        if (blockHit.getType() != HitResult.Type.MISS) {
            hit = blockHit;
        } else {
            hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        }

        if (hit.getType() != HitResult.Type.MISS) {
            onHit(hit);
            return;
        }

        this.setPos(end.x, end.y, end.z);
        this.updateRotation();
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        spawnImpactParticles();
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
        spawnImpactParticles();
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

    // ── Impact burst — server-side so all clients see it ─────────────────────a
    private void spawnImpactParticles() {
        if (level() instanceof ServerLevel serverLevel) {
            // Purple burst
            serverLevel.sendParticles(ParticleTypes.WITCH,
                    getX(), getY(), getZ(),
                    12, 0.3, 0.3, 0.3, 0.1);
            // Sparkle burst
            serverLevel.sendParticles(ParticleTypes.ENCHANT,
                    getX(), getY(), getZ(),
                    20, 0.5, 0.5, 0.5, 0.5);
            // Flash center
            serverLevel.sendParticles(ParticleTypes.POOF,
                    getX(), getY(), getZ(),
                    1, 0, 0, 0, 0);
        }
    }

    public void setFormula(ArcaneFormula formula) { this.formula = formula; }
    public ArcaneFormula getFormula() { return formula; }
    public void setSensitive(boolean sensitive) { this.sensitive = sensitive; }

    @Override
    protected void addAdditionalSaveData(
            net.minecraft.world.level.storage.ValueOutput output) {}

    @Override
    protected void readAdditionalSaveData(
            net.minecraft.world.level.storage.ValueInput input) {}
}