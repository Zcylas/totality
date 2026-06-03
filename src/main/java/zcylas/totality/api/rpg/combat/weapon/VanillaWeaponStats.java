package zcylas.totality.api.rpg.combat.weapon;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.combat.damage.DamageTypes;
import zcylas.totality.api.combat.damage.TotalityDamageType;
import zcylas.totality.api.dice.Dice;
import zcylas.totality.api.rpg.stats.AbilityScore;
import zcylas.totality.api.rpg.stats.PlayerStats;

import java.util.HashMap;
import java.util.Map;

public final class VanillaWeaponStats {

    public record WeaponData(Dice damageDie, AbilityScore ability,
                             TotalityDamageType damageType, boolean finesse) {
        public AbilityScore resolveAbility(PlayerStats stats) {
            if (!finesse) return ability;
            return stats.getModifier(AbilityScore.DEX) > stats.getModifier(AbilityScore.STR)
                    ? AbilityScore.DEX : AbilityScore.STR;
        }
    }

    private static final Map<Item, WeaponData> WEAPONS = new HashMap<>();

    static {
        // ── Swords (STR, SLASHING) ────────────────────────────────────────────
        WEAPONS.put(Items.WOODEN_SWORD,    new WeaponData(Dice.D4,  AbilityScore.STR, DamageTypes.SLASHING, false));
        WEAPONS.put(Items.STONE_SWORD,     new WeaponData(Dice.D6,  AbilityScore.STR, DamageTypes.SLASHING, false));
        WEAPONS.put(Items.IRON_SWORD,      new WeaponData(Dice.D8,  AbilityScore.STR, DamageTypes.SLASHING, false));
        WEAPONS.put(Items.GOLDEN_SWORD,    new WeaponData(Dice.D6,  AbilityScore.STR, DamageTypes.SLASHING, true));
        WEAPONS.put(Items.DIAMOND_SWORD,   new WeaponData(Dice.D10, AbilityScore.STR, DamageTypes.SLASHING, false));
        WEAPONS.put(Items.NETHERITE_SWORD, new WeaponData(Dice.D12, AbilityScore.STR, DamageTypes.SLASHING, false));
        // ── Axes (STR, SLASHING) ──────────────────────────────────────────────
        WEAPONS.put(Items.WOODEN_AXE,      new WeaponData(Dice.D6,  AbilityScore.STR, DamageTypes.SLASHING, false));
        WEAPONS.put(Items.STONE_AXE,       new WeaponData(Dice.D8,  AbilityScore.STR, DamageTypes.SLASHING, false));
        WEAPONS.put(Items.IRON_AXE,        new WeaponData(Dice.D10, AbilityScore.STR, DamageTypes.SLASHING, false));
        WEAPONS.put(Items.GOLDEN_AXE,      new WeaponData(Dice.D8,  AbilityScore.STR, DamageTypes.SLASHING, false));
        WEAPONS.put(Items.DIAMOND_AXE,     new WeaponData(Dice.D12, AbilityScore.STR, DamageTypes.SLASHING, false));
        WEAPONS.put(Items.NETHERITE_AXE,   new WeaponData(Dice.D12, AbilityScore.STR, DamageTypes.SLASHING, false));
        // ── Pickaxes (STR, PIERCING) ──────────────────────────────────────────
        WEAPONS.put(Items.WOODEN_PICKAXE,    new WeaponData(Dice.D4, AbilityScore.STR, DamageTypes.PIERCING, false));
        WEAPONS.put(Items.STONE_PICKAXE,     new WeaponData(Dice.D6, AbilityScore.STR, DamageTypes.PIERCING, false));
        WEAPONS.put(Items.IRON_PICKAXE,      new WeaponData(Dice.D6, AbilityScore.STR, DamageTypes.PIERCING, false));
        WEAPONS.put(Items.GOLDEN_PICKAXE,    new WeaponData(Dice.D4, AbilityScore.STR, DamageTypes.PIERCING, false));
        WEAPONS.put(Items.DIAMOND_PICKAXE,   new WeaponData(Dice.D8, AbilityScore.STR, DamageTypes.PIERCING, false));
        WEAPONS.put(Items.NETHERITE_PICKAXE, new WeaponData(Dice.D8, AbilityScore.STR, DamageTypes.PIERCING, false));
        // ── Shovels (STR, BLUDGEONING) ───────────────────────────────────────
        WEAPONS.put(Items.WOODEN_SHOVEL,    new WeaponData(Dice.D4, AbilityScore.STR, DamageTypes.BLUDGEONING, false));
        WEAPONS.put(Items.STONE_SHOVEL,     new WeaponData(Dice.D4, AbilityScore.STR, DamageTypes.BLUDGEONING, false));
        WEAPONS.put(Items.IRON_SHOVEL,      new WeaponData(Dice.D6, AbilityScore.STR, DamageTypes.BLUDGEONING, false));
        WEAPONS.put(Items.GOLDEN_SHOVEL,    new WeaponData(Dice.D4, AbilityScore.STR, DamageTypes.BLUDGEONING, false));
        WEAPONS.put(Items.DIAMOND_SHOVEL,   new WeaponData(Dice.D6, AbilityScore.STR, DamageTypes.BLUDGEONING, false));
        WEAPONS.put(Items.NETHERITE_SHOVEL, new WeaponData(Dice.D8, AbilityScore.STR, DamageTypes.BLUDGEONING, false));
        // ── Hoes (STR, SLASHING) ─────────────────────────────────────────────
        WEAPONS.put(Items.WOODEN_HOE,    new WeaponData(Dice.D4, AbilityScore.STR, DamageTypes.SLASHING, false));
        WEAPONS.put(Items.STONE_HOE,     new WeaponData(Dice.D4, AbilityScore.STR, DamageTypes.SLASHING, false));
        WEAPONS.put(Items.IRON_HOE,      new WeaponData(Dice.D4, AbilityScore.STR, DamageTypes.SLASHING, false));
        WEAPONS.put(Items.GOLDEN_HOE,    new WeaponData(Dice.D4, AbilityScore.STR, DamageTypes.SLASHING, false));
        WEAPONS.put(Items.DIAMOND_HOE,   new WeaponData(Dice.D6, AbilityScore.STR, DamageTypes.SLASHING, false));
        WEAPONS.put(Items.NETHERITE_HOE, new WeaponData(Dice.D6, AbilityScore.STR, DamageTypes.SLASHING, false));
        // ── Ranged (DEX, PIERCING) ────────────────────────────────────────────
        WEAPONS.put(Items.BOW,      new WeaponData(Dice.D8,  AbilityScore.DEX, DamageTypes.PIERCING, false));
        WEAPONS.put(Items.CROSSBOW, new WeaponData(Dice.D10, AbilityScore.DEX, DamageTypes.PIERCING, false));
        WEAPONS.put(Items.TRIDENT,  new WeaponData(Dice.D8,  AbilityScore.STR, DamageTypes.PIERCING, true));
        // ── Mace (STR, BLUDGEONING) ───────────────────────────────────────────
        WEAPONS.put(Items.MACE, new WeaponData(Dice.D10, AbilityScore.STR, DamageTypes.BLUDGEONING, false));
    }

    @Nullable
    public static WeaponData get(Item item) { return WEAPONS.get(item); }

    public static WeaponData unarmed() {
        return new WeaponData(Dice.D4, AbilityScore.STR, DamageTypes.BLUDGEONING, false);
    }

    private VanillaWeaponStats() {}
}