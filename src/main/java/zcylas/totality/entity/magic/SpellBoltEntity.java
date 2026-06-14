package zcylas.totality.entity.magic;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.combat.damage.DamageFlags;
import zcylas.totality.api.combat.damage.DamageTypeRegistry;
import zcylas.totality.api.combat.damage.DamageTypes;
import zcylas.totality.api.combat.damage.TotalityDamageType;
import zcylas.totality.api.dice.Dice;
import zcylas.totality.api.dice.RollType;
import zcylas.totality.api.magic.spell.SpellBoltOnHitRegistry;
import zcylas.totality.api.rpg.combat.CombatResolver;
import zcylas.totality.api.rpg.combat.CombatResolver.SpellAttackType;
import zcylas.totality.api.rpg.stats.AbilityScore;
import zcylas.totality.init.ModEntities;

import java.util.function.Consumer;

/**
 * Generic traveling bolt projectile for D&D-style spell attacks.
 *
 * All bolt-type spells (Eldritch Blast, Firebolt, Ray of Frost, Magic Missile, etc.)
 * use this single entity — they differ only in color, damage type, dice, and spellcasting ability.
 *
 * Color is synced via {@link SynchedEntityData} so the client can render the correct
 * {@link DustParticleOptions} trail without knowing anything about spell logic.
 *
 * On-hit effects (e.g. slow for Ray of Frost) are looked up from {@link SpellBoltOnHitRegistry}
 * by a nullable {@link Identifier}. Simple damage spells pass null.
 *
 * Usage:
 * <pre>
 *   SpellBoltEntity bolt = SpellBoltEntity.create(
 *       level, player,
 *       "Eldritch Blast", DamageTypes.FORCE,
 *       1, Dice.D10, AbilityScore.CHA,
 *       0xAA00FF,   // purple
 *       null        // no on-hit effect
 *   );
 *   level.addFreshEntity(bolt);
 * </pre>
 */
public class SpellBoltEntity extends Projectile {

    // ── Synced data ───────────────────────────────────────────────────────────

    /** RGB color (no alpha) — client reads this to build DustParticleOptions. */
    private static final EntityDataAccessor<Integer> BOLT_COLOR =
            SynchedEntityData.defineId(SpellBoltEntity.class, EntityDataSerializers.INT);

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final int   MAX_LIFETIME_TICKS = 60;   // 3 seconds
    private static final float BOLT_SPEED          = 2.5f; // blocks/tick

    // ── Server-side spell parameters ──────────────────────────────────────────

    private String            spellName            = "";
    private AbilityScore      spellcastingAbility  = AbilityScore.INT;
    private SpellAttackType   attackType           = SpellAttackType.RANGED;
    private RollType          rollType             = RollType.NORMAL;
    private int               diceCount            = 1;
    private Dice              damageDie            = Dice.D10;
    private TotalityDamageType damageType          = DamageTypes.FORCE;
    @Nullable private Identifier onHitEffectId     = null;

    // ── Sounds — server-side only, MC broadcasts to nearby clients ────────────
    @Nullable private SoundEvent castSound         = null;
    @Nullable private SoundEvent impactSound       = null;

    private int ticksAlive = 0;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Required by EntityType — do not call directly, use {@link #create}. */
    public SpellBoltEntity(EntityType<? extends SpellBoltEntity> type, Level level) {
        super(type, level);
    }

    private SpellBoltEntity(Level level, LivingEntity caster) {
        super(ModEntities.SPELL_BOLT, level);
        this.setOwner(caster);
        // Spawn 1.5 blocks ahead of the caster's eye so particles don't fill the screen
        Vec3 look = caster.getLookAngle();
        this.setPos(
                caster.getX() + look.x * 1.5,
                caster.getEyeY() - 0.1 + look.y * 1.5,
                caster.getZ() + look.z * 1.5);
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    /**
     * Creates a bolt aimed in the caster's look direction.
     * Call {@code level.addFreshEntity(bolt)} after this.
     *
     * @param color        RGB (no alpha), e.g. 0xAA00FF for purple Eldritch Blast
     * @param onHitEffectId nullable — if non-null, looked up in {@link SpellBoltOnHitRegistry}
     */
    public static SpellBoltEntity create(Level level,
                                         LivingEntity caster,
                                         String spellName,
                                         TotalityDamageType damageType,
                                         int diceCount,
                                         Dice damageDie,
                                         AbilityScore spellcastingAbility,
                                         int color,
                                         @Nullable Identifier onHitEffectId) {
        SpellBoltEntity bolt      = new SpellBoltEntity(level, caster);
        bolt.spellName            = spellName;
        bolt.damageType           = damageType;
        bolt.diceCount            = diceCount;
        bolt.damageDie            = damageDie;
        bolt.spellcastingAbility  = spellcastingAbility;
        bolt.onHitEffectId        = onHitEffectId;
        bolt.entityData.set(BOLT_COLOR, color & 0x00FFFFFF); // strip alpha, store RGB only

        Vec3 look = caster.getLookAngle();
        bolt.setDeltaMovement(look.scale(BOLT_SPEED));
        bolt.updateRotation();

        return bolt;
    }

    /**
     * Sets cast and impact sounds. Returns {@code this} for chaining.
     * The cast sound plays immediately (at the caster's position).
     * The impact sound plays when the bolt hits something.
     *
     * <pre>
     *   SpellBoltEntity bolt = SpellBoltEntity.create(...)
     *           .withSounds(SoundEvents.EVOKER_CAST_SPELL, SoundEvents.EVOKER_FANGS_ATTACK);
     * </pre>
     */
    public SpellBoltEntity withSounds(@Nullable SoundEvent castSound,
                                      @Nullable SoundEvent impactSound) {
        this.castSound   = castSound;
        this.impactSound = impactSound;
        if (castSound != null) {
            level().playSound(null,
                    getX(), getY(), getZ(),
                    castSound, SoundSource.PLAYERS,
                    1.0f, 1.0f);
        }
        return this;
    }

    // ── SynchedEntityData ─────────────────────────────────────────────────────

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(BOLT_COLOR, 0xAA00FF); // default purple (Eldritch Blast)
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        ticksAlive++;

        if (ticksAlive >= MAX_LIFETIME_TICKS) {
            this.discard();
            return;
        }

        // ── Client: colored particle trail ────────────────────────────────────
        if (level().isClientSide()) {
            DustParticleOptions dust = buildDust(1.0f);

            // Core bolt — tight center
            level().addParticle(dust, getX(), getY(), getZ(), 0, 0, 0);

            // Outer glow — slight spread
            level().addParticle(dust,
                    getX() + (Math.random() - 0.5) * 0.15,
                    getY() + (Math.random() - 0.5) * 0.15,
                    getZ() + (Math.random() - 0.5) * 0.15,
                    0, 0, 0);

            // Enchant sparkle
            level().addParticle(ParticleTypes.ENCHANT,
                    getX() + (Math.random() - 0.5) * 0.2,
                    getY() + (Math.random() - 0.5) * 0.2,
                    getZ() + (Math.random() - 0.5) * 0.2,
                    (Math.random() - 0.5) * 0.05, 0, (Math.random() - 0.5) * 0.05);
        }

        // ── Movement & collision ──────────────────────────────────────────────
        Vec3 start    = this.position();
        Vec3 velocity = this.getDeltaMovement();
        Vec3 end      = start.add(velocity);

        BlockHitResult blockHit = level().clip(new ClipContext(
                start, end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                this));

        HitResult hit = blockHit.getType() != HitResult.Type.MISS
                ? blockHit
                : ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);

        if (hit.getType() != HitResult.Type.MISS) {
            onHit(hit);
            return;
        }

        this.setPos(end.x, end.y, end.z);
        this.updateRotation();
    }

