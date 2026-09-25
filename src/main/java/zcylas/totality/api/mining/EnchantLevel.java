package zcylas.totality.api.mining;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Reads one enchantment's level straight off the stack's own {@code ItemEnchantments} component —
 * no {@code RegistryAccess}/level lookup needed, so gameplay (server) and the tooltip (client) can
 * read Impact/Efficiency identically and agree by construction.
 */
public final class EnchantLevel {

    private EnchantLevel() {}

    public static int of(ItemStack stack, ResourceKey<Enchantment> key) {
        var enchantments = stack.getEnchantments();
        for (Holder<Enchantment> holder : enchantments.keySet()) {
            if (holder.is(key)) return enchantments.getLevel(holder);
        }
        return 0;
    }
}
