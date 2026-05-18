package zcylas.totality.api.core.rpgutils.rarity;

import net.minecraft.util.StringRepresentable;

public enum ItemRarity implements StringRepresentable {
    // Standard Progression
    COMMON,
    UNCOMMON,
    RARE,
    EPIC,
    LEGENDARY,
    MYTHICAL,
    ARTIFACT,

    // Special
    FORBIDDEN,
    CURSED,
    QUEST,

    // Religious
    BLESSED,
    SACRED,
    CELESTIAL,
    DIVINE,
    GODFORGED,

    // Industrial
    CRUDE,
    CALIBRATED,
    REINFORCED,
    PROTOTYPE,
    OVERCHARGED,
    MASTERWORK;

    public static final com.mojang.serialization.Codec<ItemRarity> CODEC =
            StringRepresentable.fromEnum(ItemRarity::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase();
    }
}