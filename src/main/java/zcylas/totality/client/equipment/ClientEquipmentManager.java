package zcylas.totality.client.equipment;

import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.equipment.PlayerEquipmentComponent;
import zcylas.totality.api.equipment.TotalityRingItem;
import zcylas.totality.api.item.TotalityItemComponents;

import java.util.UUID;

/** Client-side cache of the local player's equipped accessory stacks. */
public final class ClientEquipmentManager {

    private static final int[] RING_SLOTS = { PlayerEquipmentComponent.IDX_RING_1, PlayerEquipmentComponent.IDX_RING_2 };

    private static final ItemStack[] stacks = new ItemStack[PlayerEquipmentComponent.SLOT_COUNT];

    static {
        for (int i = 0; i < stacks.length; i++) stacks[i] = ItemStack.EMPTY;
    }

    public static void apply(ItemStack[] incoming) {
        for (int i = 0; i < stacks.length; i++) {
            stacks[i] = (incoming != null && i < incoming.length && incoming[i] != null)
                    ? incoming[i] : ItemStack.EMPTY;
        }
    }

    /** Returns the stack in slot {@code componentIndex} (0–3). */
    public static ItemStack getStack(int componentIndex) {
        if (componentIndex < 0 || componentIndex >= stacks.length) return ItemStack.EMPTY;
        return stacks[componentIndex];
    }

    /** Total AC bonus from all attuned rings currently cached for {@code playerUUID}. */
    public static int getAcBonus(UUID playerUUID) {
        int bonus = 0;
        for (int idx : RING_SLOTS) {
            bonus += ringBonus(stacks[idx], playerUUID, false);
        }
        return bonus;
    }

    /** Total saving throw bonus from all attuned rings currently cached for {@code playerUUID}. */
    public static int getSaveBonus(UUID playerUUID) {
        int bonus = 0;
        for (int idx : RING_SLOTS) {
            bonus += ringBonus(stacks[idx], playerUUID, true);
        }
        return bonus;
    }

    private static int ringBonus(ItemStack stack, UUID playerUUID, boolean save) {
        if (stack.isEmpty() || !(stack.getItem() instanceof TotalityRingItem ring)) return 0;
        UUID attuned = stack.get(TotalityItemComponents.ATTUNED_TO);
        if (attuned == null || !attuned.equals(playerUUID)) return 0;
        return save ? ring.getSaveBonus() : ring.getAcBonus();
    }

    private ClientEquipmentManager() {}
}