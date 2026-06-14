package zcylas.totality.networking.item;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.item.TotalityItem;
import zcylas.totality.api.item.TotalityItemComponents;

public final class UnAttuneHandler {

    public static void handle(UnAttunePayload payload,
                              ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ServerPlayer player = context.player();
            int slotIndex = payload.slotIndex();

            var slots = player.containerMenu.slots;
            if (slotIndex < 0 || slotIndex >= slots.size()) return;

            ItemStack stack = slots.get(slotIndex).getItem();
            if (stack.isEmpty()) return;
            if (!(stack.getItem() instanceof TotalityItem ti)) return;

            // Only the owner can unattune
            java.util.UUID attunedTo = stack.get(TotalityItemComponents.ATTUNED_TO);
            if (attunedTo == null || !attunedTo.equals(player.getUUID())) return;

            stack.remove(TotalityItemComponents.ATTUNED_TO);
        });
    }

    private UnAttuneHandler() {}
}