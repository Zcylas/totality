package zcylas.totality.entity.magic;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import zcylas.totality.api.magic.context.FormulaContext;
import zcylas.totality.api.magic.context.FormulaResolver;
import zcylas.totality.api.magic.formula.ArcaneFormula;
import zcylas.totality.api.magic.formula.FormulaStats;
import zcylas.totality.init.ModEntities;

import org.jspecify.annotations.Nullable;

public class LingerEntity extends Entity {

    private ArcaneFormula formula;
    private LivingEntity caster;
    private int effectIndex; // index to start resolving from
    private int duration;    // ticks remaining
    private int castInterval;// ticks between casts
    private float radius;
    private boolean sensitive; // true = target blocks, false = target entities
    private boolean noGravity;
    private boolean landed = false;
    private int totalProcs = 0;
    private static final int MAX_PROCS = 100;

    private int tickCounter = 0;

    public LingerEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public LingerEntity(Level level, LivingEntity caster, ArcaneFormula formula,
                        int effectIndex, FormulaStats stats, int duration) {
        this(ModEntities.LINGER_ENTITY, level);
        this.caster      = caster;
        this.formula     = formula;
        this.effectIndex = effectIndex;
        this.duration    = duration;
        this.radius      = 2.0f + (float) stats.getAoeRadius();
        this.sensitive   = stats.isSensitive();
        this.noGravity   = stats.getAmpCount() < 0; // dampen = no gravity
        this.castInterval = Math.max(5, 20 - (int)(stats.getAccelerationModifier() * 5));
        this.setNoGravity(this.noGravity);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;
        if (!(level() instanceof ServerLevel serverLevel)) return;

        // Gravity — fall until hitting ground unless noGravity
        if (!noGravity && !landed) {
            boolean isOnGround = !level().getBlockState(blockPosition()).isAir();
            if (isOnGround) {
                landed = true;
                setDeltaMovement(Vec3.ZERO);
            } else {
                setDeltaMovement(0, -0.2, 0);
            }
        }

        duration--;
        if (duration <= 0) {
            this.discard();
            return;
        }

        tickCounter++;
        if (tickCounter < castInterval) return;
        tickCounter = 0;

        if (sensitive) {
            resolveBlocks(serverLevel);
        } else {
            resolveEntities(serverLevel);
        }
    }

    private void resolveEntities(ServerLevel level) {
        AABB box = getBoundingBox().inflate(radius);
        int count = 0;
        for (Entity entity : level.getEntities(this, box, e -> e instanceof LivingEntity && e.isAlive())) {
            FormulaContext parentContext = new FormulaContext(level, formula, caster, caster.getMainHandItem());
            FormulaContext childContext = parentContext.makeChildContext(effectIndex);
            new FormulaResolver(childContext).onResolveEffect(level, new EntityHitResult(entity));
            count++;
            if (count > 5) break;
        }
        totalProcs += count;
        if (totalProcs >= MAX_PROCS) this.discard();
    }

    private void resolveBlocks(ServerLevel level) {
        BlockPos center = blockPosition();
        int r = (int) Math.ceil(radius);
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-r, -r, -r),
                center.offset(r, r, r))) {
            if (!level.getBlockState(pos).isAir()) {
                FormulaContext parentContext = new FormulaContext(level, formula, caster, caster.getMainHandItem());
                FormulaContext childContext = parentContext.makeChildContext(effectIndex);
                Vec3 hitVec = Vec3.atCenterOf(pos);
                new FormulaResolver(childContext).onResolveEffect(level,
                        new BlockHitResult(hitVec, net.minecraft.core.Direction.UP, pos, false));
            }
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.duration     = input.getIntOr("Duration", 0);
        this.castInterval = input.getIntOr("CastInterval", 20);
        this.radius       = input.getFloatOr("Radius", 2.0f);
        this.sensitive    = input.getBooleanOr("Sensitive", false);
        this.noGravity    = input.getBooleanOr("NoGravity2", false);
        this.effectIndex  = input.getIntOr("EffectIndex", 1);
        this.landed = input.getBooleanOr("Landed", false);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("Duration", duration);
        output.putInt("CastInterval", castInterval);
        output.putFloat("Radius", radius);
        output.putBoolean("Sensitive", sensitive);
        output.putBoolean("NoGravity2", noGravity);
        output.putInt("EffectIndex", effectIndex);
        output.putBoolean("Landed", landed);
    }

    @Override
    public boolean hurtServer(net.minecraft.server.level.ServerLevel level,
                              net.minecraft.world.damagesource.DamageSource source,
                              float damage) {
        return false; // Linger entity cannot be damaged
    }
}