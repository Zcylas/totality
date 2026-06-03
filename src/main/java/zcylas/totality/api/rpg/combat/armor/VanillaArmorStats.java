package zcylas.totality.api.rpg.combat.armor;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.rpg.combat.ArmorClass;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class VanillaArmorStats {

    public record PieceStats(int ac, ArmorClass.ArmorType type, Set<ArmorProperty> properties) {
        public PieceStats(int ac, ArmorClass.ArmorType type) {
            this(ac, type, EnumSet.noneOf(ArmorProperty.class));
        }
        public boolean has(ArmorProperty property) { return properties.contains(property); }
    }

    public enum ArmorProperty {
        STEALTH_DISADVANTAGE
    }

    private static final Map<Item, PieceStats> PIECES = new HashMap<>();

    static {
        // Leather (LIGHT)
        PIECES.put(Items.LEATHER_HELMET,       new PieceStats(0, ArmorClass.ArmorType.LIGHT));
        PIECES.put(Items.LEATHER_CHESTPLATE,   new PieceStats(1, ArmorClass.ArmorType.LIGHT));
        PIECES.put(Items.LEATHER_LEGGINGS,     new PieceStats(1, ArmorClass.ArmorType.LIGHT));
        PIECES.put(Items.LEATHER_BOOTS,        new PieceStats(0, ArmorClass.ArmorType.LIGHT));
        // Gold (LIGHT)
        PIECES.put(Items.GOLDEN_HELMET,        new PieceStats(0, ArmorClass.ArmorType.LIGHT));
        PIECES.put(Items.GOLDEN_CHESTPLATE,    new PieceStats(1, ArmorClass.ArmorType.LIGHT));
        PIECES.put(Items.GOLDEN_LEGGINGS,      new PieceStats(1, ArmorClass.ArmorType.LIGHT));
        PIECES.put(Items.GOLDEN_BOOTS,         new PieceStats(0, ArmorClass.ArmorType.LIGHT));
        // Chainmail (MEDIUM)
        PIECES.put(Items.CHAINMAIL_HELMET,     new PieceStats(1, ArmorClass.ArmorType.MEDIUM));
        PIECES.put(Items.CHAINMAIL_CHESTPLATE, new PieceStats(2, ArmorClass.ArmorType.MEDIUM));
        PIECES.put(Items.CHAINMAIL_LEGGINGS,   new PieceStats(2, ArmorClass.ArmorType.MEDIUM));
        PIECES.put(Items.CHAINMAIL_BOOTS,      new PieceStats(0, ArmorClass.ArmorType.MEDIUM));
        // Iron (MEDIUM)
        PIECES.put(Items.IRON_HELMET,          new PieceStats(1, ArmorClass.ArmorType.MEDIUM));
        PIECES.put(Items.IRON_CHESTPLATE,      new PieceStats(2, ArmorClass.ArmorType.MEDIUM));
        PIECES.put(Items.IRON_LEGGINGS,        new PieceStats(2, ArmorClass.ArmorType.MEDIUM));
        PIECES.put(Items.IRON_BOOTS,           new PieceStats(1, ArmorClass.ArmorType.MEDIUM));
        // Diamond (HEAVY, stealth disadvantage)
        Set<ArmorProperty> heavy = EnumSet.of(ArmorProperty.STEALTH_DISADVANTAGE);
        PIECES.put(Items.DIAMOND_HELMET,       new PieceStats(1, ArmorClass.ArmorType.HEAVY, heavy));
        PIECES.put(Items.DIAMOND_CHESTPLATE,   new PieceStats(3, ArmorClass.ArmorType.HEAVY, heavy));
        PIECES.put(Items.DIAMOND_LEGGINGS,     new PieceStats(2, ArmorClass.ArmorType.HEAVY, heavy));
        PIECES.put(Items.DIAMOND_BOOTS,        new PieceStats(1, ArmorClass.ArmorType.HEAVY, heavy));
        // Netherite (HEAVY, stealth disadvantage)
        PIECES.put(Items.NETHERITE_HELMET,     new PieceStats(2, ArmorClass.ArmorType.HEAVY, heavy));
        PIECES.put(Items.NETHERITE_CHESTPLATE, new PieceStats(3, ArmorClass.ArmorType.HEAVY, heavy));
        PIECES.put(Items.NETHERITE_LEGGINGS,   new PieceStats(2, ArmorClass.ArmorType.HEAVY, heavy));
        PIECES.put(Items.NETHERITE_BOOTS,      new PieceStats(1, ArmorClass.ArmorType.HEAVY, heavy));
        // Turtle Shell (LIGHT, helmet only)
        PIECES.put(Items.TURTLE_HELMET,        new PieceStats(2, ArmorClass.ArmorType.LIGHT));
    }

    @Nullable
    public static PieceStats get(Item item) { return PIECES.get(item); }

    public static boolean playerHasProperty(Player player, ArmorProperty property) {
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            PieceStats s = PIECES.get(player.getItemBySlot(slot).getItem());
            if (s != null && s.has(property)) return true;
        }
        return false;
    }

    private VanillaArmorStats() {}
}