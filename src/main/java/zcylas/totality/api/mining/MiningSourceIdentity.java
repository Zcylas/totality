package zcylas.totality.api.mining;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

/**
 * "Is this still the same mining source?" — one rule shared by the server (impact at contact) and the client
 * (first-person presentation), so a swap the server cancels is never visually finished.
 *
 * <p>Two stacks are the same source when the item and its components match, IGNORING durability and other
 * swap-irrelevant components. So ordinary wear on the same tool is not a swap, but a different item, or the
 * same item with different enchantments/components, is.
 */
public final class MiningSourceIdentity {

    private MiningSourceIdentity() {}

    public static boolean same(ItemStack snapshot, ItemStack current) {
        return ItemStack.matchesIgnoringComponents(snapshot, current,
                type -> type == DataComponents.DAMAGE || type.ignoreSwapAnimation());
    }
}
