package zcylas.totality.api.mana;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.mana.base.*;

import java.util.HashMap;
import java.util.UUID;

public class PlayerManaManager {
    public static final int BASE_MAX_MANA = 100;
    // 2% of max mana per regen tick (every 20 ticks = 1 second)
    public static final float BASE_REGEN_PERCENT = 0.02f;

    private static final HashMap<UUID, Integer> manaMap = new HashMap<>();

    public static int getMana(Player player) {
        if (!manaMap.containsKey(player.getUUID())) {
            int max = getMaxMana(player);
            manaMap.put(player.getUUID(), max);
            return max;
        }
        return manaMap.get(player.getUUID());
    }

    public static void setMana(Player player, int amount) {
        int max = getMaxMana(player);
        manaMap.put(player.getUUID(), Math.clamp(amount, 0, max));
    }

    public static void addMana(Player player, int amount) {
        setMana(player, getMana(player) + amount);
    }

    public static void removeMana(Player player, int amount) {
        if (player.isCreative()) return;
        setMana(player, getMana(player) - amount);
    }

    public static boolean hasMana(Player player, int amount) {
        if (player.isCreative()) return true;
        return getMana(player) >= amount;
    }

    public static int getMaxMana(Player player) {
        int max = BASE_MAX_MANA;

        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.getItem() instanceof ManaItem manaItem) {
                max += manaItem.getMaxMana(stack, ManaSource.ARMOR);
            }
        }

        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.isEmpty() || !(stack.getItem() instanceof ManaItem manaItem))
                continue;
            boolean alreadyCounted = false;
            for (EquipmentSlot slot : new EquipmentSlot[]{
                    EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                if (player.getItemBySlot(slot).is(stack.getItem())) {
                    alreadyCounted = true;
                    break;
                }
            }
            if (!alreadyCounted)
                max += manaItem.getMaxMana(stack, ManaSource.ITEM);
        }

        MaxManaCalcEvent event = new MaxManaCalcEvent(player, max);
        ManaEvents.postMaxMana(event);
        return event.getMax();
    }

    public static float getRegenPercent(Player player) {
        float regen = BASE_REGEN_PERCENT;

        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.getItem() instanceof ManaRegenItem regenItem) {
                regen += regenItem.getManaRegenMultiplier(stack, ManaSource.ARMOR);
            }
        }

        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.isEmpty() || !(stack.getItem() instanceof ManaRegenItem regenItem))
                continue;
            boolean alreadyCounted = false;
            for (EquipmentSlot slot : new EquipmentSlot[]{
                    EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                if (player.getItemBySlot(slot).is(stack.getItem())) {
                    alreadyCounted = true;
                    break;
                }
            }
            if (!alreadyCounted)
                regen += regenItem.getManaRegenMultiplier(stack, ManaSource.ITEM);
        }

        ManaRegenCalcEvent event = new ManaRegenCalcEvent(player, regen);
        ManaEvents.postManaRegen(event);
        return event.getRegenPercent();
    }

    public static int calculateRegenAmount(Player player) {
        int max = getMaxMana(player);
        float percent = getRegenPercent(player);
        return Math.max(1, (int)(max * percent));
    }
}
