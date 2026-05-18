package zcylas.totality.api.core.rpgutils.rarity;

import net.minecraft.util.StringRepresentable;

public enum ItemType implements StringRepresentable {
    //Blocks
    BLOCK,
    DECORATIVE,
    // General
    STANDARD,
    CONSUMABLE,
    MATERIAL,
    POTION,
    FUEL,
    FOOD,
    INGREDIENT,
    CURRENCY,

    // Combat
    WEAPON,
    ARMOR,
    TOOL,

    // Industrial
    INDUSTRIAL,
    CABLE,
    BATTERY,
    MACHINE,
    COMPONENT,

    // Magic
    MAGICAL,
    REAGENT,
    RITUAL;

    public static final com.mojang.serialization.Codec<ItemType> CODEC =
            StringRepresentable.fromEnum(ItemType::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase();
    }
}