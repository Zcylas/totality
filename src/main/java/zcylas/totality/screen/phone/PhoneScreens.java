package zcylas.totality.screen.phone;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.equipment.PlayerEquipmentComponent;
import zcylas.totality.api.item.TotalityItemComponents;
import zcylas.totality.client.equipment.ClientEquipmentManager;

/**
 * Entry point for opening the Phone's setup/app-grid screens from the equipped
 * Phone slot (TAB key). Right-click-while-held goes through {@code PhoneItem.use()} instead.
 */
public final class PhoneScreens {

    private PhoneScreens() {}

    /** Returns false (no-op) if no Phone is equipped. */
    public static boolean openForEquippedPhone(Minecraft client) {
        ItemStack phone = ClientEquipmentManager.getStack(PlayerEquipmentComponent.IDX_PHONE);
        if (phone.isEmpty()) return false;

        boolean setupComplete = Boolean.TRUE.equals(phone.get(TotalityItemComponents.PHONE_SETUP_COMPLETE));
        PhoneFrame frame = PhoneFrame.forStack(phone);
        client.setScreen(setupComplete
                ? new PhoneAppGridScreen(frame)
                : new PhoneSetupScreen(PhoneSource.equippedSlot(), frame));
        return true;
    }
}