    // ── Hit resolution ────────────────────────────────────────────────────────

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);

        if (!(level() instanceof ServerLevel serverLevel)) { this.discard(); return; }
        if (!(getOwner() instanceof LivingEntity caster)) { this.discard(); return; }

        // Fire bolt ignites dropped items and item frames — per D&D rules
        if (isFireType() && hit.getEntity() instanceof ItemEntity item) {
            item.setRemainingFireTicks(300); // 15 seconds
            spawnImpactParticles();
            this.discard();
            return;
        }

        if (!(hit.getEntity() instanceof LivingEntity target)) { this.discard(); return; }

        // Spell attack roll + damage. NO_CONDITIONS: bolt spells manage conditions
        // themselves via SpellBoltOnHitRegistry, not via applyFromDamageType.
        CombatResolver.resolveSpellAttack(
                caster, target, spellName,
                spellcastingAbility, attackType, rollType,
                diceCount, damageDie, damageType,
                DamageFlags.NO_CONDITIONS);

        if (onHitEffectId != null) {
            Consumer<LivingEntity> effect = SpellBoltOnHitRegistry.get(onHitEffectId);
            if (effect != null) effect.accept(target);
        }

        spawnImpactParticles();
        this.discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);

        // Fire bolt ignites flammable blocks on hit — per D&D rules
        if (isFireType() && level() instanceof ServerLevel serverLevel) {
            var firePos = hit.getBlockPos().relative(hit.getDirection());
            if (BaseFireBlock.canBePlacedAt(serverLevel, firePos, hit.getDirection())) {
                serverLevel.setBlock(firePos,
                        BaseFireBlock.getState(serverLevel, firePos), 3);
            }
        }

        spawnImpactParticles();
        this.discard();
    }

    private boolean isFireType() {
        return damageType != null &&
                damageType.getId().equals(DamageTypes.FIRE.getId());
    }

    // ── Particles ─────────────────────────────────────────────────────────────

    /** Impact burst — sent from server so all nearby clients see it. */
    private void spawnImpactParticles() {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        serverLevel.sendParticles(buildDust(2.0f),
                getX(), getY(), getZ(), 20, 0.3, 0.3, 0.3, 0.05);
        serverLevel.sendParticles(ParticleTypes.ENCHANT,
                getX(), getY(), getZ(), 15, 0.4, 0.4, 0.4, 0.4);
        serverLevel.sendParticles(ParticleTypes.POOF,
                getX(), getY(), getZ(), 1, 0, 0, 0, 0);
        if (impactSound != null) {
            serverLevel.playSound(null,
                    getX(), getY(), getZ(),
                    impactSound, SoundSource.PLAYERS,
                    1.0f, 1.0f);
        }
    }

    /** Unpacks the synced RGB int into a {@link DustParticleOptions}. */
    private DustParticleOptions buildDust(float size) {
        return new DustParticleOptions(this.entityData.get(BOLT_COLOR), size);
    }

    // ── Save / Load ───────────────────────────────────────────────────────────
    // Bolts live at most 3 seconds — save/load is not meaningful in practice.

    @Override
    protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {}

    @Override
    protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {}
}