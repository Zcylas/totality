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

        PhoneFrame frame = PhoneFrame.forStack(phone);
        open(phone, PhoneSource.equippedSlot(), frame);
        return true;
    }

    /**
     * Held-in-hand entry point (right-click-while-held). Kept out of {@code PhoneItem} itself —
     * an {@code Item} subclass is loaded on the dedicated server too, and a ternary between two
     * {@code Screen} subtypes inside its bytecode forces the JVM verifier to resolve the
     * client-only {@code Screen} class merely by loading the class, crashing a dedicated server
     * even though the branch is guarded by {@code isClientSide()} and never actually runs there.
     */
    public static void openFromHand(ItemStack phone, PhoneSource source, PhoneFrame frame) {
        open(phone, source, frame);
    }

    private static void open(ItemStack phone, PhoneSource source, PhoneFrame frame) {
        boolean setupComplete = Boolean.TRUE.equals(phone.get(TotalityItemComponents.PHONE_SETUP_COMPLETE));
        Minecraft.getInstance().gui.setScreen(setupComplete
                ? new PhoneAppGridScreen(frame)
                : new PhoneSetupScreen(source, frame));
    }
}
