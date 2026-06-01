package zcylas.totality.api.core.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.api.ability.AbilityComponents;
import zcylas.totality.api.ability.AbilityRegistry;
import zcylas.totality.api.client.particles.TotalityParticles;
import zcylas.totality.api.combat.damage.DamageFlags;
import zcylas.totality.api.combat.damage.DamageTypes;
import zcylas.totality.api.combat.damage.TotalityDamage;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.rpg.stats.AbilityScore;
import zcylas.totality.api.rpg.stats.StatsComponents;

public final class GroundSlamImpact {

    public static void perform(ServerPlayer player, double startY) {
        ServerLevel level = player.level();
        BlockPos center = player.blockPosition();

        double fallDistance = Math.max(0.0D, startY - player.getY());

        if (fallDistance < MovementStaminaCosts.GROUND_SLAM_MIN_FALL_DISTANCE) {
            player.fallDistance = 0.0F;
            return;
        }

        int strMod = StatsComponents.getStats(player).getModifier(AbilityScore.STR);
        int conMod = StatsComponents.getStats(player).getModifier(AbilityScore.CON);

        double radius = calculateRadius(fallDistance, strMod);
        float enemyDamage = calculateEnemyDamage(fallDistance, strMod);
        int craterRadius = calculateCraterRadius(fallDistance, strMod);
        float hardnessLimit = calculateHardnessLimit(fallDistance, strMod);
        float selfDamage = calculateSelfDamage(
                fallDistance,
                conMod,
                hasImpactImmunity(player)
        );

        damageEntities(player, level, radius, enemyDamage, strMod);
        makeCrater(level, center, craterRadius, hardnessLimit);

        // ── Impact particles ──────────────────────────────────────────────────────
        Vec3 impactPos = Vec3.atBottomCenterOf(center);

// Shockwave ring — dust explosion outward
        TotalityParticles.setCount(48);
        TotalityParticles.spawnRing(ParticleTypes.EXPLOSION, level, impactPos, radius * 0.8, 0.2);

// Central burst — large explosion at impact point
        TotalityParticles.setCount(12);
        TotalityParticles.spawnSphere(ParticleTypes.EXPLOSION, level, impactPos, 0.5, 0.3);

// Debris cloud — block dust rising upward
        TotalityParticles.setCount(32);
        TotalityParticles.setVelocity(new Vec3(0, 0.3, 0));
        TotalityParticles.spawnSphere(ParticleTypes.CLOUD, level, impactPos, radius * 0.5, 0.4);

// Ground crack ring — smoke at ground level
        TotalityParticles.setCount(24);
        TotalityParticles.spawnRing(ParticleTypes.LARGE_SMOKE, level, impactPos, radius * 0.5, 0.1);

        if (selfDamage > 0.0F && !player.isCreative()) {
            TotalityDamage.hurt(
                    player, player,
                    DamageTypes.BLUDGEONING,
                    selfDamage,
                    DamageFlags.BYPASS_RESISTANCE
            );
        }

        player.fallDistance = 0.0F;
    }

    private static double calculateRadius(double fallDistance, int strMod) {
        double radius = MovementStaminaCosts.GROUND_SLAM_BASE_RADIUS
                + fallDistance * MovementStaminaCosts.GROUND_SLAM_RADIUS_PER_FALL_BLOCK
                + Math.max(0, strMod) * MovementStaminaCosts.GROUND_SLAM_RADIUS_PER_STR_MOD;

        return Math.min(radius, MovementStaminaCosts.GROUND_SLAM_MAX_RADIUS);
    }

    private static float calculateEnemyDamage(double fallDistance, int strMod) {
        float damage = MovementStaminaCosts.GROUND_SLAM_BASE_DAMAGE
                + (float) fallDistance * MovementStaminaCosts.GROUND_SLAM_DAMAGE_PER_FALL_BLOCK
                + Math.max(0, strMod) * MovementStaminaCosts.GROUND_SLAM_DAMAGE_PER_STR_MOD;

        return Math.min(damage, MovementStaminaCosts.GROUND_SLAM_MAX_DAMAGE);
    }

