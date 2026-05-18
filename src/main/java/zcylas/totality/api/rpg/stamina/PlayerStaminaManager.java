package zcylas.totality.api.rpg.stamina;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.rpg.combat.CombatStateManager;
import zcylas.totality.api.rpg.stamina.base.*;

import java.util.HashMap;
import java.util.UUID;

public class PlayerStaminaManager {
    public static final int   BASE_MAX_STAMINA        = 100;

    /**
     * Out-of-combat regen: 5% of max per second.
     * At 100 max stamina → 5/sec → full bar in ~20 seconds.
     * Future: the out-of-combat regen mastery multiplies this value.
     */
    public static final float BASE_REGEN_PERCENT       = 0.05f;

    /**
     * In-combat regen: 2% of max per second.
     * At 100 max stamina → 2/sec → full bar in ~50 seconds.
     * Slower recovery forces stamina management during fights.
     */
    public static final float IN_COMBAT_REGEN_PERCENT  = 0.02f;

    // Drain 1 stamina every 3 ticks ≈ 6.6 stamina/second ≈ Skyrim's 6s depletion
    public static final float SPRINT_DRAIN_PER_TICK = 0.33f;

    private static final HashMap<UUID, Integer> staminaMap = new HashMap<>();

    public static int getStamina(Player player) {
        if (!staminaMap.containsKey(player.getUUID())) {
            int max = getMaxStamina(player);
            staminaMap.put(player.getUUID(), max);
            return max;
        }
        return staminaMap.get(player.getUUID());
    }

    public static void setStamina(Player player, int amount) {
        int max = getMaxStamina(player);
        staminaMap.put(player.getUUID(), Math.clamp(amount, 0, max));
    }

    public static void addStamina(Player player, int amount) {
        setStamina(player, getStamina(player) + amount);
    }

    public static void removeStamina(Player player, int amount) {
        if (player.isCreative()) return;
        setStamina(player, getStamina(player) - amount);
    }

    public static boolean hasStamina(Player player, int amount) {
        if (player.isCreative()) return true;
        return getStamina(player) >= amount;
    }

    public static int getMaxStamina(Player player) {
        int max = BASE_MAX_STAMINA;

        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            max += zcylas.totality.api.rpg.stats.StatsComponents.getStats(serverPlayer)
                    .getMaxStaminaBonus();
        }

        // Armor slots
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.getItem() instanceof StaminaItem staminaItem) {
                max += staminaItem.getMaxStamina(stack, StaminaSource.ARMOR);
            }
        }

        if (player.hasEffect(zcylas.totality.init.ModEffects.FORTIFY_STAMINA)) {
            net.minecraft.world.effect.MobEffectInstance inst =
                    player.getEffect(zcylas.totality.init.ModEffects.FORTIFY_STAMINA);
            if (inst != null) {
                max += inst.getAmplifier() + 1;
            }
        }

        // Hand slots
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.isEmpty() || !(stack.getItem() instanceof StaminaItem staminaItem))
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
                max += staminaItem.getMaxStamina(stack, StaminaSource.ITEM);
        }

        MaxStaminaCalcEvent event = new MaxStaminaCalcEvent(player, max);
        StaminaEvents.postMaxStamina(event);
        return event.getMax();
    }

    /**
     * Returns the regen percent for this tick, accounting for combat state.
     *
     * Combat state is checked here so all callers (including future masteries
     * hooking via StaminaRegenCalcEvent) automatically get the right base rate.
     *
     * Mastery hook example — subscribe to StaminaRegenCalcEvent and do:
     *   if (!CombatStateManager.isInCombat((ServerPlayer) player))
     *       event.setRegenPercent(event.getRegenPercent() * 2f);
     */
    public static float getRegenPercent(Player player) {
        // Pick base rate depending on combat state
        boolean inCombat = player instanceof ServerPlayer sp
                && CombatStateManager.isInCombat(sp);
        float regen = inCombat ? IN_COMBAT_REGEN_PERCENT : BASE_REGEN_PERCENT;

        // Armor slots
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.getItem() instanceof StaminaRegenItem regenItem) {
                regen += regenItem.getStaminaRegenMultiplier(stack, StaminaSource.ARMOR);
            }
        }

        if (player.hasEffect(zcylas.totality.init.ModEffects.REGENERATE_STAMINA)) {
            net.minecraft.world.effect.MobEffectInstance inst =
                    player.getEffect(zcylas.totality.init.ModEffects.REGENERATE_STAMINA);
            if (inst != null) {
                regen *= (1f + (inst.getAmplifier() + 1) / 100f);
            }
        }

        // Hand slots
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.isEmpty() || !(stack.getItem() instanceof StaminaRegenItem regenItem))
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
                regen += regenItem.getStaminaRegenMultiplier(stack, StaminaSource.ITEM);
        }

        StaminaRegenCalcEvent event = new StaminaRegenCalcEvent(player, regen);
        StaminaEvents.postStaminaRegen(event);
        return event.getRegenPercent();
    }

    public static int calculateRegenAmount(Player player) {
        int max = getMaxStamina(player);
        float percent = getRegenPercent(player);
        return Math.max(1, (int)(max * percent));
    }
}