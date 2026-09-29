package zcylas.totality.api.item;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;
import zcylas.totality.init.ModEnchantments;

/**
 * Soulbound: an ordinary registered enchantment ({@link ModEnchantments#SOULBOUND}) that keeps the individual enchanted
 * ItemStack with its owner through death instead of dropping it. It belongs to the stack, not to any slot, and is
 * independent of Attunement. Applied on death by the vanilla inventory drop (InventorySoulboundMixin) and by the
 * custom Equipment drop ({@code PlayerEquipmentComponent#dropOnDeath}). Curse of Vanishing still destroys an item.
 */
public final class Soulbound {

    private Soulbound() {}

    /**
     * On a death respawn without keepInventory (vanilla then transfers nothing), moves the Soulbound stacks the death
     * drop left in the dead player's inventory to the same slots of the new one. Other respawns (keepInventory, a
     * spectator's death, returning from the End) already carry the whole inventory; the custom Equipment component is
     * carried by its own respawn copy.
     */
    public static void register() {
        ServerPlayerEvents.COPY_FROM.register(Soulbound::onCopyFrom);
    }

    /** The {@code COPY_FROM} handler (public so the live verification can drive exactly this step). */
    public static void onCopyFrom(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
        if (!alive && !keepsInventory(oldPlayer)) restoreInventory(oldPlayer.getInventory(), newPlayer.getInventory());
    }

    private static boolean keepsInventory(ServerPlayer oldPlayer) {
        return oldPlayer.level().getGameRules().get(GameRules.KEEP_INVENTORY) || oldPlayer.isSpectator();
    }

    private static void restoreInventory(Inventory from, Inventory to) {
        for (int i = 0; i < from.getContainerSize(); i++) {
            ItemStack stack = from.getItem(i);
            if (isSoulbound(stack)) to.setItem(i, stack.copy());
        }
    }

    public static boolean isSoulbound(ItemStack stack) {
        return !stack.isEmpty() && stack.getEnchantments().keySet().stream().anyMatch(h -> h.is(ModEnchantments.SOULBOUND));
    }
}
