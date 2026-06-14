package zcylas.totality.networking.item;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.item.TotalityItem;
import zcylas.totality.api.item.TotalityItemComponents;

/**
 * Server-side handler for {@link AttunementPayload}.
 * Validates the request and applies the ATTUNED_TO component to the item stack.
 *
 * Registration: add to TotalityPackets.register():
 * <pre>
 *   ServerPlayNetworking.registerGlobalReceiver(
 *       AttunementPayload.TYPE, AttunementHandler::handle);
 * </pre>
 */
public final class AttunementHandler {

    public static void handle(AttunementPayload payload,
                              ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            var player = context.player();
            int slotIndex = payload.slotIndex();

            // Validate slot range
            var slots = player.containerMenu.slots;
            if (slotIndex < 0 || slotIndex >= slots.size()) return;

            ItemStack stack = slots.get(slotIndex).getItem();
            if (stack.isEmpty()) return;
            if (!(stack.getItem() instanceof TotalityItem ti)) return;
            if (!ti.requiresAttunement()) return;
            if (ti.isAttuned(stack, player)) return; // already attuned

            // Apply attunement
            stack.set(TotalityItemComponents.ATTUNED_TO, player.getUUID());

            // TODO: send feedback message to player (chat or floating text)
        });
    }

    private AttunementHandler() {}
}