package zcylas.totality.api.core.rpgutils.rarity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Presentation-side rarity resolution (Tooltip V2 Pass 1): the stack's authored {@link RarityComponent} — including
 * exact vanilla overrides such as {@code VanillaItemPresentation} — always wins; an ordinary vanilla item without one
 * is Standard {@link ItemRarity#COMMON}. Never derived from material, and vanilla's own {@code DataComponents.RARITY}
 * is not consulted. Anything else without an authored rarity (another mod's item) resolves to empty and shows none.
 * Totality's own items are required to author one ({@link RarityCoverage}).
 */
public final class ItemRarityResolver {

    public static final ItemRarity VANILLA_FALLBACK = ItemRarity.COMMON;

    public static Optional<ItemRarity> resolve(ItemStack stack) {
        if (stack.isEmpty()) return Optional.empty();
        var rarityType = ItemComponents.getRarity();
        RarityComponent authored = rarityType == null ? null : stack.get(rarityType);
        if (authored != null && authored.rarity() != null) return Optional.of(authored.rarity());
        return isVanilla(stack) ? Optional.of(VANILLA_FALLBACK) : Optional.empty();
    }

    static boolean isVanilla(ItemStack stack) {
        return "minecraft".equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace());
    }

    private ItemRarityResolver() {}
}
