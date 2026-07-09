package zcylas.totality.api.shop;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

/**
 * A single buyable line in a shop's catalog. {@code stack} carries its full
 * data components (enchantments, custom name, etc.) via vanilla's own
 * {@code ItemStack.CODEC} — the same codec used for JSON-formatted item stacks
 * in loot tables/recipes, so enchanted books and similar NBT-bearing items
 * work with no custom parsing.
 */
public record ShopEntry(ItemStack stack, long price) {
    public static final Codec<ShopEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStack.CODEC.fieldOf("item").forGetter(ShopEntry::stack),
            Codec.LONG.fieldOf("price").forGetter(ShopEntry::price)
    ).apply(i, ShopEntry::new));
}
