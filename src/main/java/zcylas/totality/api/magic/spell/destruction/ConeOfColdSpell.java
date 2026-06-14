package zcylas.totality.api.magic.spell.destruction;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.ability.AbilityContext;
import zcylas.totality.api.combat.condition.ConditionComponent;
import zcylas.totality.api.combat.condition.Conditions;
import zcylas.totality.api.combat.damage.DamageFlags;
import zcylas.totality.api.combat.damage.DamageTypes;
import zcylas.totality.api.combat.damage.TotalityDamage;
import zcylas.totality.api.dice.Dice;
import zcylas.totality.api.dice.RollOutcome;
import zcylas.totality.api.dice.RollType;
import zcylas.totality.api.magic.spell.CastType;
import zcylas.totality.api.magic.spell.Spell;
import zcylas.totality.api.magic.spell.SpellActionType;
import zcylas.totality.api.magic.spell.SpellComponent;
import zcylas.totality.api.magic.spell.SpellMaterial;
import zcylas.totality.api.magic.spell.SpellSchool;
import zcylas.totality.api.rpg.combat.SavingThrow;
import zcylas.totality.api.rpg.stats.AbilityScore;

import java.util.EnumSet;
import java.util.List;

/**
 * Cone of Cold — Conjuration, 5th level. V, S, M (a small crystal or glass cone).
 *
 * A blast of cold air erupts in a 60-foot cone. Each creature in the area
 * must make a CON saving throw. Fail: 8d8 Cold + CHILLED. Save: half, no chill.
 *
 * Material is replaceable (no gold cost).
 */
public class ConeOfColdSpell extends Spell {

    private static final double CONE_LENGTH  = 18.0;  // 60ft ≈ 18 blocks
    private static final double CONE_DOT     = 0.75;  // ~41° half-angle
    private static final int    DICE_COUNT   = 8;

    public ConeOfColdSpell() {
        super(
                Identifier.fromNamespaceAndPath("totality", "cone_of_cold"),
                "Cone of Cold",
                "A blast of cold air erupts in an 18-block cone. CON save vs your spell DC. " +
                        "Fail: 8d8 Cold damage + Chilled. Save: half damage.",
                Type.ACTIVE,
                5,
                SpellSchool.CONJURATION,
                false,
                false,
                SpellActionType.ACTION,
                EnumSet.of(SpellComponent.VERBAL, SpellComponent.SOMATIC, SpellComponent.MATERIAL),
                SpellMaterial.replaceable("a small crystal or glass cone"),
                CastType.INSTANT,
                0,
                200,   // 10s cooldown — powerful 5th level
                Identifier.fromNamespaceAndPath("totality", "textures/ability/spell/cone_of_cold.png"),
                "The breath of winter itself."
        );
    }

    @Override public boolean isDefault() { return true; }

    @Override
    public boolean canActivate(ServerPlayer player, @Nullable AbilityContext ctx) {
        return checkMaterials(player);
    }

    @Override
    public void onActivate(ServerPlayer player, @Nullable AbilityContext ctx) {
        if (!(player.level() instanceof ServerLevel sl)) return;
        consumeMaterials(player);

        Vec3 eye  = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        int  dc   = getSpellSaveDc(player);

        // Particle cone visual
        for (double d = 0.5; d <= CONE_LENGTH; d += 0.6) {
            double radius = d * 0.45;
            int pts = Math.max(4, (int)(radius * 5));
            for (int p = 0; p < pts; p++) {
                double angle = 2 * Math.PI * p / pts;
                // Build perpendicular vectors to look
                Vec3 perp1 = look.cross(new Vec3(0, 1, 0)).normalize();
                Vec3 perp2 = look.cross(perp1).normalize();
                Vec3 offset = perp1.scale(Math.cos(angle) * radius)
                        .add(perp2.scale(Math.sin(angle) * radius));
                Vec3 pos = eye.add(look.scale(d)).add(offset);
                sl.sendParticles(ParticleTypes.SNOWFLAKE,
                        pos.x, pos.y, pos.z, 1, 0.05, 0.05, 0.05, 0.01);
            }
        }

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.POWDER_SNOW_BREAK, SoundSource.PLAYERS, 1.5f, 0.6f);

        // Damage targets in cone
        List<LivingEntity> targets = sl.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(CONE_LENGTH));

        for (LivingEntity target : targets) {
            if (target == player) continue;
            Vec3 toTarget = target.getBoundingBox().getCenter().subtract(eye);
            if (toTarget.length() > CONE_LENGTH) continue;
            if (toTarget.normalize().dot(look) < CONE_DOT) continue;

            int raw = 0;
            for (int i = 0; i < DICE_COUNT; i++) raw += Dice.D8.roll(player.getRandom());

            RollOutcome outcome = SavingThrow.roll(target, AbilityScore.CON, dc, RollType.NORMAL);
            float damage = outcome.isSuccess() ? raw / 2f : raw;
            TotalityDamage.hurt(target, player, DamageTypes.FROST, damage, DamageFlags.MAGICAL);

            if (!outcome.isSuccess()) {
                ConditionComponent.apply(target, Conditions.CHILLED, 60, player);
            }
        }
    }
}