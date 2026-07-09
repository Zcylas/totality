package zcylas.totality.api.rpg.combat.weapon;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import zcylas.totality.api.combat.damage.TotalityDamageType;
import zcylas.totality.api.dice.Dice;
import zcylas.totality.api.dice.RollType;
import zcylas.totality.api.rpg.stats.AbilityScore;
import zcylas.totality.api.rpg.stats.PlayerStats;
import zcylas.totality.api.rpg.stats.StatsComponents;

/**
 * Resolves the stats to swing a melee weapon with — checks {@link TotalityWeaponItem}
 * first (mirrors {@code ThrownShurikenEntity.onHitEntity()}'s pattern) and only falls
 * back to the vanilla-only {@link VanillaWeaponStats} map for plain vanilla items.
 */
public final class WeaponDataResolver {

    public record Resolved(int diceCount, Dice damageDie, TotalityDamageType damageType,
                            AbilityScore ability, boolean proficient, RollType rollType) {}

    public static Resolved resolve(LivingEntity attacker, LivingEntity target, ItemStack weapon, RollType baseRollType) {
        if (weapon.getItem() instanceof TotalityWeaponItem totWeapon) {
            return new Resolved(
                    totWeapon.getDiceCount(),
                    totWeapon.getDamageDie(),
                    totWeapon.getDamageType(),
                    totWeapon.getEffectiveAbilityScore(attacker),
                    totWeapon.isProficient(attacker),
                    totWeapon.modifyRollType(attacker, target, baseRollType));
        }

        VanillaWeaponStats.WeaponData data = VanillaWeaponStats.get(weapon.getItem());
        if (data == null) data = VanillaWeaponStats.unarmed();

        PlayerStats stats = attacker instanceof ServerPlayer player ? StatsComponents.getStats(player) : null;
        AbilityScore ability = stats != null ? data.resolveAbility(stats) : data.ability();

        return new Resolved(1, data.damageDie(), data.damageType(), ability, true, baseRollType);
    }

    /**
     * Resolves the attack speed a given weapon stack would grant if it were the item
     * governing the swing — regardless of which slot it's actually sitting in. Vanilla's
     * {@code Attributes.ATTACK_SPEED} only reflects modifiers from whatever is currently
     * in the slot they target (weapons conventionally target {@code MAINHAND}), so a fast
     * weapon held in the offhand normally contributes nothing to the player's attribute
     * value at all. This reads the stack's own {@code ItemAttributeModifiers} component
     * directly and computes the result as if it applied to {@code MAINHAND} unconditionally,
     * so each dual-wielded hand's own weapon governs its own swing timing.
     */
    public static double resolveAttackSpeed(LivingEntity attacker, ItemStack weapon) {
        AttributeInstance instance = attacker.getAttribute(Attributes.ATTACK_SPEED);
        double base = instance != null ? instance.getBaseValue() : 4.0;
        ItemAttributeModifiers modifiers = weapon.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        double computed = modifiers.compute(Attributes.ATTACK_SPEED, base, EquipmentSlot.MAINHAND);
        return Math.max(0.1, computed);
    }

    private WeaponDataResolver() {}
}