    private static float calculateSelfDamage(double fallDistance,
                                             int conMod,
                                             boolean impactImmune) {
        if (impactImmune) {
            return 0.0F;
        }

        float damage = MovementStaminaCosts.GROUND_SLAM_BASE_SELF_DAMAGE
                + (float) fallDistance * MovementStaminaCosts.GROUND_SLAM_SELF_DAMAGE_PER_FALL_BLOCK;

        int positiveConMod = Math.max(0, conMod);

        double percentReduction = Math.min(
                MovementStaminaCosts.GROUND_SLAM_SELF_DAMAGE_MAX_CON_PERCENT_REDUCTION,
                positiveConMod * MovementStaminaCosts.GROUND_SLAM_SELF_DAMAGE_CON_PERCENT_PER_MOD
        );

        damage *= (float) (1.0D - percentReduction);
        damage -= positiveConMod * MovementStaminaCosts.GROUND_SLAM_SELF_DAMAGE_CON_FLAT_REDUCTION;

        return Math.max(0.0F, damage);
    }

    private static int calculateCraterRadius(double fallDistance, int strMod) {
        int craterRadius = MovementStaminaCosts.GROUND_SLAM_BASE_CRATER_RADIUS
                + (int) (fallDistance / 6.0D)
                + Math.max(0, strMod) / 4;

        return Math.min(craterRadius, MovementStaminaCosts.GROUND_SLAM_MAX_CRATER_RADIUS);
    }

    private static float calculateHardnessLimit(double fallDistance, int strMod) {
        float hardnessLimit = MovementStaminaCosts.GROUND_SLAM_BASE_HARDNESS_LIMIT
                + Math.max(0, strMod) * MovementStaminaCosts.GROUND_SLAM_HARDNESS_PER_STR_MOD
                + (float) fallDistance * MovementStaminaCosts.GROUND_SLAM_HARDNESS_PER_FALL_BLOCK;

        return Math.min(
                hardnessLimit,
                MovementStaminaCosts.GROUND_SLAM_MAX_HARDNESS_LIMIT
        );
    }

    private static void damageEntities(ServerPlayer player,
                                       ServerLevel level,
                                       double radius,
                                       float damage,
                                       int strMod) {
        AABB box = player.getBoundingBox().inflate(radius);

        for (LivingEntity target : level.getEntitiesOfClass(
                LivingEntity.class,
                box,
                entity -> entity != player && entity.isAlive()
        )) {
            double distance = target.distanceTo(player);
            if (distance > radius) continue;

            float distanceMultiplier = (float) (1.0D - distance / radius);
            float finalDamage = Math.max(1.0F, damage * distanceMultiplier);

            TotalityDamage.hurt(
                    target, player,
                    DamageTypes.BLUDGEONING,
                    finalDamage
            );

            double knockbackPower = 0.65D
                    + distanceMultiplier * 0.9D
                    + Math.max(0, strMod) * 0.05D;

            Vec3 direction = target.position().subtract(player.position());

            if (direction.lengthSqr() < 1.0E-6D) {
                direction = new Vec3(0.0D, 0.0D, 1.0D);
            }

            Vec3 knockback = direction.normalize().scale(knockbackPower);

            target.setDeltaMovement(
                    target.getDeltaMovement().x + knockback.x,
                    target.getDeltaMovement().y
                            + 0.45D
                            + distanceMultiplier * 0.45D
                            + Math.max(0, strMod) * 0.03D,
                    target.getDeltaMovement().z + knockback.z
            );

            target.hurtMarked = true;
        }
    }

    private static void makeCrater(ServerLevel level,
                                   BlockPos center,
                                   int radius,
                                   float hardnessLimit) {

        int depth = Math.max(2, radius);

        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-radius, -depth, -radius),
                center.offset(radius, 0, radius)
        )) {

            double dx = pos.getX() - center.getX();
            double dy = pos.getY() - center.getY();
            double dz = pos.getZ() - center.getZ();

            double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

            if (horizontalDistance > radius) continue;

            // Bowl shape
            double normalized = horizontalDistance / radius;

            double allowedDepth = depth * (1.0D - normalized * normalized);

            if (-dy > allowedDepth) continue;

            var state = level.getBlockState(pos);

            if (state.isAir()) continue;
            if (state.hasBlockEntity()) continue;

            float hardness = state.getDestroySpeed(level, pos);

            if (hardness < 0.0F) continue;
            if (hardness > hardnessLimit) continue;

            level.destroyBlock(pos, false);
        }
    }

    private static boolean hasImpactImmunity(ServerPlayer player) {
        var abilities = AbilityComponents.ABILITIES.get((ComponentProvider) player);

        return abilities.hasAbility(AbilityRegistry.VILTRUMITE_PHYSIOLOGY.getId())
                || abilities.hasAbility(AbilityRegistry.KRYPTONIAN_PHYSIOLOGY.getId());
    }

    private GroundSlamImpact() {}
}