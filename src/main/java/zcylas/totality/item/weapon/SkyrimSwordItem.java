package zcylas.totality.item.weapon;

import net.minecraft.world.item.Item;
import zcylas.totality.api.combat.damage.TotalityDamageType;
import zcylas.totality.api.dice.Dice;
import zcylas.totality.api.rpg.combat.weapon.*;
import zcylas.totality.api.rpg.stats.AbilityScore;

/**
 * Base class for all Skyrim-lore swords in Totality.
 *
 * Register each sword variant with a unique registry name and its own
 * {@link TotalityWeaponStats} and {@link Item.Properties} (rarity, lore, durability).
 * The Totality tooltip renderer reads weapon stats automatically via
 * {@link zcylas.totality.client.tooltip.renderer.TooltipWeaponBlock}.
 *
 * Damage and attack rolls go through {@link zcylas.totality.api.rpg.combat.CombatResolver}
 * via the player attack mixin — vanilla sword damage is not used.
 *
 * Example registration in an item class:
 * <pre>
 *   public static final SkyrimSwordItem DRAGONBONE_SWORD = TotalityRegistry.registerItem(
 *       "dragonbone_sword",
 *       props -> new SkyrimSwordItem(props, new TotalityWeaponStats(
 *           Dice.D8, 1, DamageTypes.PHYSICAL, AbilityScore.STR,
 *           WeaponCategory.MARTIAL_MELEE, WeaponType.ONE_HANDED,
 *           false, false, false, false)),
 *       new Item.Properties()
 *           .durability(1200)
 *           .component(ItemComponents.getRarity(), new RarityComponent(ItemRarity.EPIC))
 *           .component(ItemComponents.getLore(), new LoreComponent(
 *               "Forged from the bones of dragons, this blade never dulls."))
 *   );
 * </pre>
 */
public class SkyrimSwordItem extends TotalityMeleeWeaponItem {

    private final TotalityWeaponStats stats;

    public SkyrimSwordItem(Item.Properties properties, TotalityWeaponStats stats) {
        super(properties);
        this.stats = stats;
    }

    // ── TotalityWeaponItem ────────────────────────────────────────────────────

    @Override public Dice getDamageDie()                    { return stats.damageDie(); }
    @Override public int getDiceCount()                     { return stats.diceCount(); }
    @Override public TotalityDamageType getDamageType()     { return stats.damageType(); }
    @Override public AbilityScore getDefaultAbilityScore()  { return stats.abilityScore(); }
    @Override public WeaponCategory getWeaponCategory()     { return stats.weaponCategory(); }
    @Override public WeaponType getWeaponType()             { return stats.weaponType(); }
    @Override public boolean isFinesse()                    { return stats.finesse(); }
    @Override public boolean isLight()                      { return stats.light(); }
    @Override public boolean isHeavy()                      { return stats.heavy(); }
    @Override public boolean isReach()                      { return stats.reach(); }

    public TotalityWeaponStats getSkyrimStats()                { return stats; }
}