package zcylas.totality.networking.item;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.equipment.EquipmentComponents;
import zcylas.totality.api.equipment.PlayerEquipmentComponent;
import zcylas.totality.api.item.TotalityItemComponents;
import zcylas.totality.api.quest.QuestManager;
import zcylas.totality.item.energy.PhoneItem;

/**
 * Server-side handler for {@link PhoneSetupPayload}.
 * Marks the phone stack (held or equipped, per the payload) as set up.
 */
public final class PhoneSetupHandler {

    public static void handle(PhoneSetupPayload payload,
                               ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            var player = context.player();
            ItemStack stack;
            if (payload.equipped()) {
                stack = EquipmentComponents.get(player).getItem(PlayerEquipmentComponent.IDX_PHONE);
            } else {
                InteractionHand hand = payload.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                stack = player.getItemInHand(hand);
            }
            if (!(stack.getItem() instanceof PhoneItem)) return;

            stack.set(TotalityItemComponents.PHONE_SETUP_COMPLETE, true);
            if (payload.equipped()) {
                EquipmentComponents.get(player).sync();
            }
            QuestManager.onPhoneSetupComplete(player);
        });
    }

    private PhoneSetupHandler() {}
}
