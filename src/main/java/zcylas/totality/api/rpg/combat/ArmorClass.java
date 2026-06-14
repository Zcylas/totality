package zcylas.totality.api.rpg.combat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.equipment.EquipmentComponents;
import zcylas.totality.api.item.TotalityArmorItem;
import zcylas.totality.api.rpg.combat.armor.VanillaArmorStats;
import zcylas.totality.api.rpg.stats.AbilityScore;
import zcylas.totality.api.rpg.stats.PlayerStats;
import zcylas.totality.api.rpg.stats.StatsComponents;

public final class ArmorClass {

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private ArmorClass() {}

    public static int calculate(ServerPlayer player) {
        PlayerStats stats = StatsComponents.getStats(player);
        int dexMod = stats != null ? stats.getModifier(AbilityScore.DEX) : 0;

        ArmorResult armor = readArmor(player);

        int base;
        if (armor == null) {
            base = unarmoredAc(player, stats, dexMod);
        } else {
            int cappedDex = switch (armor.type()) {
                case LIGHT  -> dexMod;
                case MEDIUM -> Math.min(dexMod, 2);
                case HEAVY  -> 0;
            };
            base = armor.baseAc() + cappedDex;
        }

        int equipAc = EquipmentComponents.get(player).getAcBonus();
        return base + (isHoldingShield(player) ? 2 : 0) + equipAc;
    }

    @Nullable
    private static ArmorResult readArmor(ServerPlayer player) {
        int totalAc = 0;
        ArmorType heaviest = null;
        boolean hasArmor = false;

        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;

            // Vanilla armor first
            VanillaArmorStats.PieceStats piece = VanillaArmorStats.get(stack.getItem());
            if (piece != null) {
                hasArmor = true;
                totalAc += piece.ac();
                if (heaviest == null || piece.type().ordinal() > heaviest.ordinal())
                    heaviest = piece.type();
                continue;
            }

            // Totality armor in vanilla slots — CLOTHING and accessories (null category)
            // are transparent: they don't contribute AC and don't block Unarmored Defense.
            if (stack.getItem() instanceof TotalityArmorItem tai) {
                TotalityArmorItem.ArmorCategory cat = tai.getArmorCategory();
                if (cat != null && cat.isArmored()) {
                    hasArmor = true;
                    totalAc += tai.getAcBonus();
                    ArmorType type = switch (cat) {
                        case LIGHT  -> ArmorType.LIGHT;
                        case MEDIUM -> ArmorType.MEDIUM;
                        case HEAVY  -> ArmorType.HEAVY;
                        default     -> ArmorType.LIGHT;
                    };
                    if (heaviest == null || type.ordinal() > heaviest.ordinal())
                        heaviest = type;
                }
                // CLOTHING / null → skip, Unarmored Defense remains eligible
            }
        }

        if (!hasArmor) return null;
        return new ArmorResult(10 + totalAc, heaviest);
    }

    private static int unarmoredAc(ServerPlayer player, @Nullable PlayerStats stats, int dexMod) {
        UnarmoredDefenseRegistry.UnarmoredDefenseProvider provider =
                UnarmoredDefenseRegistry.get(player);
        if (provider != null && stats != null) return provider.calculate(player, stats);
        return 10 + dexMod;
    }

    private static boolean isHoldingShield(ServerPlayer player) {
        ItemStack offhand = player.getItemBySlot(EquipmentSlot.OFFHAND);
        return offhand.getItem() instanceof net.minecraft.world.item.ShieldItem;
    }

    private record ArmorResult(int baseAc, ArmorType type) {}

    public enum ArmorType { LIGHT, MEDIUM, HEAVY }
}