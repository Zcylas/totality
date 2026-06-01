package zcylas.totality.api.rpg.combat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.rpg.stats.AbilityScore;
import zcylas.totality.api.rpg.stats.PlayerStats;
import zcylas.totality.api.rpg.stats.StatsComponents;

/**
 * Calculates a player's Armor Class (AC) using D&D 5e rules.
 *
 * Unarmored:  10 + DEX modifier (or class Unarmored Defense override)
 * Armored:    armor base AC + DEX modifier (capped by armor type)
 * Shield:     +2 if offhand is a shield
 *
 * Armor reading and class Unarmored Defense are stubbed until
 * the Equipment and Class APIs are built.
 */
public final class ArmorClass {

    private ArmorClass() {}

    /**
     * Calculates total AC for a player. Server-side only.
     */
    public static int calculate(ServerPlayer player) {
        PlayerStats stats = StatsComponents.getStats(player);
        int dexMod = stats != null ? stats.getModifier(AbilityScore.DEX) : 0;

        ArmorResult armor = readArmor(player);

        int base;
        if (armor == null) {
            // Unarmored — class features may override the default 10 + DEX
            base = classUnarmoredAc(player, stats, dexMod);
        } else {
            int cappedDex = switch (armor.type()) {
                case LIGHT  -> dexMod;
                case MEDIUM -> Math.min(dexMod, 2);
                case HEAVY  -> 0;
            };
            base = armor.baseAc() + cappedDex;
        }

        return base + (isHoldingShield(player) ? 2 : 0);
    }

    // ── Stubs ─────────────────────────────────────────────────────────────────

    /**
     * Reads base AC and armor type from equipped armor items.
     * Returns null if unarmored.
     *
     * TODO: read TotalityComponents.ARMOR_AC data component from worn items
     * once armor items and the Equipment API exist.
     */
    private static ArmorResult readArmor(ServerPlayer player) {
        return null; // unarmored until Equipment API is built
    }

    /**
     * Unarmored Defense from class features.
     * Default: 10 + DEX modifier.
     *
     * TODO: query PlayerClass.get(player).getUnarmoredAc(stats) once Class API exists.
     * Examples:
     *   Barbarian: 10 + STR mod + CON mod
     *   Monk:      10 + DEX mod + WIS mod
     */
    private static int classUnarmoredAc(ServerPlayer player, PlayerStats stats, int dexMod) {
        return 10 + dexMod;
    }

    /**
     * TODO: replace with ModTags.SHIELDS tag check once shields are registered.
     */
    private static boolean isHoldingShield(ServerPlayer player) {
        ItemStack offhand = player.getItemBySlot(EquipmentSlot.OFFHAND);
        return false;
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private record ArmorResult(int baseAc, ArmorType type) {}

    public enum ArmorType { LIGHT, MEDIUM, HEAVY }
}