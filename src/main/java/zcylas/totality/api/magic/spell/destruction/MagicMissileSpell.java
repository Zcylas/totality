package zcylas.totality.api.magic.spell.destruction;

import net.minecraft.resources.Identifier;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.ability.AbilityContext;
import zcylas.totality.api.combat.damage.DamageFlags;
import zcylas.totality.api.combat.damage.DamageTypes;
import zcylas.totality.api.combat.damage.TotalityDamage;
import zcylas.totality.api.magic.spell.CastType;
import zcylas.totality.api.magic.spell.Spell;
import zcylas.totality.api.magic.spell.SpellActionType;
import zcylas.totality.api.magic.spell.SpellComponent;
import zcylas.totality.api.magic.spell.SpellSchool;

import java.util.EnumSet;

/**
 * Magic Missile — Evocation, 1st level. V, S.
 * Three darts that automatically hit the nearest target in look direction.
 * Each dart: 1d4 + 1 Force. No attack roll, no save.
 */
public class MagicMissileSpell extends Spell {

    private static final int    DART_COUNT = 3;
    private static final double MAX_RANGE  = 30.0;

    public MagicMissileSpell() {
        super(
                Identifier.fromNamespaceAndPath("totality", "magic_missile"),
                "Magic Missile",
                "Three darts of magical force automatically strike a target within range. " +
                        "No attack roll. Each dart deals 1d4 + 1 Force damage.",
                Type.ACTIVE, 1, SpellSchool.DESTRUCTION, false, false,
                SpellActionType.ACTION,
                EnumSet.of(SpellComponent.VERBAL, SpellComponent.SOMATIC),
                null, CastType.INSTANT, 0,
                60,
                Identifier.fromNamespaceAndPath("totality", "textures/ability/spell/magic_missile.png"),
                "Unerring, unavoidable force."
        );
    }

    @Override public boolean isDefault() { return true; }

    @Override
    public boolean canActivate(ServerPlayer player, @Nullable AbilityContext context) {
        return true;
    }

    @Override
    public void onActivate(ServerPlayer player, @Nullable AbilityContext context) {
        if (!(player.level() instanceof ServerLevel sl)) return;

        Vec3 eye  = player.getEyePosition();
        Vec3 look = player.getLookAngle();

        // Find nearest entity in look direction
        LivingEntity target = null;
        double best = MAX_RANGE;
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(MAX_RANGE))) {
            if (e == player) continue;
            Vec3 toEnt = e.getBoundingBox().getCenter().subtract(eye);
            double dist = toEnt.length();
            if (dist > MAX_RANGE) continue;
            if (toEnt.normalize().dot(look) < 0.97) continue;
            if (dist < best) { best = dist; target = e; }
        }
        if (target == null) return;
        final LivingEntity finalTarget = target;

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.8f, 1.8f);

        Vec3 start = player.getEyePosition();
        Vec3 targetCenter = finalTarget.getBoundingBox().getCenter();
        Vec3 baseDir = targetCenter.subtract(start);
        int steps = Math.max(4, (int)(baseDir.length() * 4));

        for (int i = 0; i < DART_COUNT; i++) {
            // Each dart gets a small random spread so they're visually distinct
            double spread = 0.18;
            Vec3 dartDir = baseDir.add(
                    (player.getRandom().nextDouble() - 0.5) * spread * baseDir.length(),
                    (player.getRandom().nextDouble() - 0.5) * spread * baseDir.length(),
                    (player.getRandom().nextDouble() - 0.5) * spread * baseDir.length());

            // Visual: stream of gold sparkle particles for this dart
            for (int s = 0; s <= steps; s++) {
                double t = (double) s / steps;
                Vec3 pos = start.add(dartDir.scale(t));
                sl.sendParticles(ParticleTypes.ENCHANTED_HIT,
                        pos.x, pos.y + (i - 1) * 0.1, pos.z,
                        1, 0.03, 0.03, 0.03, 0.0);
            }
            // Damage: auto-hit, 1d4+1 force per dart
            int damage = player.getRandom().nextInt(4) + 1 + 1;
            TotalityDamage.hurt(finalTarget, player, DamageTypes.FORCE, damage, DamageFlags.MAGICAL);
        }
    }
}