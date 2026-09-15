package zcylas.totality.api.rpg.stamina;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.rpg.combat.CombatStateManager;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.PlayerResourceService;
import zcylas.totality.api.rpg.resources.ResourceAmount;
import zcylas.totality.api.rpg.resources.ResourceCause;
import zcylas.totality.api.rpg.resources.ResourceContext;
import zcylas.totality.api.rpg.resources.ResourceQueryResult;
import zcylas.totality.api.rpg.resources.ResourceTarget;
import zcylas.totality.api.rpg.resources.integration.PlayerBaselineResources;
import zcylas.totality.api.rpg.stamina.base.*;


/**
 * Phase 4 migration (2026-09-15): compatibility facade over {@link PlayerResourceService} — see
 * {@link zcylas.totality.api.rpg.mana.PlayerManaManager}'s own Javadoc for the full rationale behind
 * every design decision below (operation mapping, self-healing instantiation, cause-type choices),
 * which mirrors this class exactly. {@code totality:stamina} is now the authoritative, {@code
 * GENERIC_COMPONENT}-owned current value. {@link #getMaxStamina}/{@link #getRegenPercent}/{@link
 * #calculateRegenAmount} are untouched pure formula computations — {@link StaminaMaximumResolver}
 * delegates back to {@link #getMaxStamina} rather than duplicating it.
 */
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

    public static int getStamina(Player player) {
        if (!(player instanceof ServerPlayer sp)) return 0;
        PlayerBaselineResources.ensureInstantiated(sp);
        ResourceQueryResult result = PlayerResourceService.INSTANCE.query(sp, PlayerResourceIds.STAMINA);
        return result instanceof ResourceQueryResult.Success success ? Math.toIntExact(success.snapshot().currentUnits()) : 0;
    }

    public static void setStamina(Player player, int amount) {
        if (!(player instanceof ServerPlayer sp)) return;
        PlayerBaselineResources.ensureInstantiated(sp);
        PlayerResourceService.INSTANCE.set(sp, ResourceTarget.scalar(PlayerResourceIds.STAMINA, amount),
                ResourceContext.of(ResourceCause.of(ResourceContext.CauseTypes.ADMIN_COMMAND)));
    }

    public static void addStamina(Player player, int amount) {
        if (!(player instanceof ServerPlayer sp)) return;
        PlayerBaselineResources.ensureInstantiated(sp);
        PlayerResourceService.INSTANCE.restore(sp, ResourceAmount.scalar(PlayerResourceIds.STAMINA, amount),
                ResourceContext.of(ResourceCause.of(ResourceContext.CauseTypes.PASSIVE_REGENERATION)));
    }

    public static void removeStamina(Player player, int amount) {
        if (player.isCreative()) return;
        if (!(player instanceof ServerPlayer sp)) return;
        PlayerBaselineResources.ensureInstantiated(sp);
        PlayerResourceService.INSTANCE.drain(sp, ResourceAmount.scalar(PlayerResourceIds.STAMINA, amount),
                ResourceContext.of(ResourceCause.of(ResourceContext.CauseTypes.ABILITY_COST)));
    }

    public static boolean hasStamina(Player player, int amount) {
        if (player.isCreative()) return true;
        return getStamina(player) >= amount;
    }

    /**
     * Task §14 / canonical §24.7: a valid Long Rest fully restores Stamina; ordinary Short Rest does
     * nothing to it (no separate feature currently says otherwise). Registered as a {@code
     * RestListener} in {@code PlayerConnectionEvents} alongside Rage/Abilities/Spell Slots'
     * established pattern. Uses {@code restore} with the exact deficit (never an astronomically
     * large amount) so the now-checked-arithmetic mutation path cannot spuriously overflow-reject a
     * legitimate full restore.
     */
    public static void onLongRest(ServerPlayer player) {
        ResourceQueryResult result = PlayerResourceService.INSTANCE.query(player, PlayerResourceIds.STAMINA);
        if (!(result instanceof ResourceQueryResult.Success success)) return;
        long deficit = success.snapshot().maximumUnits() - success.snapshot().currentUnits();
        if (deficit <= 0) return;
        PlayerResourceService.INSTANCE.restore(player, ResourceAmount.scalar(PlayerResourceIds.STAMINA, deficit),
                ResourceContext.of(ResourceCause.of(ResourceContext.CauseTypes.LONG_REST)));
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