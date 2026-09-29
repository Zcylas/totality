package zcylas.totality.entity.magic;

import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.random.WeightedList;
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
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.combat.damage.DamageFlags;
import zcylas.totality.api.combat.damage.DamageTypes;
import zcylas.totality.api.combat.damage.TotalityDamage;
import zcylas.totality.api.dice.Dice;
import zcylas.totality.api.dice.RollOutcome;
import zcylas.totality.api.dice.RollType;
import zcylas.totality.api.rpg.combat.SavingThrow;
import zcylas.totality.api.rpg.stats.AbilityScore;
import zcylas.totality.init.ModEntities;
import zcylas.totality.init.ModParticles;

import java.util.List;
import java.util.Optional;

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
    /** Blast radius in blocks (D&D: a 20-foot-radius sphere); also the size the detonation visuals bloom out to. */
    public static final float BLAST_RADIUS = 6.0f;
    private static final int   DICE_COUNT  = 8;
    private static final Dice  DAMAGE_DIE  = Dice.D6;
    private static final int   MAX_LIFETIME = 100; // 5 seconds
    /** Visual only: no trail particles closer than this to the cast point (keeps the caster's own view clear). */
    private static final double TRAIL_CLEARANCE = 2.5;

    private int ticksAlive = 0;
    private int spellSaveDc = 14; // computed from caster in create()
    /** Client only: where this client first saw the fireball (the cast point), for the ignition and the renderer. */
    @Nullable private Vec3 visualOrigin = null;

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
        if (ticksAlive >= MAX_LIFETIME) {
            // Expired in the air: the fireball gutters out (a small puff) instead of vanishing.
            if (level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ModParticles.FIREBALL_DETONATION, getX(), getY(), getZ(), 0, -1, 0, 0, 1.0);
            }
            this.discard();
            return;
        }

        Vec3 start    = this.position();
        Vec3 velocity = this.getDeltaMovement();
        Vec3 end      = start.add(velocity);

        if (level().isClientSide() && visualOrigin == null) {
            visualOrigin = start;
            FireballVfx.castBurst(level(), start, travelDirection());
        }

        var blockHit = level().clip(new net.minecraft.world.level.ClipContext(
                start, end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, this));

        HitResult hit = blockHit.getType() != HitResult.Type.MISS ? blockHit
                : net.minecraft.world.entity.projectile.ProjectileUtil.getHitResultOnMoveVector(
                this, this::canHitEntity);

        if (level().isClientSide()) {
            // The trail starts 2.5 blocks out from the cast point: closer, it fills the caster's first-person view.
            Vec3 trailEnd = hit.getType() != HitResult.Type.MISS ? hit.getLocation() : end;
            if (trailEnd.distanceTo(visualOrigin) > TRAIL_CLEARANCE) {
                Vec3 trailStart = start.distanceTo(visualOrigin) >= TRAIL_CLEARANCE ? start
                        : visualOrigin.add(travelDirection().scale(TRAIL_CLEARANCE));
                FireballVfx.trail(level(), trailStart, trailEnd, travelDirection());
            }
        }

        if (hit.getType() != HitResult.Type.MISS) {
            // Detonate where it actually hit, not where this tick's move started (up to 1.2 blocks short).
            Vec3 impact = impactPoint(hit, start, velocity);
            this.setPos(impact.x, impact.y, impact.z);
            onHit(hit);
            return;
        }

        this.setPos(end.x, end.y, end.z);
        this.updateRotation();
    }

    /**
     * The blast centre for a hit on this tick's move from {@code start} by {@code velocity}: on a block, the hit point
     * backed off the surface by a quarter block (so the blast's exposure rays start in the open); on an entity, where
     * the path enters its (projectile-inflated) box, or its centre if the path starts inside it.
     */
    static Vec3 impactPoint(HitResult hit, Vec3 start, Vec3 velocity) {
        Vec3 dir = velocity.lengthSqr() < 1.0E-8 ? Vec3.ZERO : velocity.normalize();
        if (hit instanceof EntityHitResult entityHit) {
            AABB box = entityHit.getEntity().getBoundingBox().inflate(0.25);
            return box.clip(start, start.add(velocity)).orElse(box.getCenter());
        }
        return hit.getLocation().subtract(dir.scale(0.25));
    }

    /** The direction of flight (the look direction for the first tick of a stationary fireball). */
    private Vec3 travelDirection() {
        Vec3 v = getDeltaMovement();
        return v.lengthSqr() < 1.0E-8 ? Vec3.directionFromRotation(getXRot(), getYRot()) : v.normalize();
    }

    /** Client only: the point this fireball was first seen at (null before its first client tick). */
    @Nullable
    public Vec3 visualOrigin() {
        return visualOrigin;
    }

    @Override
    protected void onHitEntity(EntityHitResult hit)  { explode(); }
    @Override
    protected void onHitBlock(BlockHitResult hit)     { explode(); }

    private void explode() {
        if (!(level() instanceof ServerLevel serverLevel)) { this.discard(); return; }
        if (!(getOwner() instanceof LivingEntity caster))  { this.discard(); return; }

        double x = getX(), y = getY(), z = getZ();

        // ── Presentation only ────────────────────────────────────────────────
        // No vanilla explosion is created: ServerExplosion would add its own damage (turned into Force damage by
        // VanillaDamageInterceptor), knockback, and pushes to every entity nearby, projectiles included. Only what
        // it presents is kept: the explosion game event, and the explosion packet (the blast's sound and the Fireball
        // detonation emitter, FireballVfx) sent to every player within 64 blocks, as vanilla sends it, but carrying
        // no knockback. Fireball's only damage is the 8d6 Fire below.
        Vec3 centre = new Vec3(x, y, z);
        serverLevel.gameEvent(null, GameEvent.EXPLODE, centre);
        sendDetonation(serverLevel, centre);

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
        // Every creature in the sphere, the caster included (D&D: "each creature in a 20-foot-radius sphere").
        List<LivingEntity> targets = serverLevel.getEntitiesOfClass(LivingEntity.class, box,
                e -> e.distanceTo(this) <= BLAST_RADIUS);

        float dc = this.entityData.get(SPELL_SAVE_DC);

        for (LivingEntity target : targets) {
            int raw = 0;
            for (int i = 0; i < DICE_COUNT; i++) raw += DAMAGE_DIE.roll(target.getRandom());

            RollOutcome outcome = SavingThrow.roll(target, AbilityScore.DEX, (int) dc, RollType.NORMAL);
            float damage = outcome.isSuccess() ? raw / 2f : raw;

            // The caster's own hit has no attacker: as an attack by themself, vanilla would gate it on PvP and shove
            // them in a random direction. It is the same single Fire calculation, saves and resistances as everyone's.
            TotalityDamage.hurt(target, target == caster ? null : caster, DamageTypes.FIRE, damage,
                    DamageFlags.IS_AOE, DamageFlags.NO_CONDITIONS);
        }

        this.discard();
    }

    /** The explosion packet a vanilla explosion sends, minus its knockback: sound and detonation emitter only. */
    private static void sendDetonation(ServerLevel level, Vec3 centre) {
        ClientboundExplodePacket packet = new ClientboundExplodePacket(centre, BLAST_RADIUS * 0.8f, 0, Optional.empty(),
                ModParticles.FIREBALL_DETONATION, SoundEvents.GENERIC_EXPLODE, WeightedList.of());
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(centre) < 4096.0) player.connection.send(packet);
        }
    }

    @Override protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput o) {}
    @Override protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput i) {}
}