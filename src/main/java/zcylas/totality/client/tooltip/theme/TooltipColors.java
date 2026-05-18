package zcylas.totality.client.tooltip.theme;

import zcylas.totality.api.core.rpgutils.rarity.ItemRarity;
import zcylas.totality.api.core.rpgutils.rarity.ItemType;

public final class TooltipColors {

    public static int forRarity(ItemRarity rarity) {
        return switch (rarity) {
            case COMMON     -> 0xFF888888;
            case UNCOMMON   -> 0xFF55AA55;
            case RARE       -> 0xFF5588FF;
            case EPIC       -> 0xFFAA55FF;
            case LEGENDARY  -> 0xFFFFAA00;
            case MYTHICAL   -> 0xFFFF5555;
            case ARTIFACT   -> 0xFFD4A017;
            case CURSED     -> 0xFF8B1A35;
            case FORBIDDEN  -> 0xFF5A174F;
            case QUEST      -> 0xFFFFD85A;
            case BLESSED    -> 0xFFFFE6A3;
            case SACRED     -> 0xFFD6A84F;
            case CELESTIAL  -> 0xFFA8DFFF;
            case DIVINE     -> 0xFFFFFFFF;
            case GODFORGED   -> 0xFFE0B94A;
            case CRUDE       -> 0xFF8A6F4D;
            case CALIBRATED  -> 0xFF6A8F9C;
            case REINFORCED  -> 0xFFB0B0B0;
            case PROTOTYPE   -> 0xFF42F5D7;
            case OVERCHARGED -> 0xFF42F5FF;
            case MASTERWORK  -> 0xFFD8914A;
        };
    }

    public static int forType(ItemType type) {
        if (type == null) return 0xFF666666;
        return switch (type) {
            case MAGICAL     -> 0xFFAA55FF;
            case RITUAL      -> 0xFFC99A2E;
            case INDUSTRIAL  -> 0xFF6A6A6A;
            case MACHINE     -> 0xFF9A9A9A;
            case BATTERY     -> 0xFF42F5FF;
            case CABLE       -> 0xFFD8914A;
            case FUEL        -> 0xFFFF6600;
            case CURRENCY    -> 0xFFFFD700;
            case WEAPON      -> 0xFFB84A3A;
            case ARMOR       -> 0xFFB8B8B8;
            case TOOL        -> 0xFFC0C0C0;
            case FOOD        -> 0xFFE8B85A;
            case POTION      -> 0xFF7E6FFF;
            case CONSUMABLE  -> 0xFFA8D06D;
            case MATERIAL    -> 0xFFB8A36A;
            case COMPONENT   -> 0xFFD8914A;
            case INGREDIENT  -> 0xFF8FD45A;
            case STANDARD    -> 0xFF666666;
            case BLOCK       -> 0xFFD4C9A8;
            case DECORATIVE -> 0xFFBAAF96;
            case REAGENT     -> 0xFF3DAA5A;
            default          -> 0xFF666666;
        };
    }

    private TooltipColors() {}
}