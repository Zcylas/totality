package zcylas.totality.screen.phone;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.equipment.PlayerEquipmentComponent;
import zcylas.totality.client.equipment.ClientEquipmentManager;

/**
 * Where a Phone screen's backing item stack lives — either held in a hand
 * (right-click-while-held) or sitting in the dedicated Phone equipment slot (TAB).
 * Both {@link #resolveClient()} calls return the live stack reference (not a copy),
 * mirroring {@code ClientEquipmentManager}/{@code Player#getItemInHand} semantics,
 * so mutating the returned stack updates the client-side cache in place.
 */
public record PhoneSource(boolean equipped, InteractionHand hand) {

    public static PhoneSource hand(InteractionHand hand) { return new PhoneSource(false, hand); }

    public static PhoneSource equippedSlot() { return new PhoneSource(true, InteractionHand.MAIN_HAND); }

    public ItemStack resolveClient() {
        if (equipped) {
            return ClientEquipmentManager.getStack(PlayerEquipmentComponent.IDX_PHONE);
        }
        Player player = Minecraft.getInstance().player;
        return player != null ? player.getItemInHand(hand) : ItemStack.EMPTY;
    }
}
