// api/combat/damage/TotalityDamage.java
package zcylas.totality.api.combat.damage;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.Totality;
import zcylas.totality.api.ability.impl.barbarian.BarbarianRageAbility;
import zcylas.totality.client.combat.CombatTextEntry;
import zcylas.totality.networking.combat.CombatTextPayload;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

public final class TotalityDamage {

    private TotalityDamage() {}

    public static void hurt(LivingEntity target,
                            @Nullable LivingEntity source,
                            TotalityDamageType type,
                            float amount,
                            DamageFlags... flags) {

        Set<DamageFlags> flagSet = flags.length > 0
                ? EnumSet.copyOf(Arrays.asList(flags))
                : EnumSet.noneOf(DamageFlags.class);

        // Break invisibility on dealing damage
        if (!flagSet.contains(DamageFlags.SILENT) && source != null) {
            if (source instanceof net.minecraft.server.level.ServerPlayer player) {
                zcylas.totality.api.combat.condition.ConditionComponent comp =
                        zcylas.totality.api.combat.condition.ConditionComponent.get(player);
                comp.remove(zcylas.totality.api.combat.condition.Conditions.INVISIBLE);
                // GREATER_INVISIBLE intentionally not removed
            }
        }

        float finalAmount = amount;
        DamageModifier modifier = null;

        // Apply resistance unless bypassed
        if (!flagSet.contains(DamageFlags.BYPASS_RESISTANCE)) {
            modifier = DamageResistanceComponent.getModifier(target, type);
            if (modifier != null) {
                finalAmount *= DamageModifier.resolve(modifier, flagSet);
            }
        }

        // Zero damage — immune
        if (finalAmount <= 0f) {
            sendCombatText(target, source, CombatTextEntry.TextType.IMMUNE, type, 0, null);
            return;
        }

        // Build vanilla damage source
        DamageSource vanillaSource = buildVanillaSource(target, source, type);

        // Apply
        if (target.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            target.hurtServer(serverLevel, vanillaSource, finalAmount);
        }
        if (target instanceof net.minecraft.server.level.ServerPlayer sp
                && BarbarianRageAbility.isRaging(sp)) {
            BarbarianRageAbility.refreshCombatTimer(sp);
        }

        // Fire floating combat text
        sendCombatText(target, source, resolveTextType(modifier), type,
                finalAmount * zcylas.totality.api.core.rpgutils.RpgDisplayUtils.HP_DISPLAY_MULTIPLIER, null);

        // Apply conditions from damage type
        zcylas.totality.api.combat.condition.ConditionComponent.applyFromDamageType(
                target, source, type, flagSet);
    }

    private static DamageSource buildVanillaSource(LivingEntity target,
                                                   @Nullable LivingEntity source,
                                                   TotalityDamageType type) {
        var level = target.level();
        if (source instanceof net.minecraft.server.level.ServerPlayer player) {
            return level.damageSources().playerAttack(player);
        } else if (source != null) {
            return level.damageSources().mobAttack(source);
        }
        return level.damageSources().magic();
    }

    private static CombatTextEntry.TextType resolveTextType(
            @Nullable DamageModifier modifier) {
        if (modifier instanceof DamageModifier.Resistance)
            return CombatTextEntry.TextType.RESIST;
        if (modifier instanceof DamageModifier.Vulnerability)
            return CombatTextEntry.TextType.VULNERABLE;
        return CombatTextEntry.TextType.DAMAGE;
    }

    private static void sendCombatText(LivingEntity target,
                                       @Nullable LivingEntity source,
                                       CombatTextEntry.TextType textType,
                                       TotalityDamageType damageType,
                                       float amount,
                                       @Nullable String label) {
        if (target.level().isClientSide()) return;
        Vec3 pos = target.position();
        CombatTextPayload payload = new CombatTextPayload(
                textType,
                damageType.getId(),
                amount,
                label,
                pos.x, pos.y, pos.z,
                textType == CombatTextEntry.TextType.RESIST,
                textType == CombatTextEntry.TextType.VULNERABLE,
                target.getId(),
                source != null ? source.getId() : -1   // ← attackerEntityId
        );

        // Send to nearby players
        for (net.minecraft.server.level.ServerPlayer player :
                net.minecraft.server.level.ServerLevel.class.cast(target.level())
                        .getPlayers(p -> p.distanceToSqr(pos) < 1024)) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
        }
    }
}