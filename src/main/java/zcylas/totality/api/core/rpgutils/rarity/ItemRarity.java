package zcylas.totality.api.core.rpgutils.rarity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.util.StringRepresentable;

/**
 * Standard progression ends in {@link #ANCIENT} — {@code ARTIFACT} was removed from the
 * ladder (2026-07-30 Tooltip API foundation pass). Artifact is expected to return later as a
 * separate item classification/tag, not a rarity tier; see {@link #CODEC} for the read-time
 * compatibility alias that keeps pre-existing saved stacks readable.
 */
public enum ItemRarity implements StringRepresentable {
    // Standard Progression
    COMMON(RarityFamily.STANDARD),
    UNCOMMON(RarityFamily.STANDARD),
    RARE(RarityFamily.STANDARD),
    EPIC(RarityFamily.STANDARD),
    LEGENDARY(RarityFamily.STANDARD),
    MYTHICAL(RarityFamily.STANDARD),
    ANCIENT(RarityFamily.STANDARD),

    // Special
    FORBIDDEN(RarityFamily.SPECIAL),
    CURSED(RarityFamily.SPECIAL),
    QUEST(RarityFamily.SPECIAL),

    // Religious
    BLESSED(RarityFamily.RELIGIOUS),
    SACRED(RarityFamily.RELIGIOUS),
    CELESTIAL(RarityFamily.RELIGIOUS),
    DIVINE(RarityFamily.RELIGIOUS),
    GODFORGED(RarityFamily.RELIGIOUS),

    // Industrial
    CRUDE(RarityFamily.INDUSTRIAL),
    CALIBRATED(RarityFamily.INDUSTRIAL),
    REINFORCED(RarityFamily.INDUSTRIAL),
    PROTOTYPE(RarityFamily.INDUSTRIAL),
    OVERCHARGED(RarityFamily.INDUSTRIAL),
    MASTERWORK(RarityFamily.INDUSTRIAL);

    private final RarityFamily family;

    ItemRarity(RarityFamily family) {
        this.family = family;
    }

    /** Which quality ladder this rarity belongs to — see {@link RarityFamily}. */
    public RarityFamily family() {
        return family;
    }

    /**
     * Read-time compatibility alias: stacks persisted before Artifact was removed from the
     * standard ladder still encode the literal string "artifact" on disk. Decoding maps that
     * legacy value onto {@link #ANCIENT}, the tier that replaced it, so old saves stay readable.
     * This alias is a temporary migration aid, not a selectable rarity — "artifact" is never
     * produced by {@link #getSerializedName()} and cannot be chosen going forward.
     */
    public static final Codec<ItemRarity> CODEC = Codec.STRING.comapFlatMap(
            ItemRarity::byNameWithLegacyAlias,
            ItemRarity::getSerializedName
    );

    private static DataResult<ItemRarity> byNameWithLegacyAlias(String name) {
        String normalized = name.toLowerCase();
        if (normalized.equals("artifact")) {
            return DataResult.success(ANCIENT);
        }
        try {
            return DataResult.success(ItemRarity.valueOf(normalized.toUpperCase()));
        } catch (IllegalArgumentException e) {
            return DataResult.error(() -> "Unknown ItemRarity: " + name);
        }
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase();
    }
}